/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.apache.ambari.server.controller.internal;

import com.google.gson.Gson;
import org.apache.ambari.server.AmbariException;
import org.apache.ambari.server.api.services.AmbariMetaInfo;
import org.apache.ambari.server.controller.predicate.EqualsPredicate;
import org.apache.ambari.server.controller.spi.NoSuchParentResourceException;
import org.apache.ambari.server.controller.spi.NoSuchResourceException;
import org.apache.ambari.server.controller.spi.Predicate;
import org.apache.ambari.server.controller.spi.Request;
import org.apache.ambari.server.controller.spi.Resource;
import org.apache.ambari.server.controller.spi.ResourceAlreadyExistsException;
import org.apache.ambari.server.controller.spi.ResourceProvider;
import org.apache.ambari.server.controller.spi.SystemException;
import org.apache.ambari.server.controller.spi.UnsupportedPropertyException;
import org.apache.ambari.server.controller.utilities.PropertyHelper;
import org.apache.ambari.server.orm.dao.BlueprintDAO;
import org.apache.ambari.server.orm.entities.BlueprintConfigEntity;
import org.apache.ambari.server.orm.entities.BlueprintEntity;
import org.apache.ambari.server.orm.entities.HostGroupComponentEntity;
import org.apache.ambari.server.orm.entities.HostGroupConfigEntity;
import org.apache.ambari.server.orm.entities.HostGroupEntity;
import org.apache.ambari.server.state.ComponentInfo;
import org.apache.ambari.server.state.ServiceInfo;
import org.easymock.Capture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.fail;

/**
 * BlueprintResourceProvider unit tests.
 */
@SuppressWarnings("unchecked")
public class BlueprintResourceProviderTest {
	
	private static String BLUEPRINT_NAME = "test-blueprint";
	
	private final static BlueprintDAO dao = createStrictMock(BlueprintDAO.class);
	private final static Gson gson = new Gson();
	private final static AmbariMetaInfo metaInfo = createMock(AmbariMetaInfo.class);
	
	@BeforeClass
	public static void initClass() {
		BlueprintResourceProvider.init(dao, gson, metaInfo);
	}
	
	@Before
	public void resetGlobalMocks() {
		reset(dao, metaInfo);
	}
	
	@Test
	public void testCreateResources() throws AmbariException, ResourceAlreadyExistsException, SystemException, UnsupportedPropertyException, NoSuchParentResourceException {
		
		Request request = createMock(Request.class);
		
		Map<String, ServiceInfo> services = new HashMap<String, ServiceInfo>();
		ServiceInfo service = new ServiceInfo();
		service.setName("test-service");
		services.put("test-service", service);
		
		List<ComponentInfo> serviceComponents = new ArrayList<ComponentInfo>();
		ComponentInfo component1 = new ComponentInfo();
		component1.setName("component1");
		ComponentInfo component2 = new ComponentInfo();
		component2.setName("component2");
		serviceComponents.add(component1);
		serviceComponents.add(component2);
		
		Set<Map<String, Object>> setProperties = getTestProperties();
		
		Capture<BlueprintEntity> entityCapture = new Capture<BlueprintEntity>();
		
		// set expectations
		expect(request.getProperties()).andReturn(setProperties);
		expect(dao.findByName(BLUEPRINT_NAME)).andReturn(null);
		expect(metaInfo.getServices("test-stack-name", "test-stack-version")).andReturn(services).anyTimes();
		expect(metaInfo.getComponentsByService("test-stack-name", "test-stack-version", "test-service")).andReturn(serviceComponents).anyTimes();
		expect(metaInfo.getComponentToService("test-stack-name", "test-stack-version", "component1")).andReturn("test-service").anyTimes();
		expect(metaInfo.getComponentToService("test-stack-name", "test-stack-version", "component2")).andReturn("test-service").anyTimes();
		expect(metaInfo.getRequiredProperties("test-stack-name", "test-stack-version", "test-service")).andReturn(Collections.<String, org.apache.ambari.server.state.PropertyInfo> emptyMap()).anyTimes();
		dao.create(capture(entityCapture));
		
		replay(dao, metaInfo, request);
		// end expectations
		
		ResourceProvider provider = createProvider();
		AbstractResourceProviderTest.TestObserver observer = new AbstractResourceProviderTest.TestObserver();
		((ObservableResourceProvider) provider).addObserver(observer);
		
		provider.createResources(request);
		
		ResourceProviderEvent lastEvent = observer.getLastEvent();
		assertNotNull(lastEvent);
		assertEquals(Resource.Type.Blueprint, lastEvent.getResourceType());
		assertEquals(ResourceProviderEvent.Type.Create, lastEvent.getType());
		assertEquals(request, lastEvent.getRequest());
		assertNull(lastEvent.getPredicate());
		
		validateEntity(entityCapture.getValue(), false);
		
		verify(dao, metaInfo, request);
	}
	
