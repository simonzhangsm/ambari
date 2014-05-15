/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.apache.ambari.server.upgrade;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMockBuilder;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.apache.ambari.server.configuration.Configuration;
import org.apache.ambari.server.orm.DBAccessor;
import org.easymock.Capture;
import org.junit.Assert;
import org.junit.Test;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

/**
 * UpgradeCatalog161 unit tests.
 */
public class UpgradeCatalog161Test {
	
	@Test
	public void testExecuteDDLUpdates() throws Exception {
		
		final DBAccessor dbAccessor = createNiceMock(DBAccessor.class);
		Configuration configuration = createNiceMock(Configuration.class);
		Capture<List<DBAccessor.DBColumnInfo>> operationLevelEntitycolumnCapture = new Capture<List<DBAccessor.DBColumnInfo>>();
		
		expect(configuration.getDatabaseUrl()).andReturn(Configuration.JDBC_IN_MEMORY_URL).anyTimes();
		
		setOperationLevelEntityConfigExpectations(dbAccessor, operationLevelEntitycolumnCapture);
		
		replay(dbAccessor, configuration);
		AbstractUpgradeCatalog upgradeCatalog = getUpgradeCatalog(dbAccessor);
		Class<?> c = AbstractUpgradeCatalog.class;
		Field f = c.getDeclaredField("configuration");
		f.setAccessible(true);
		f.set(upgradeCatalog, configuration);
		
		upgradeCatalog.executeDDLUpdates();
		verify(dbAccessor, configuration);
		
		assertOperationLevelEntityColumns(operationLevelEntitycolumnCapture);
	}
	
	@Test
	public void testExecuteDMLUpdates() throws Exception {
		Configuration configuration = createNiceMock(Configuration.class);
		DBAccessor dbAccessor = createNiceMock(DBAccessor.class);
		
		Method m = AbstractUpgradeCatalog.class.getDeclaredMethod("updateConfigurationProperties", String.class, Map.class, boolean.class);
		
		UpgradeCatalog161 upgradeCatalog = createMockBuilder(UpgradeCatalog161.class).addMockedMethod(m).createMock();
		
		expect(configuration.getDatabaseUrl()).andReturn(Configuration.JDBC_IN_MEMORY_URL).anyTimes();
		
		replay(upgradeCatalog, dbAccessor, configuration);
		
		Class<?> c = AbstractUpgradeCatalog.class;
		Field f = c.getDeclaredField("configuration");
		f.setAccessible(true);
		f.set(upgradeCatalog, configuration);
		f = c.getDeclaredField("dbAccessor");
		f.setAccessible(true);
		f.set(upgradeCatalog, dbAccessor);
		
		upgradeCatalog.executeDMLUpdates();
		
		verify(upgradeCatalog, dbAccessor, configuration);
	}
	
	@Test
	public void testGetTargetVersion() throws Exception {
		final DBAccessor dbAccessor = createNiceMock(DBAccessor.class);
		UpgradeCatalog upgradeCatalog = getUpgradeCatalog(dbAccessor);
		
		Assert.assertEquals("1.6.1", upgradeCatalog.getTargetVersion());
	}
	
	private AbstractUpgradeCatalog getUpgradeCatalog(final DBAccessor dbAccessor) {
		Module module = new Module() {
			@Override
			public void configure(Binder binder) {
				binder.bind(DBAccessor.class).toInstance(dbAccessor);
			}
		};
		Injector injector = Guice.createInjector(module);
		return injector.getInstance(UpgradeCatalog161.class);
	}
	
	private void setOperationLevelEntityConfigExpectations(DBAccessor dbAccessor, Capture<List<DBAccessor.DBColumnInfo>> operationLevelEntitycolumnCapture) throws SQLException {
		
		dbAccessor.createTable(eq("requestoperationlevel"), capture(operationLevelEntitycolumnCapture), eq("operation_level_id"));
		
		dbAccessor.addFKConstraint("requestoperationlevel", "FK_req_op_level_req_id", "request_id", "request", "request_id", true);
	}
	
	private void assertOperationLevelEntityColumns(Capture<List<DBAccessor.DBColumnInfo>> operationLevelEntitycolumnCapture) {
		List<DBAccessor.DBColumnInfo> columns = operationLevelEntitycolumnCapture.getValue();
		assertEquals(7, columns.size());
		
		DBAccessor.DBColumnInfo column = columns.get(0);
		assertEquals("operation_level_id", column.getName());
		assertNull(column.getLength());
		assertEquals(Long.class, column.getType());
		assertNull(column.getDefaultValue());
		assertFalse(column.isNullable());
		
		column = columns.get(1);
		assertEquals("request_id", column.getName());
		assertNull(column.getLength());
		assertEquals(Long.class, column.getType());
		assertNull(column.getDefaultValue());
		assertFalse(column.isNullable());
		
		column = columns.get(2);
		assertEquals("level_name", column.getName());
		assertEquals(255, (int) column.getLength());
		assertEquals(String.class, column.getType());
		assertNull(column.getDefaultValue());
		assertTrue(column.isNullable());
		
		column = columns.get(3);
		assertEquals("cluster_name", column.getName());
		assertEquals(255, (int) column.getLength());
		assertEquals(String.class, column.getType());
		assertNull(column.getDefaultValue());
		assertTrue(column.isNullable());
		
		column = columns.get(4);
		assertEquals("service_name", column.getName());
		assertEquals(255, (int) column.getLength());
		assertEquals(String.class, column.getType());
		assertNull(column.getDefaultValue());
		assertTrue(column.isNullable());
		
		column = columns.get(5);
		assertEquals("host_component_name", column.getName());
		assertEquals(255, (int) column.getLength());
		assertEquals(String.class, column.getType());
		assertNull(column.getDefaultValue());
		assertTrue(column.isNullable());
		
		column = columns.get(6);
		assertEquals("host_name", column.getName());
		assertEquals(255, (int) column.getLength());
		assertEquals(String.class, column.getType());
		assertNull(column.getDefaultValue());
		assertTrue(column.isNullable());
		
	}
	
}
