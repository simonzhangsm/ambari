/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.apache.ambari.server.controller;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.ambari.server.AmbariException;
import org.apache.ambari.server.Role;
import org.apache.ambari.server.RoleCommand;
import org.apache.ambari.server.StackAccessException;
import org.apache.ambari.server.actionmanager.ActionManager;
import org.apache.ambari.server.actionmanager.Stage;
import org.apache.ambari.server.actionmanager.TargetHostType;
import org.apache.ambari.server.agent.ExecutionCommand;
import org.apache.ambari.server.api.services.AmbariMetaInfo;
import org.apache.ambari.server.controller.internal.RequestResourceFilter;
import org.apache.ambari.server.customactions.ActionDefinition;
import org.apache.ambari.server.metadata.ActionMetadata;
import org.apache.ambari.server.state.Cluster;
import org.apache.ambari.server.state.Clusters;
import org.apache.ambari.server.state.ComponentInfo;
import org.apache.ambari.server.state.ServiceComponentHost;
import org.apache.ambari.server.state.ServiceInfo;
import org.apache.ambari.server.state.StackId;
import org.apache.ambari.server.state.svccomphost.ServiceComponentHostOpInProgressEvent;
import org.apache.ambari.server.utils.StageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.apache.ambari.server.agent.ExecutionCommand.KeyNames.COMMAND_TIMEOUT;
import static org.apache.ambari.server.agent.ExecutionCommand.KeyNames.COMPONENT_CATEGORY;
import static org.apache.ambari.server.agent.ExecutionCommand.KeyNames.SCHEMA_VERSION;
import static org.apache.ambari.server.agent.ExecutionCommand.KeyNames.SCRIPT;
import static org.apache.ambari.server.agent.ExecutionCommand.KeyNames.SCRIPT_TYPE;

/**
 * Helper class containing logic to process custom action execution requests
 */
@Singleton
public class AmbariActionExecutionHelper {
	private final static Logger LOG = LoggerFactory.getLogger(AmbariActionExecutionHelper.class);
	private static final String TYPE_PYTHON = "PYTHON";
	
	@Inject
	private ActionMetadata actionMetadata;
	@Inject
	private Clusters clusters;
	@Inject
	private AmbariManagementController managementController;
	@Inject
	private ActionManager actionManager;
	@Inject
	private AmbariMetaInfo ambariMetaInfo;
	@Inject
	private MaintenanceStateHelper maintenanceStateHelper;
	