	@Test
	public void testCreateResources_withConfiguration() throws AmbariException, ResourceAlreadyExistsException, SystemException, UnsupportedPropertyException, NoSuchParentResourceException {
		
		Set<Map<String, Object>> setProperties = getTestProperties();
		setConfigurationProperties(setProperties);
		Request request = createMock(Request.class);
		
		Map<String, ServiceInfo> services = new HashMap<String, ServiceInfo>();
		ServiceInfo service = new ServiceInfo();
		service.setName("test-service");
		services.put("test-service", service);
		
		List<ComponentInfo> serviceComponents = new ArrayList<ComponentInfo>();
		ComponentInfo component1 = new ComponentInfo();
		component1.setName("component1");
		ComponentInfo component2 = new ComponentInfo();
		component2.setName("component2");
		serviceComponents.add(component1);
		serviceComponents.add(component2);
		
		Capture<BlueprintEntity> entityCapture = new Capture<BlueprintEntity>();
		
		// set expectations
		expect(request.getProperties()).andReturn(setProperties);
		expect(dao.findByName(BLUEPRINT_NAME)).andReturn(null);
		expect(metaInfo.getServices("test-stack-name", "test-stack-version")).andReturn(services).anyTimes();
		expect(metaInfo.getComponentsByService("test-stack-name", "test-stack-version", "test-service")).andReturn(serviceComponents).anyTimes();
		expect(metaInfo.getComponentToService("test-stack-name", "test-stack-version", "component1")).andReturn("test-service").anyTimes();
		expect(metaInfo.getComponentToService("test-stack-name", "test-stack-version", "component2")).andReturn("test-service").anyTimes();
		expect(metaInfo.getRequiredProperties("test-stack-name", "test-stack-version", "test-service")).andReturn(Collections.<String, org.apache.ambari.server.state.PropertyInfo> emptyMap()).anyTimes();
		dao.create(capture(entityCapture));
		
		replay(dao, metaInfo, request);
		// end expectations
		
		ResourceProvider provider = createProvider();
		AbstractResourceProviderTest.TestObserver observer = new AbstractResourceProviderTest.TestObserver();
		((ObservableResourceProvider) provider).addObserver(observer);
		
		provider.createResources(request);
		
		ResourceProviderEvent lastEvent = observer.getLastEvent();
		assertNotNull(lastEvent);
		assertEquals(Resource.Type.Blueprint, lastEvent.getResourceType());
		assertEquals(ResourceProviderEvent.Type.Create, lastEvent.getType());
		assertEquals(request, lastEvent.getRequest());
		assertNull(lastEvent.getPredicate());
		
		validateEntity(entityCapture.getValue(), true);
		
		verify(dao, metaInfo, request);
	}
	
	@Test
	public void testGetResourcesNoPredicate() throws SystemException, UnsupportedPropertyException, NoSuchParentResourceException, NoSuchResourceException {
		Request request = createNiceMock(Request.class);
		
		ResourceProvider provider = createProvider();
		BlueprintEntity entity = createEntity(getTestProperties().iterator().next());
		
		List<BlueprintEntity> results = new ArrayList<BlueprintEntity>();
		results.add(entity);
		
		// set expectations
		expect(dao.findAll()).andReturn(results);
		replay(dao, request);
		
		Set<Resource> setResults = provider.getResources(request, null);
		assertEquals(1, setResults.size());
		
		verify(dao);
		validateResource(setResults.iterator().next(), false);
	}
	
