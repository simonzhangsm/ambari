/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.apache.ambari.server.controller.internal;

import com.google.gson.Gson;
import com.google.inject.Inject;
import org.apache.ambari.server.AmbariException;
import org.apache.ambari.server.DuplicateResourceException;
import org.apache.ambari.server.api.services.AmbariMetaInfo;
import org.apache.ambari.server.controller.spi.NoSuchParentResourceException;
import org.apache.ambari.server.controller.spi.NoSuchResourceException;
import org.apache.ambari.server.controller.spi.Predicate;
import org.apache.ambari.server.controller.spi.Request;
import org.apache.ambari.server.controller.spi.RequestStatus;
import org.apache.ambari.server.controller.spi.Resource;
import org.apache.ambari.server.controller.spi.ResourceAlreadyExistsException;
import org.apache.ambari.server.controller.spi.SystemException;
import org.apache.ambari.server.controller.spi.UnsupportedPropertyException;
import org.apache.ambari.server.controller.utilities.PropertyHelper;
import org.apache.ambari.server.orm.dao.BlueprintDAO;
import org.apache.ambari.server.orm.entities.BlueprintConfigEntity;
import org.apache.ambari.server.orm.entities.BlueprintConfiguration;
import org.apache.ambari.server.orm.entities.BlueprintEntity;
import org.apache.ambari.server.orm.entities.HostGroupComponentEntity;
import org.apache.ambari.server.orm.entities.HostGroupConfigEntity;
import org.apache.ambari.server.orm.entities.HostGroupEntity;
import org.apache.ambari.server.state.ComponentInfo;
import org.apache.ambari.server.state.ServiceInfo;
import org.apache.ambari.server.state.PropertyInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resource Provider for Blueprint resources.
 */
public class BlueprintResourceProvider extends AbstractResourceProvider {
	
	// ----- Property ID constants ---------------------------------------------
	
	// Blueprints
	protected static final String BLUEPRINT_NAME_PROPERTY_ID = PropertyHelper.getPropertyId("Blueprints", "blueprint_name");
	protected static final String STACK_NAME_PROPERTY_ID = PropertyHelper.getPropertyId("Blueprints", "stack_name");
	protected static final String STACK_VERSION_PROPERTY_ID = PropertyHelper.getPropertyId("Blueprints", "stack_version");
	
	// Host Groups
	protected static final String HOST_GROUP_PROPERTY_ID = "host_groups";
	protected static final String HOST_GROUP_NAME_PROPERTY_ID = "name";
	protected static final String HOST_GROUP_CARDINALITY_PROPERTY_ID = "cardinality";
	
	// Host Group Components
	protected static final String COMPONENT_PROPERTY_ID = "components";
	protected static final String COMPONENT_NAME_PROPERTY_ID = "name";
	
	// Configurations
	protected static final String CONFIGURATION_PROPERTY_ID = "configurations";
	
	// Primary Key Fields
	private static Set<String> pkPropertyIds = new HashSet<String>(Arrays.asList(new String[] { BLUEPRINT_NAME_PROPERTY_ID }));
	
	/**
	 * Blueprint data access object.
	 */
	private static BlueprintDAO dao;
	
	/**
	 * Used to serialize to/from json.
	 */
	private static Gson jsonSerializer;
	
	/**
	 * Stack information.
	 */
	private static AmbariMetaInfo stackInfo;
	
	// ----- Constructors ----------------------------------------------------
	
	/**
	 * Create a new resource provider for the given management controller.
	 * 
	 * @param propertyIds the property ids
	 * @param keyPropertyIds the key property ids
	 */
	BlueprintResourceProvider(Set<String> propertyIds, Map<Resource.Type, String> keyPropertyIds) {
		super(propertyIds, keyPropertyIds);
	}
	
	/**
	 * Static initialization.
	 * 
	 * @param blueprintDAO blueprint data access object
	 * @param gson gson json serializer
	 * @param metaInfo stack related information
	 */
	@Inject
	public static void init(BlueprintDAO blueprintDAO, Gson gson, AmbariMetaInfo metaInfo) {
		dao = blueprintDAO;
		jsonSerializer = gson;
		stackInfo = metaInfo;
	}
	
	// ----- ResourceProvider ------------------------------------------------
	
	@Override
	protected Set<String> getPKPropertyIds() {
		return pkPropertyIds;
	}
	
	@Override
	public RequestStatus createResources(Request request) throws SystemException, UnsupportedPropertyException, ResourceAlreadyExistsException, NoSuchParentResourceException {
		
		for (Map<String, Object> properties : request.getProperties()) {
			createResources(getCreateCommand(properties));
		}
		notifyCreate(Resource.Type.Blueprint, request);
		
		return getRequestStatus(null);
	}
	