	/**
	 * Validates the request to execute an action.
	 * 
	 * @param actionRequest
	 * @throws AmbariException
	 */
	public void validateAction(ExecuteActionRequest actionRequest) throws AmbariException {
		Cluster cluster = clusters.getCluster(actionRequest.getClusterName());
		
		if (cluster == null) { throw new AmbariException("Unable to find cluster. clusterName = " + actionRequest.getClusterName()); }
		
		if (actionRequest.getActionName() == null || actionRequest.getActionName().isEmpty()) { throw new AmbariException("Action name must be specified"); }
		
		ActionDefinition actionDef = ambariMetaInfo.getActionDefinition(actionRequest.getActionName());
		if (actionDef == null) { throw new AmbariException("Action " + actionRequest.getActionName() + " does not exist"); }
		
		if (actionDef.getInputs() != null) {
			String[] inputs = actionDef.getInputs().split(",");
			for (String input : inputs) {
				String inputName = input.trim();
				if (!inputName.isEmpty()) {
					boolean mandatory = true;
					if (inputName.startsWith("[") && inputName.endsWith("]")) {
						mandatory = false;
					}
					if (mandatory && !actionRequest.getParameters().containsKey(inputName)) { throw new AmbariException("Action " + actionRequest.getActionName() + " requires input '" + input.trim() + "' that is not provided."); }
				}
			}
		}
		
		StackId stackId = cluster.getCurrentStackVersion();
		String expectedService = actionDef.getTargetService() == null ? "" : actionDef.getTargetService();
		
		List<RequestResourceFilter> resourceFilters = actionRequest.getResourceFilters();
		String targetService = "";
		String targetComponent = "";
		RequestResourceFilter resourceFilter = null;
		
		if (resourceFilters != null && !resourceFilters.isEmpty()) {
			if (resourceFilters.size() > 1) { throw new AmbariException("Custom action definition only allows one " + "resource filter to be specified."); }
			
			resourceFilter = resourceFilters.get(0);
			String actualService = resourceFilter.getServiceName() == null ? "" : resourceFilter.getServiceName();
			if (!expectedService.isEmpty() && !actualService.isEmpty() && !expectedService.equals(actualService)) { throw new AmbariException("Action " + actionRequest.getActionName() + " targets service " + actualService + " that does not match with expected " + expectedService); }
			
			targetService = expectedService;
			if (targetService == null || targetService.isEmpty()) {
				targetService = actualService;
			}
			
			if (targetService != null && !targetService.isEmpty()) {
				ServiceInfo serviceInfo;
				try {
					serviceInfo = ambariMetaInfo.getService(stackId.getStackName(), stackId.getStackVersion(), targetService);
				} catch (StackAccessException se) {
					serviceInfo = null;
				}
				
				if (serviceInfo == null) { throw new AmbariException("Action " + actionRequest.getActionName() + " targets service " + targetService + " that does not exist."); }
			}
			
			String expectedComponent = actionDef.getTargetComponent() == null ? "" : actionDef.getTargetComponent();
			String actualComponent = resourceFilter.getComponentName() == null ? "" : resourceFilter.getComponentName();
			if (!expectedComponent.isEmpty() && !actualComponent.isEmpty() && !expectedComponent.equals(actualComponent)) { throw new AmbariException("Action " + actionRequest.getActionName() + " targets component " + actualComponent + " that does not match with expected "
					+ expectedComponent); }
			
			targetComponent = expectedComponent;
			if (targetComponent == null || targetComponent.isEmpty()) {
				targetComponent = actualComponent;
			}
			
			if (!targetComponent.isEmpty() && targetService.isEmpty()) { throw new AmbariException("Action " + actionRequest.getActionName() + " targets component " + targetComponent + " without specifying the target service."); }
			
			if (targetComponent != null && !targetComponent.isEmpty()) {
				ComponentInfo compInfo;
				try {
					compInfo = ambariMetaInfo.getComponent(stackId.getStackName(), stackId.getStackVersion(), targetService, targetComponent);
				} catch (StackAccessException se) {
					compInfo = null;
				}
				
				if (compInfo == null) { throw new AmbariException("Action " + actionRequest.getActionName() + " targets component " + targetComponent + " that does not exist."); }
			}
		}
		
		if (TargetHostType.SPECIFIC.equals(actionDef.getTargetType()) || (targetService.isEmpty() && targetComponent.isEmpty())) {
			if (resourceFilter == null || resourceFilter.getHostNames().size() == 0) { throw new AmbariException("Action " + actionRequest.getActionName() + " requires explicit target host(s)" + " that is not provided."); }
		}
	}
	