	@Test
	public void testGetResourcesNoPredicate_withConfiguration() throws SystemException, UnsupportedPropertyException, NoSuchParentResourceException, NoSuchResourceException {
		Request request = createNiceMock(Request.class);
		
		ResourceProvider provider = createProvider();
		Set<Map<String, Object>> testProperties = getTestProperties();
		setConfigurationProperties(testProperties);
		BlueprintEntity entity = createEntity(testProperties.iterator().next());
		
		List<BlueprintEntity> results = new ArrayList<BlueprintEntity>();
		results.add(entity);
		
		// set expectations
		expect(dao.findAll()).andReturn(results);
		replay(dao, request);
		
		Set<Resource> setResults = provider.getResources(request, null);
		assertEquals(1, setResults.size());
		
		verify(dao);
		validateResource(setResults.iterator().next(), true);
	}
	
	@Test
	public void testDeleteResources() throws SystemException, UnsupportedPropertyException, NoSuchParentResourceException, NoSuchResourceException {
		
		Capture<BlueprintEntity> entityCapture = new Capture<BlueprintEntity>();
		
		ResourceProvider provider = createProvider();
		BlueprintEntity blueprintEntity = createEntity(getTestProperties().iterator().next());
		
		// set expectations
		expect(dao.findByName(BLUEPRINT_NAME)).andReturn(blueprintEntity);
		dao.remove(capture(entityCapture));
		replay(dao);
		
		Predicate predicate = new EqualsPredicate<String>(BlueprintResourceProvider.BLUEPRINT_NAME_PROPERTY_ID, BLUEPRINT_NAME);
		
		AbstractResourceProviderTest.TestObserver observer = new AbstractResourceProviderTest.TestObserver();
		((ObservableResourceProvider) provider).addObserver(observer);
		
		provider.deleteResources(predicate);
		
		ResourceProviderEvent lastEvent = observer.getLastEvent();
		assertNotNull(lastEvent);
		assertEquals(Resource.Type.Blueprint, lastEvent.getResourceType());
		assertEquals(ResourceProviderEvent.Type.Delete, lastEvent.getType());
		assertNotNull(lastEvent.getPredicate());
		
		verify(dao);
		
		validateEntity(entityCapture.getValue(), false);
	}
	
	@Test
	public void testCreateResource_validate__NoHostGroups() throws AmbariException, ResourceAlreadyExistsException, SystemException, UnsupportedPropertyException, NoSuchParentResourceException {
		Request request = createMock(Request.class);
		
		Set<Map<String, Object>> setProperties = getTestProperties();
		setProperties.iterator().next().remove(BlueprintResourceProvider.HOST_GROUP_PROPERTY_ID);
		
		// set expectations
		expect(request.getProperties()).andReturn(setProperties);
		
		replay(dao, metaInfo, request);
		// end expectations
		
		ResourceProvider provider = createProvider();
		try {
			provider.createResources(request);
			fail("Exception expected");
		} catch (IllegalArgumentException e) {
			// expected
		}
		verify(dao, metaInfo, request);
	}
	
	@Test
	public void testCreateResource_Validate__NoHostGroupName() throws AmbariException, ResourceAlreadyExistsException, SystemException, UnsupportedPropertyException, NoSuchParentResourceException {
		Request request = createMock(Request.class);
		
		Map<String, ServiceInfo> services = new HashMap<String, ServiceInfo>();
		ServiceInfo service = new ServiceInfo();
		service.setName("test-service");
		services.put("test-service", service);
		
		List<ComponentInfo> serviceComponents = new ArrayList<ComponentInfo>();
		ComponentInfo component1 = new ComponentInfo();
		component1.setName("component1");
		ComponentInfo component2 = new ComponentInfo();
		component2.setName("component2");
		serviceComponents.add(component1);
		serviceComponents.add(component2);
		
		Set<Map<String, Object>> setProperties = getTestProperties();
		((HashSet<Map<String, String>>) setProperties.iterator().next().get(BlueprintResourceProvider.HOST_GROUP_PROPERTY_ID)).iterator().next().put("name", "");
		
		// set expectations
		expect(request.getProperties()).andReturn(setProperties);
		expect(metaInfo.getServices("test-stack-name", "test-stack-version")).andReturn(services).anyTimes();
		expect(metaInfo.getComponentsByService("test-stack-name", "test-stack-version", "test-service")).andReturn(serviceComponents).anyTimes();
		
		replay(dao, metaInfo, request);
		// end expectations
		
		ResourceProvider provider = createProvider();
		try {
			provider.createResources(request);
			fail("Exception expected");
		} catch (IllegalArgumentException e) {
			// expected
		}
		verify(dao, metaInfo, request);
	}
	