	@Override
	public Set<Resource> getResources(Request request, Predicate predicate) throws SystemException, UnsupportedPropertyException, NoSuchResourceException, NoSuchParentResourceException {
		
		List<BlueprintEntity> results = null;
		boolean applyPredicate = false;
		
		if (predicate != null) {
			Set<Map<String, Object>> requestProps = getPropertyMaps(predicate);
			if (requestProps.size() == 1) {
				String name = (String) requestProps.iterator().next().get(BLUEPRINT_NAME_PROPERTY_ID);
				
				if (name != null) {
					BlueprintEntity entity = dao.findByName(name);
					results = entity == null ? Collections.<BlueprintEntity> emptyList() : Collections.singletonList(entity);
				}
			}
		}
		
		if (results == null) {
			applyPredicate = true;
			results = dao.findAll();
		}
		
		Set<Resource> resources = new HashSet<Resource>();
		for (BlueprintEntity entity : results) {
			Resource resource = toResource(entity, getRequestPropertyIds(request, predicate));
			if (predicate == null || !applyPredicate || predicate.evaluate(resource)) {
				resources.add(resource);
			}
		}
		
		if (predicate != null && resources.isEmpty()) { throw new NoSuchResourceException("The requested resource doesn't exist: Blueprint not found, " + predicate); }
		
		return resources;
	}
	
	@Override
	public RequestStatus updateResources(Request request, Predicate predicate) throws SystemException, UnsupportedPropertyException, NoSuchResourceException, NoSuchParentResourceException {
		
		// no-op, blueprints are immutable. Service doesn't support PUT so should never get here.
		return null;
	}
	