	/**
	 * Add tasks to the stage based on the requested action execution
	 * 
	 * @param actionContext the context associated with the action
	 * @param stage stage into which tasks must be inserted
	 * @param hostLevelParams host level params to send with the command
	 * @throws AmbariException
	 */
	public void addExecutionCommandsToStage(ActionExecutionContext actionContext, Stage stage, Map<String, String> hostLevelParams) throws AmbariException {
		
		String actionName = actionContext.getActionName();
		String clusterName = actionContext.getClusterName();
		Cluster cluster = clusters.getCluster(clusterName);
		
		List<RequestResourceFilter> resourceFilters = actionContext.getResourceFilters();
		
		RequestResourceFilter resourceFilter = new RequestResourceFilter();
		if (resourceFilters != null && !resourceFilters.isEmpty()) {
			resourceFilter = resourceFilters.get(0);
		}
		
		// List of host to select from
		Set<String> candidateHosts = new HashSet<String>();
		
		String serviceName = actionContext.getExpectedServiceName();
		String componentName = actionContext.getExpectedComponentName();
		StackId stackId = cluster.getCurrentStackVersion();
		ComponentInfo componentInfo = null;
		
		if (serviceName != null && !serviceName.isEmpty()) {
			if (componentName != null && !componentName.isEmpty()) {
				Map<String, ServiceComponentHost> componentHosts = cluster.getService(serviceName).getServiceComponent(componentName).getServiceComponentHosts();
				candidateHosts.addAll(componentHosts.keySet());
				componentInfo = ambariMetaInfo.getComponentCategory(stackId.getStackName(), stackId.getStackVersion(), serviceName, componentName);
			} else {
				for (String component : cluster.getService(serviceName).getServiceComponents().keySet()) {
					Map<String, ServiceComponentHost> componentHosts = cluster.getService(serviceName).getServiceComponent(component).getServiceComponentHosts();
					candidateHosts.addAll(componentHosts.keySet());
				}
			}
		} else {
			// All hosts are valid target host
			candidateHosts.addAll(clusters.getHostsForCluster(cluster.getClusterName()).keySet());
		}
		
		// If request did not specify hosts and there exists no host
		if (resourceFilter.getHostNames().isEmpty() && candidateHosts.isEmpty()) { throw new AmbariException("Suitable hosts not found, component=" + componentName + ", service=" + serviceName + ", cluster=" + cluster.getClusterName() + ", " + "actionName="
				+ actionContext.getActionName()); }
		
		// Compare specified hosts to available hosts
		if (!resourceFilter.getHostNames().isEmpty() && !candidateHosts.isEmpty()) {
			for (String hostname : resourceFilter.getHostNames()) {
				if (!candidateHosts.contains(hostname)) { throw new AmbariException("Request specifies host " + hostname + " but its not a valid host based on the " + "target service=" + serviceName + " and component=" + componentName); }
			}
		}
		
		List<String> targetHosts = resourceFilter.getHostNames();
		
		// Find target hosts to execute
		if (targetHosts.isEmpty()) {
			TargetHostType hostType = actionContext.getTargetType();
			switch (hostType) {
			case ALL:
				targetHosts.addAll(candidateHosts);
				break;
			case ANY:
				targetHosts.add(managementController.getHealthyHost(candidateHosts));
				break;
			case MAJORITY:
				for (int i = 0; i < (candidateHosts.size() / 2) + 1; i++) {
					String hostname = managementController.getHealthyHost(candidateHosts);
					targetHosts.add(hostname);
					candidateHosts.remove(hostname);
				}
				break;
			default:
				throw new AmbariException("Unsupported target type = " + hostType);
			}
		}
		
		Set<Map<String, String>> maintenanceSCHs = maintenanceStateHelper.getMaintenanceHostComponents(clusters, cluster);
		
		// create tasks for each host
		for (String hostName : targetHosts) {
			stage.addHostRoleExecutionCommand(hostName, Role.valueOf(actionContext.getActionName()), RoleCommand.ACTIONEXECUTE, new ServiceComponentHostOpInProgressEvent(actionContext.getActionName(), hostName, System.currentTimeMillis()), clusterName, serviceName);
			
			Map<String, Map<String, String>> configurations = new TreeMap<String, Map<String, String>>();
			Map<String, Map<String, String>> configTags = null;
			if (!serviceName.isEmpty()) {
				configTags = managementController.findConfigurationTagsWithOverrides(cluster, hostName);
			}
			
			Map<String, String> commandParams = actionContext.getParameters();
			commandParams.put(COMMAND_TIMEOUT, actionContext.getTimeout().toString());
			commandParams.put(SCRIPT, actionName + ".py");
			commandParams.put(SCRIPT_TYPE, TYPE_PYTHON);
			commandParams.put(SCHEMA_VERSION, AmbariMetaInfo.SCHEMA_VERSION_2);
			
			ExecutionCommand execCmd = stage.getExecutionCommandWrapper(hostName, actionContext.getActionName()).getExecutionCommand();
			
			/*
			 * TODO Execution command field population should be (partially?) combined with the same code at createHostAction()
			 */
			execCmd.setConfigurations(configurations);
			execCmd.setConfigurationTags(configTags);
			execCmd.setHostLevelParams(hostLevelParams);
			execCmd.setCommandParams(commandParams);
			execCmd.setServiceName(serviceName == null || serviceName.isEmpty() ? resourceFilter.getServiceName() : serviceName);
			execCmd.setComponentName(componentName == null || componentName.isEmpty() ? resourceFilter.getComponentName() : componentName);
			
			Map<String, String> roleParams = execCmd.getRoleParams();
			if (roleParams == null) {
				roleParams = new TreeMap<String, String>();
			}
			roleParams.putAll(actionContext.getParameters());
			if (componentInfo != null) {
				roleParams.put(COMPONENT_CATEGORY, componentInfo.getCategory());
			}
			execCmd.setRoleParams(roleParams);
			
			// Generate cluster host info
			execCmd.setClusterHostInfo(StageUtils.getClusterHostInfo(clusters.getHostsForCluster(clusterName), cluster));
			
			// cluster passive map
			execCmd.setPassiveInfo(maintenanceSCHs);
		}
	}
}