	@Test
	public void testCreateResource_Validate__NoHostGroupComponents() throws AmbariException, ResourceAlreadyExistsException, SystemException, UnsupportedPropertyException, NoSuchParentResourceException {
		Request request = createMock(Request.class);
		
		Map<String, ServiceInfo> services = new HashMap<String, ServiceInfo>();
		ServiceInfo service = new ServiceInfo();
		service.setName("test-service");
		services.put("test-service", service);
		
		List<ComponentInfo> serviceComponents = new ArrayList<ComponentInfo>();
		ComponentInfo component1 = new ComponentInfo();
		component1.setName("component1");
		ComponentInfo component2 = new ComponentInfo();
		component2.setName("component2");
		serviceComponents.add(component1);
		serviceComponents.add(component2);
		
		Set<Map<String, Object>> setProperties = getTestProperties();
		((HashSet<Map<String, String>>) setProperties.iterator().next().get(BlueprintResourceProvider.HOST_GROUP_PROPERTY_ID)).iterator().next().remove("components");
		
		// set expectations
		expect(request.getProperties()).andReturn(setProperties);
		expect(metaInfo.getServices("test-stack-name", "test-stack-version")).andReturn(services).anyTimes();
		expect(metaInfo.getComponentsByService("test-stack-name", "test-stack-version", "test-service")).andReturn(serviceComponents).anyTimes();
		
		replay(dao, metaInfo, request);
		// end expectations
		
		ResourceProvider provider = createProvider();
		try {
			provider.createResources(request);
			fail("Exception expected");
		} catch (IllegalArgumentException e) {
			// expected
		}
		verify(dao, metaInfo, request);
	}
	
	@Test
	public void testCreateResource_Validate__NoHostGroupComponentName() throws AmbariException, ResourceAlreadyExistsException, SystemException, UnsupportedPropertyException, NoSuchParentResourceException {
		Request request = createMock(Request.class);
		
		Map<String, ServiceInfo> services = new HashMap<String, ServiceInfo>();
		ServiceInfo service = new ServiceInfo();
		service.setName("test-service");
		services.put("test-service", service);
		
		List<ComponentInfo> serviceComponents = new ArrayList<ComponentInfo>();
		ComponentInfo component1 = new ComponentInfo();
		component1.setName("component1");
		ComponentInfo component2 = new ComponentInfo();
		component2.setName("component2");
		serviceComponents.add(component1);
		serviceComponents.add(component2);
		
		Set<Map<String, Object>> setProperties = getTestProperties();
		((HashSet<Map<String, String>>) ((HashSet<Map<String, Object>>) setProperties.iterator().next().get(BlueprintResourceProvider.HOST_GROUP_PROPERTY_ID)).iterator().next().get("components")).iterator().next().put("name", "");
		
		// set expectations
		expect(request.getProperties()).andReturn(setProperties);
		expect(metaInfo.getServices("test-stack-name", "test-stack-version")).andReturn(services).anyTimes();
		expect(metaInfo.getComponentsByService("test-stack-name", "test-stack-version", "test-service")).andReturn(serviceComponents).anyTimes();
		
		replay(dao, metaInfo, request);
		// end expectations
		
		ResourceProvider provider = createProvider();
		try {
			provider.createResources(request);
			fail("Exception expected");
		} catch (IllegalArgumentException e) {
			// expected
		}
		verify(dao, metaInfo, request);
	}
	
	@Test
	public void testCreateResource_Validate__InvalidComponent() throws AmbariException, ResourceAlreadyExistsException, SystemException, UnsupportedPropertyException, NoSuchParentResourceException {
		Request request = createMock(Request.class);
		
		Map<String, ServiceInfo> services = new HashMap<String, ServiceInfo>();
		ServiceInfo service = new ServiceInfo();
		service.setName("test-service");
		services.put("test-service", service);
		
		List<ComponentInfo> serviceComponents = new ArrayList<ComponentInfo>();
		ComponentInfo component1 = new ComponentInfo();
		// change component1->foo which results in a validation failure for bad component name
		component1.setName("foo");
		ComponentInfo component2 = new ComponentInfo();
		component2.setName("component2");
		serviceComponents.add(component1);
		serviceComponents.add(component2);
		
		Set<Map<String, Object>> setProperties = getTestProperties();
		
		// set expectations
		expect(request.getProperties()).andReturn(setProperties);
		expect(metaInfo.getServices("test-stack-name", "test-stack-version")).andReturn(services).anyTimes();
		expect(metaInfo.getComponentsByService("test-stack-name", "test-stack-version", "test-service")).andReturn(serviceComponents).anyTimes();
		
		replay(dao, metaInfo, request);
		// end expectations
		
		ResourceProvider provider = createProvider();
		try {
			provider.createResources(request);
			fail("Exception expected");
		} catch (IllegalArgumentException e) {
			// expected
		}
		verify(dao, metaInfo, request);
	}
	