	@Override
	public RequestStatus deleteResources(Predicate predicate) throws SystemException, UnsupportedPropertyException, NoSuchResourceException, NoSuchParentResourceException {
		
		// TODO (jspeidel): Revisit concurrency control
		Set<Resource> setResources = getResources(new RequestImpl(null, null, null, null), predicate);
		
		for (final Resource resource : setResources) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("Deleting Blueprint, name=" + resource.getPropertyValue(BLUEPRINT_NAME_PROPERTY_ID));
			}
			modifyResources(new Command<Void>() {
				@Override
				public Void invoke() throws AmbariException {
					dao.remove(toEntity(resource));
					return null;
				}
			});
		}
		
		notifyDelete(Resource.Type.Blueprint, predicate);
		return getRequestStatus(null);
	}
	
	// ----- Instance Methods ------------------------------------------------
	
	/**
	 * Create a resource instance from a blueprint entity.
	 * 
	 * @param entity blueprint entity
	 * @param requestedIds requested id's
	 * @return a new resource instance for the given blueprint entity
	 */
	protected Resource toResource(BlueprintEntity entity, Set<String> requestedIds) {
		Resource resource = new ResourceImpl(Resource.Type.Blueprint);
		setResourceProperty(resource, BLUEPRINT_NAME_PROPERTY_ID, entity.getBlueprintName(), requestedIds);
		setResourceProperty(resource, STACK_NAME_PROPERTY_ID, entity.getStackName(), requestedIds);
		setResourceProperty(resource, STACK_VERSION_PROPERTY_ID, entity.getStackVersion(), requestedIds);
		
		List<Map<String, Object>> listGroupProps = new ArrayList<Map<String, Object>>();
		Collection<HostGroupEntity> hostGroups = entity.getHostGroups();
		for (HostGroupEntity hostGroup : hostGroups) {
			Map<String, Object> mapGroupProps = new HashMap<String, Object>();
			mapGroupProps.put(HOST_GROUP_NAME_PROPERTY_ID, hostGroup.getName());
			listGroupProps.add(mapGroupProps);
			mapGroupProps.put(HOST_GROUP_CARDINALITY_PROPERTY_ID, hostGroup.getCardinality());
			
			List<Map<String, String>> listComponentProps = new ArrayList<Map<String, String>>();
			Collection<HostGroupComponentEntity> components = hostGroup.getComponents();
			for (HostGroupComponentEntity component : components) {
				Map<String, String> mapComponentProps = new HashMap<String, String>();
				mapComponentProps.put(COMPONENT_NAME_PROPERTY_ID, component.getName());
				listComponentProps.add(mapComponentProps);
			}
			mapGroupProps.put(COMPONENT_PROPERTY_ID, listComponentProps);
			mapGroupProps.put(CONFIGURATION_PROPERTY_ID, populateConfigurationList(hostGroup.getConfigurations()));
		}
		setResourceProperty(resource, HOST_GROUP_PROPERTY_ID, listGroupProps, requestedIds);
		setResourceProperty(resource, CONFIGURATION_PROPERTY_ID, populateConfigurationList(entity.getConfigurations()), requestedIds);
		
		return resource;
	}
	
	/**
	 * Convert a resource to a blueprint entity.
	 * 
	 * @param resource the resource to convert
	 * @return a new blueprint entity
	 */
	@SuppressWarnings("unchecked")
	protected BlueprintEntity toEntity(Resource resource) {
		BlueprintEntity entity = new BlueprintEntity();
		entity.setBlueprintName((String) resource.getPropertyValue(BLUEPRINT_NAME_PROPERTY_ID));
		entity.setStackName((String) resource.getPropertyValue(STACK_NAME_PROPERTY_ID));
		entity.setStackVersion((String) resource.getPropertyValue(STACK_VERSION_PROPERTY_ID));
		
		Collection<HostGroupEntity> blueprintHostGroups = new ArrayList<HostGroupEntity>();
		entity.setHostGroups(blueprintHostGroups);
		
		Collection<Map<String, Object>> hostGroupProps = (Collection<Map<String, Object>>) resource.getPropertyValue(HOST_GROUP_PROPERTY_ID);
		
		for (Map<String, Object> properties : hostGroupProps) {
			HostGroupEntity group = new HostGroupEntity();
			group.setName((String) properties.get(BlueprintResourceProvider.HOST_GROUP_NAME_PROPERTY_ID));
			group.setBlueprintEntity(entity);
			group.setBlueprintName(entity.getBlueprintName());
			group.setCardinality((String) properties.get(HOST_GROUP_CARDINALITY_PROPERTY_ID));
			
			Collection<HostGroupComponentEntity> hostGroupComponents = new ArrayList<HostGroupComponentEntity>();
			group.setComponents(hostGroupComponents);
			createHostGroupConfigEntities((Collection<Map<String, String>>) properties.get(CONFIGURATION_PROPERTY_ID), group);
			
			List<Map<String, String>> listComponents = (List<Map<String, String>>) properties.get(BlueprintResourceProvider.COMPONENT_PROPERTY_ID);
			
			for (Map<String, String> componentProperties : listComponents) {
				HostGroupComponentEntity component = new HostGroupComponentEntity();
				component.setName(componentProperties.get(COMPONENT_NAME_PROPERTY_ID));
				component.setBlueprintName(entity.getBlueprintName());
				component.setHostGroupEntity(group);
				component.setHostGroupName((String) properties.get(HOST_GROUP_NAME_PROPERTY_ID));
				
				hostGroupComponents.add(component);
			}
			blueprintHostGroups.add(group);
		}
		
		createBlueprintConfigEntities((Collection<Map<String, String>>) resource.getPropertyValue(CONFIGURATION_PROPERTY_ID), entity);
		
		return entity;
	}
	
	/**
	 * Convert a map of properties to a blueprint entity.
	 * 
	 * @param properties property map
	 * @return new blueprint entity
	 */
	@SuppressWarnings("unchecked")
	protected BlueprintEntity toBlueprintEntity(Map<String, Object> properties) {
		String name = (String) properties.get(BLUEPRINT_NAME_PROPERTY_ID);
		if (name == null || name.isEmpty()) { throw new IllegalArgumentException("Blueprint name must be provided"); }
		
		BlueprintEntity blueprint = new BlueprintEntity();
		blueprint.setBlueprintName(name);
		blueprint.setStackName((String) properties.get(STACK_NAME_PROPERTY_ID));
		blueprint.setStackVersion((String) properties.get(STACK_VERSION_PROPERTY_ID));
		
		createHostGroupEntities(blueprint, (HashSet<HashMap<String, Object>>) properties.get(HOST_GROUP_PROPERTY_ID));
		
		createBlueprintConfigEntities((Collection<Map<String, String>>) properties.get(CONFIGURATION_PROPERTY_ID), blueprint);
		
		return blueprint;
	}
	
	/**
	 * Create host group entities and add to the parent blueprint entity.
	 * 
	 * @param blueprint parent blueprint entity
	 * @param setHostGroups set of host group property maps
	 */
	@SuppressWarnings("unchecked")
	private void createHostGroupEntities(BlueprintEntity blueprint, HashSet<HashMap<String, Object>> setHostGroups) {
		
		if (setHostGroups == null || setHostGroups.isEmpty()) { throw new IllegalArgumentException("At least one host group must be specified in a blueprint"); }
		
		Collection<HostGroupEntity> entities = new ArrayList<HostGroupEntity>();
		Collection<String> stackComponentNames = getAllStackComponents(blueprint.getStackName(), blueprint.getStackVersion());
		
		for (HashMap<String, Object> hostGroupProperties : setHostGroups) {
			HostGroupEntity hostGroup = new HostGroupEntity();
			entities.add(hostGroup);
			
			String hostGroupName = (String) hostGroupProperties.get(HOST_GROUP_NAME_PROPERTY_ID);
			if (hostGroupName == null || hostGroupName.isEmpty()) { throw new IllegalArgumentException("Every host group must include a non-null 'name' property"); }
			
			hostGroup.setName(hostGroupName);
			hostGroup.setBlueprintEntity(blueprint);
			hostGroup.setBlueprintName(blueprint.getBlueprintName());
			hostGroup.setCardinality((String) hostGroupProperties.get(HOST_GROUP_CARDINALITY_PROPERTY_ID));
			
			createHostGroupConfigEntities((Collection<Map<String, String>>) hostGroupProperties.get(CONFIGURATION_PROPERTY_ID), hostGroup);
			
			createComponentEntities(hostGroup, (HashSet<HashMap<String, String>>) hostGroupProperties.get(COMPONENT_PROPERTY_ID), stackComponentNames);
		}
		blueprint.setHostGroups(entities);
	}
	
	/**
	 * Create component entities and add to parent host group.
	 * 
	 * @param group parent host group
	 * @param setComponents set of component property maps
	 * @param componentNames set of all component names for the associated stack
	 */
	@SuppressWarnings("unchecked")
	private void createComponentEntities(HostGroupEntity group, HashSet<HashMap<String, String>> setComponents, Collection<String> componentNames) {
		
		Collection<HostGroupComponentEntity> components = new ArrayList<HostGroupComponentEntity>();
		String groupName = group.getName();
		group.setComponents(components);
		
		if (setComponents == null || setComponents.isEmpty()) { throw new IllegalArgumentException("Host group '" + groupName + "' must contain at least one component"); }
		
		for (HashMap<String, String> componentProperties : setComponents) {
			HostGroupComponentEntity component = new HostGroupComponentEntity();
			components.add(component);
			
			String componentName = componentProperties.get(COMPONENT_NAME_PROPERTY_ID);
			if (componentName == null || componentName.isEmpty()) { throw new IllegalArgumentException("Host group '" + groupName + "' contains a component with no 'name' property"); }
			
			if (!componentNames.contains(componentName)) { throw new IllegalArgumentException("The component '" + componentName + "' in host group '" + groupName + "' is not valid for the specified stack"); }
			
			component.setName(componentName);
			component.setBlueprintName(group.getBlueprintName());
			component.setHostGroupEntity(group);
			component.setHostGroupName(group.getName());
		}
		group.setComponents(components);
	}
	
	/**
	 * Obtain all component names for the specified stack.
	 * 
	 * @param stackName stack name
	 * @param stackVersion stack version
	 * @return collection of component names for the specified stack
	 * @throws IllegalArgumentException if the specified stack doesn't exist
	 */
	private Collection<String> getAllStackComponents(String stackName, String stackVersion) {
		Collection<String> componentNames = new HashSet<String>();
		componentNames.add("AMBARI_SERVER");
		Collection<ComponentInfo> components;
		try {
			components = getComponents(stackName, stackVersion);
		} catch (AmbariException e) {
			throw new IllegalArgumentException("The specified stack doesn't exist.  Name='" + stackName + "', Version='" + stackVersion + "'");
		}
		if (components != null) {
			for (ComponentInfo component : components) {
				componentNames.add(component.getName());
			}
		}
		return componentNames;
	}
	
	/**
	 * Get all the components for the specified stack.
	 * 
	 * @param stackName stack name
	 * @param version stack version
	 * @return all components for the specified stack
	 * @throws AmbariException if the stack doesn't exist
	 */
	private Collection<ComponentInfo> getComponents(String stackName, String version) throws AmbariException {
		Collection<ComponentInfo> components = new HashSet<ComponentInfo>();
		Map<String, ServiceInfo> services = stackInfo.getServices(stackName, version);
		
		for (ServiceInfo service : services.values()) {
			List<ComponentInfo> serviceComponents = stackInfo.getComponentsByService(stackName, version, service.getName());
			for (ComponentInfo component : serviceComponents) {
				components.add(component);
			}
		}
		return components;
	}
	
	/**
	 * Populate a list of configuration property maps from a collection of configuration entities.
	 * 
	 * @param configurations collection of configuration entities
	 * @return list of configuration property maps
	 */
	private List<Map<String, Object>> populateConfigurationList(Collection<? extends BlueprintConfiguration> configurations) {
		
		List<Map<String, Object>> listConfigurations = new ArrayList<Map<String, Object>>();
		for (BlueprintConfiguration config : configurations) {
			Map<String, Object> mapConfigurations = new HashMap<String, Object>();
			String type = config.getType();
			Map<String, String> properties = jsonSerializer.<Map<String, String>> fromJson(config.getConfigData(), Map.class);
			mapConfigurations.put(type, properties);
			listConfigurations.add(mapConfigurations);
		}
		
		return listConfigurations;
	}
	
	/**
	 * Populate blueprint configurations.
	 * 
	 * @param propertyMaps collection of configuration property maps
	 * @param blueprint blueprint entity to set configurations on
	 */
	private void createBlueprintConfigEntities(Collection<Map<String, String>> propertyMaps, BlueprintEntity blueprint) {
		
		Collection<BlueprintConfigEntity> configurations = new ArrayList<BlueprintConfigEntity>();
		if (propertyMaps != null) {
			for (Map<String, String> configuration : propertyMaps) {
				BlueprintConfigEntity configEntity = new BlueprintConfigEntity();
				configEntity.setBlueprintEntity(blueprint);
				populateConfigurationEntity(blueprint.getBlueprintName(), configuration, configEntity);
				configurations.add(configEntity);
			}
		}
		blueprint.setConfigurations(configurations);
	}
	
	/**
	 * Populate host group configurations.
	 * 
	 * @param propertyMaps collection of configuration property maps
	 * @param hostGroup host group entity to set configurations on
	 */
	private void createHostGroupConfigEntities(Collection<Map<String, String>> propertyMaps, HostGroupEntity hostGroup) {
		
		Collection<HostGroupConfigEntity> configurations = new ArrayList<HostGroupConfigEntity>();
		if (propertyMaps != null) {
			for (Map<String, String> configuration : propertyMaps) {
				HostGroupConfigEntity configEntity = new HostGroupConfigEntity();
				configEntity.setHostGroupEntity(hostGroup);
				configEntity.setHostGroupName(hostGroup.getName());
				populateConfigurationEntity(hostGroup.getBlueprintName(), configuration, configEntity);
				configurations.add(configEntity);
			}
		}
		hostGroup.setConfigurations(configurations);
	}
	
	/**
	 * Populate a configuration entity from properties.
	 * 
	 * @param blueprintName name of blueprint
	 * @param configuration property map
	 * @param configEntity config entity to populate
	 */
	private void populateConfigurationEntity(String blueprintName, Map<String, String> configuration, BlueprintConfiguration configEntity) {
		
		configEntity.setBlueprintName(blueprintName);
		Map<String, String> configData = new HashMap<String, String>();
		
		for (Map.Entry<String, String> entry : configuration.entrySet()) {
			String absolutePropName = entry.getKey();
			
			int idx = absolutePropName.indexOf('/');
			if (configEntity.getType() == null) {
				configEntity.setType(absolutePropName.substring(0, idx));
			}
			configData.put(absolutePropName.substring(idx + 1), entry.getValue());
		}
		configEntity.setConfigData(jsonSerializer.toJson(configData));
	}
	
	/**
	 * Create a create command with all properties set.
	 * 
	 * @param properties properties to be applied to blueprint
	 * @return a new create command
	 */
	private Command<Void> getCreateCommand(final Map<String, Object> properties) {
		return new Command<Void>() {
			@Override
			public Void invoke() throws AmbariException {
				BlueprintEntity blueprint = toBlueprintEntity(properties);
				
				if (dao.findByName(blueprint.getBlueprintName()) != null) { throw new DuplicateResourceException("Attempted to create a Blueprint which already exists, blueprint_name=" + blueprint.getBlueprintName()); }
				
				Map<String, Map<String, Collection<String>>> missingProperties = blueprint.validateConfigurations(stackInfo, PropertyInfo.PropertyType.DEFAULT);
				
				if (!missingProperties.isEmpty()) { throw new IllegalArgumentException("Required configurations are missing from the specified host groups: " + missingProperties); }
				
				if (LOG.isDebugEnabled()) {
					LOG.debug("Creating Blueprint, name=" + blueprint.getBlueprintName());
				}
				dao.create(blueprint);
				return null;
			}
		};
	}
}