	@Test
	public void testCreateResource_Validate__AmbariServerComponent() throws AmbariException, ResourceAlreadyExistsException, SystemException, UnsupportedPropertyException, NoSuchParentResourceException {
		Request request = createMock(Request.class);
		
		Map<String, ServiceInfo> services = new HashMap<String, ServiceInfo>();
		ServiceInfo service = new ServiceInfo();
		service.setName("test-service");
		services.put("test-service", service);
		
		List<ComponentInfo> serviceComponents = new ArrayList<ComponentInfo>();
		ComponentInfo component1 = new ComponentInfo();
		component1.setName("component1");
		ComponentInfo component2 = new ComponentInfo();
		component2.setName("component2");
		serviceComponents.add(component1);
		serviceComponents.add(component2);
		
		Set<Map<String, Object>> setProperties = getTestProperties();
		((HashSet<Map<String, String>>) ((HashSet<Map<String, Object>>) setProperties.iterator().next().get(BlueprintResourceProvider.HOST_GROUP_PROPERTY_ID)).iterator().next().get("components")).iterator().next().put("name", "AMBARI_SERVER");
		
		Capture<BlueprintEntity> entityCapture = new Capture<BlueprintEntity>();
		
		// set expectations
		expect(request.getProperties()).andReturn(setProperties);
		expect(dao.findByName(BLUEPRINT_NAME)).andReturn(null);
		expect(metaInfo.getServices("test-stack-name", "test-stack-version")).andReturn(services).anyTimes();
		expect(metaInfo.getComponentsByService("test-stack-name", "test-stack-version", "test-service")).andReturn(serviceComponents).anyTimes();
		expect(metaInfo.getComponentToService("test-stack-name", "test-stack-version", "component1")).andReturn("test-service").anyTimes();
		expect(metaInfo.getRequiredProperties("test-stack-name", "test-stack-version", "test-service")).andReturn(Collections.<String, org.apache.ambari.server.state.PropertyInfo> emptyMap()).anyTimes();
		
		dao.create(capture(entityCapture));
		
		replay(dao, metaInfo, request);
		// end expectations
		
		ResourceProvider provider = createProvider();
		AbstractResourceProviderTest.TestObserver observer = new AbstractResourceProviderTest.TestObserver();
		((ObservableResourceProvider) provider).addObserver(observer);
		
		provider.createResources(request);
		
		ResourceProviderEvent lastEvent = observer.getLastEvent();
		assertNotNull(lastEvent);
		assertEquals(Resource.Type.Blueprint, lastEvent.getResourceType());
		assertEquals(ResourceProviderEvent.Type.Create, lastEvent.getType());
		assertEquals(request, lastEvent.getRequest());
		assertNull(lastEvent.getPredicate());
		
		verify(dao, metaInfo, request);
	}
	
	private Set<Map<String, Object>> getTestProperties() {
		Map<String, String> mapHostGroupComponentProperties = new HashMap<String, String>();
		mapHostGroupComponentProperties.put(BlueprintResourceProvider.COMPONENT_NAME_PROPERTY_ID, "component1");
		
		Map<String, String> mapHostGroupComponentProperties2 = new HashMap<String, String>();
		mapHostGroupComponentProperties2.put(BlueprintResourceProvider.COMPONENT_NAME_PROPERTY_ID, "component2");
		
		Set<Map<String, String>> setComponentProperties = new HashSet<Map<String, String>>();
		setComponentProperties.add(mapHostGroupComponentProperties);
		setComponentProperties.add(mapHostGroupComponentProperties2);
		
		Set<Map<String, String>> setComponentProperties2 = new HashSet<Map<String, String>>();
		setComponentProperties2.add(mapHostGroupComponentProperties);
		
		Map<String, Object> mapHostGroupProperties = new HashMap<String, Object>();
		mapHostGroupProperties.put(BlueprintResourceProvider.HOST_GROUP_NAME_PROPERTY_ID, "group1");
		mapHostGroupProperties.put(BlueprintResourceProvider.HOST_GROUP_CARDINALITY_PROPERTY_ID, "1");
		mapHostGroupProperties.put(BlueprintResourceProvider.COMPONENT_PROPERTY_ID, setComponentProperties);
		
		Map<String, Object> mapHostGroupProperties2 = new HashMap<String, Object>();
		mapHostGroupProperties2.put(BlueprintResourceProvider.HOST_GROUP_NAME_PROPERTY_ID, "group2");
		mapHostGroupProperties2.put(BlueprintResourceProvider.HOST_GROUP_CARDINALITY_PROPERTY_ID, "2");
		mapHostGroupProperties2.put(BlueprintResourceProvider.COMPONENT_PROPERTY_ID, setComponentProperties2);
		
		Set<Map<String, Object>> setHostGroupProperties = new HashSet<Map<String, Object>>();
		setHostGroupProperties.add(mapHostGroupProperties);
		setHostGroupProperties.add(mapHostGroupProperties2);
		
		Map<String, Object> mapProperties = new HashMap<String, Object>();
		mapProperties.put(BlueprintResourceProvider.BLUEPRINT_NAME_PROPERTY_ID, BLUEPRINT_NAME);
		mapProperties.put(BlueprintResourceProvider.STACK_NAME_PROPERTY_ID, "test-stack-name");
		mapProperties.put(BlueprintResourceProvider.STACK_VERSION_PROPERTY_ID, "test-stack-version");
		mapProperties.put(BlueprintResourceProvider.HOST_GROUP_PROPERTY_ID, setHostGroupProperties);
		
		return Collections.singleton(mapProperties);
	}
	
	private void setConfigurationProperties(Set<Map<String, Object>> properties) {
		Map<String, String> clusterProperties = new HashMap<String, String>();
		clusterProperties.put("core-site/fs.trash.interval", "480");
		clusterProperties.put("core-site/ipc.client.idlethreshold", "8500");
		
		// single entry in set which was created in getTestProperties
		Map<String, Object> mapProperties = properties.iterator().next();
		mapProperties.put("configurations", Collections.singleton(clusterProperties));
		
		Map<String, Object> hostGroupProperties = new HashMap<String, Object>();
		hostGroupProperties.put("core-site/my.custom.hg.property", "anything");
		
		Collection<Map<String, Object>> hostGroups = (Collection<Map<String, Object>>) mapProperties.get(BlueprintResourceProvider.HOST_GROUP_PROPERTY_ID);
		
		for (Map<String, Object> hostGroupProps : hostGroups) {
			if (hostGroupProps.get(BlueprintResourceProvider.HOST_GROUP_NAME_PROPERTY_ID).equals("group2")) {
				hostGroupProps.put("configurations", Collections.singleton(hostGroupProperties));
				break;
			}
		}
	}
	
	private void validateEntity(BlueprintEntity entity, boolean containsConfig) {
		assertEquals(BLUEPRINT_NAME, entity.getBlueprintName());
		assertEquals("test-stack-name", entity.getStackName());
		assertEquals("test-stack-version", entity.getStackVersion());
		
		Collection<HostGroupEntity> hostGroupEntities = entity.getHostGroups();
		
		assertEquals(2, hostGroupEntities.size());
		for (HostGroupEntity hostGroup : hostGroupEntities) {
			assertEquals(BLUEPRINT_NAME, hostGroup.getBlueprintName());
			assertNotNull(hostGroup.getBlueprintEntity());
			Collection<HostGroupComponentEntity> componentEntities = hostGroup.getComponents();
			if (hostGroup.getName().equals("group1")) {
				assertEquals("1", hostGroup.getCardinality());
				assertEquals(2, componentEntities.size());
				Iterator<HostGroupComponentEntity> componentIterator = componentEntities.iterator();
				String name = componentIterator.next().getName();
				assertTrue(name.equals("component1") || name.equals("component2"));
				String name2 = componentIterator.next().getName();
				assertFalse(name.equals(name2));
				assertTrue(name2.equals("component1") || name2.equals("component2"));
			} else if (hostGroup.getName().equals("group2")) {
				assertEquals("2", hostGroup.getCardinality());
				assertEquals(1, componentEntities.size());
				HostGroupComponentEntity componentEntity = componentEntities.iterator().next();
				assertEquals("component1", componentEntity.getName());
				
				if (containsConfig) {
					Collection<HostGroupConfigEntity> configurations = hostGroup.getConfigurations();
					assertEquals(1, configurations.size());
					HostGroupConfigEntity hostGroupConfigEntity = configurations.iterator().next();
					assertEquals(BLUEPRINT_NAME, hostGroupConfigEntity.getBlueprintName());
					assertSame(hostGroup, hostGroupConfigEntity.getHostGroupEntity());
					assertEquals("core-site", hostGroupConfigEntity.getType());
					Map<String, String> properties = gson.<Map<String, String>> fromJson(hostGroupConfigEntity.getConfigData(), Map.class);
					assertEquals(1, properties.size());
					assertEquals("anything", properties.get("my.custom.hg.property"));
				}
			} else {
				fail("Unexpected host group name");
			}
		}
		Collection<BlueprintConfigEntity> configurations = entity.getConfigurations();
		if (containsConfig) {
			assertEquals(1, configurations.size());
			BlueprintConfigEntity blueprintConfigEntity = configurations.iterator().next();
			assertEquals(BLUEPRINT_NAME, blueprintConfigEntity.getBlueprintName());
			assertSame(entity, blueprintConfigEntity.getBlueprintEntity());
			assertEquals("core-site", blueprintConfigEntity.getType());
			Map<String, String> properties = gson.<Map<String, String>> fromJson(blueprintConfigEntity.getConfigData(), Map.class);
			assertEquals(2, properties.size());
			assertEquals("480", properties.get("fs.trash.interval"));
			assertEquals("8500", properties.get("ipc.client.idlethreshold"));
		} else {
			assertEquals(0, configurations.size());
		}
	}
	
	private void validateResource(Resource resource, boolean containsConfig) {
		assertEquals(BLUEPRINT_NAME, resource.getPropertyValue(BlueprintResourceProvider.BLUEPRINT_NAME_PROPERTY_ID));
		assertEquals("test-stack-name", resource.getPropertyValue(BlueprintResourceProvider.STACK_NAME_PROPERTY_ID));
		assertEquals("test-stack-version", resource.getPropertyValue(BlueprintResourceProvider.STACK_VERSION_PROPERTY_ID));
		
		Collection<Map<String, Object>> hostGroupProperties = (Collection<Map<String, Object>>) resource.getPropertyValue(BlueprintResourceProvider.HOST_GROUP_PROPERTY_ID);
		
		assertEquals(2, hostGroupProperties.size());
		for (Map<String, Object> hostGroupProps : hostGroupProperties) {
			String name = (String) hostGroupProps.get(BlueprintResourceProvider.HOST_GROUP_NAME_PROPERTY_ID);
			assertTrue(name.equals("group1") || name.equals("group2"));
			List<Map<String, String>> listComponents = (List<Map<String, String>>) hostGroupProps.get(BlueprintResourceProvider.COMPONENT_PROPERTY_ID);
			if (name.equals("group1")) {
				assertEquals("1", hostGroupProps.get(BlueprintResourceProvider.HOST_GROUP_CARDINALITY_PROPERTY_ID));
				assertEquals(2, listComponents.size());
				Map<String, String> mapComponent = listComponents.get(0);
				String componentName = mapComponent.get(BlueprintResourceProvider.COMPONENT_NAME_PROPERTY_ID);
				assertTrue(componentName.equals("component1") || componentName.equals("component2"));
				mapComponent = listComponents.get(1);
				String componentName2 = mapComponent.get(BlueprintResourceProvider.COMPONENT_NAME_PROPERTY_ID);
				assertFalse(componentName2.equals(componentName));
				assertTrue(componentName2.equals("component1") || componentName2.equals("component2"));
			} else if (name.equals("group2")) {
				assertEquals("2", hostGroupProps.get(BlueprintResourceProvider.HOST_GROUP_CARDINALITY_PROPERTY_ID));
				assertEquals(1, listComponents.size());
				Map<String, String> mapComponent = listComponents.get(0);
				String componentName = mapComponent.get(BlueprintResourceProvider.COMPONENT_NAME_PROPERTY_ID);
				assertEquals("component1", componentName);
			} else {
				fail("Unexpected host group name");
			}
		}
		
		if (containsConfig) {
			Collection<Map<String, Object>> blueprintConfigurations = (Collection<Map<String, Object>>) resource.getPropertyValue(BlueprintResourceProvider.CONFIGURATION_PROPERTY_ID);
			assertEquals(1, blueprintConfigurations.size());
			
			Map<String, Object> typeConfigs = blueprintConfigurations.iterator().next();
			assertEquals(1, typeConfigs.size());
			Map<String, String> properties = (Map<String, String>) typeConfigs.get("core-site");
			assertEquals(2, properties.size());
			assertEquals("480", properties.get("fs.trash.interval"));
			assertEquals("8500", properties.get("ipc.client.idlethreshold"));
		}
	}
	
	private BlueprintResourceProvider createProvider() {
		return new BlueprintResourceProvider(PropertyHelper.getPropertyIds(Resource.Type.Blueprint), PropertyHelper.getKeyPropertyIds(Resource.Type.Blueprint));
	}
	
	private BlueprintEntity createEntity(Map<String, Object> properties) {
		BlueprintEntity entity = new BlueprintEntity();
		entity.setBlueprintName((String) properties.get(BlueprintResourceProvider.BLUEPRINT_NAME_PROPERTY_ID));
		entity.setStackName((String) properties.get(BlueprintResourceProvider.STACK_NAME_PROPERTY_ID));
		entity.setStackVersion((String) properties.get(BlueprintResourceProvider.STACK_VERSION_PROPERTY_ID));
		
		Set<Map<String, Object>> hostGroupProperties = (Set<Map<String, Object>>) properties.get(BlueprintResourceProvider.HOST_GROUP_PROPERTY_ID);
		
		Collection<HostGroupEntity> hostGroups = new ArrayList<HostGroupEntity>();
		for (Map<String, Object> groupProperties : hostGroupProperties) {
			HostGroupEntity hostGroup = new HostGroupEntity();
			hostGroups.add(hostGroup);
			hostGroup.setName((String) groupProperties.get(BlueprintResourceProvider.HOST_GROUP_NAME_PROPERTY_ID));
			hostGroup.setCardinality((String) groupProperties.get(BlueprintResourceProvider.HOST_GROUP_CARDINALITY_PROPERTY_ID));
			hostGroup.setConfigurations(new ArrayList<HostGroupConfigEntity>());
			
			Set<Map<String, String>> setComponentProperties = (Set<Map<String, String>>) groupProperties.get(BlueprintResourceProvider.COMPONENT_PROPERTY_ID);
			
			Collection<HostGroupComponentEntity> components = new ArrayList<HostGroupComponentEntity>();
			for (Map<String, String> compProperties : setComponentProperties) {
				HostGroupComponentEntity component = new HostGroupComponentEntity();
				components.add(component);
				component.setName(compProperties.get(BlueprintResourceProvider.COMPONENT_NAME_PROPERTY_ID));
			}
			hostGroup.setComponents(components);
			
		}
		entity.setHostGroups(hostGroups);
		
		Collection<Map<String, String>> configProperties = (Collection<Map<String, String>>) properties.get(BlueprintResourceProvider.CONFIGURATION_PROPERTY_ID);
		Map<String, String> configData = new HashMap<String, String>();
		Collection<BlueprintConfigEntity> configs = new ArrayList<BlueprintConfigEntity>();
		if (configProperties != null) {
			for (Map<String, String> config : configProperties) {
				BlueprintConfigEntity configEntity = new BlueprintConfigEntity();
				for (Map.Entry<String, String> entry : config.entrySet()) {
					String absolutePropName = entry.getKey();
					
					int idx = absolutePropName.indexOf('/');
					if (configEntity.getType() == null) {
						configEntity.setType(absolutePropName.substring(0, idx));
					}
					configData.put(absolutePropName.substring(idx + 1), entry.getValue());
				}
				configEntity.setConfigData(gson.toJson(configData));
				configs.add(configEntity);
			}
		}
		entity.setConfigurations(configs);
		
		return entity;
	}
}
