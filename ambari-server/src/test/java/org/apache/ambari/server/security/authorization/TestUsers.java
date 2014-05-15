/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package org.apache.ambari.server.security.authorization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Properties;

import org.apache.ambari.server.AmbariException;
import org.apache.ambari.server.configuration.Configuration;
import org.apache.ambari.server.orm.GuiceJpaInitializer;
import org.apache.ambari.server.orm.InMemoryDefaultTestModule;
import org.apache.ambari.server.orm.dao.RoleDAO;
import org.apache.ambari.server.orm.dao.UserDAO;
import org.apache.ambari.server.orm.entities.RoleEntity;
import org.apache.ambari.server.orm.entities.UserEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.persist.PersistService;

public class TestUsers {
	private Injector injector;
	
	@Inject
	protected Users users;
	@Inject
	protected UserDAO userDAO;
	@Inject
	protected RoleDAO roleDAO;
	@Inject
	protected PasswordEncoder passwordEncoder;
	private Properties properties;
	
	@Before
	public void setup() throws AmbariException {
		InMemoryDefaultTestModule module = new InMemoryDefaultTestModule();
		properties = module.getProperties();
		injector = Guice.createInjector(module);
		injector.getInstance(GuiceJpaInitializer.class);
		injector.injectMembers(this);
		users.createDefaultRoles();
		Authentication auth = new UsernamePasswordAuthenticationToken("admin", null);
		SecurityContextHolder.getContext().setAuthentication(auth);
	}
	
	@After
	public void tearDown() throws AmbariException {
		injector.getInstance(PersistService.class).stop();
	}
	
	@Test
	public void testGetAllUsers() throws Exception {
		users.createUser("user", "user");
		users.createUser("admin", "admin");
		
		List<User> userList = users.getAllUsers();
		
		assertEquals(2, userList.size());
		
		for (User user : userList) {
			assertEquals(false, user.isLdapUser());
		}
		
		assertEquals(2, userDAO.findAll().size());
		
		UserEntity userEntity = userDAO.findLocalUserByName("user");
		assertNotNull("user", userEntity.getUserPassword());
		
		users.modifyPassword("user", "admin", "resu");
		
		assertNotSame(userEntity.getUserPassword(), userDAO.findLocalUserByName("user").getUserPassword());
	}
	
	@Test(expected = AmbariException.class)
	public void testModifyPassword() throws Exception {
		users.createUser("user", "user");
		
		UserEntity userEntity = userDAO.findLocalUserByName("user");
		
		assertNotSame("user", userEntity.getUserPassword());
		assertTrue(passwordEncoder.matches("user", userEntity.getUserPassword()));
		
		users.modifyPassword("user", "admin", "user_new_password");
		
		assertTrue("user_new_password".equals(userDAO.findLocalUserByName("user").getUserPassword()));
		
		users.modifyPassword("user", "error", "new");
		
		fail("Exception was not thrown");
	}
	
	@Test(expected = AmbariException.class)
	public void testPromoteUser() throws Exception {
		users.createUser("admin", "admin");
		users.createUser("admin2", "admin2");
		User user = users.getLocalUser("admin");
		assertTrue(user.getRoles().contains(users.getUserRole()));
		assertFalse(user.getRoles().contains(users.getAdminRole()));
		users.promoteToAdmin(user);
		user = users.getLocalUser("admin2");
		users.promoteToAdmin(user);
		
		user = users.getLocalUser("admin");
		assertTrue(user.getRoles().contains(users.getAdminRole()));
		
		users.demoteAdmin(user);
		
		user = users.getLocalUser("admin");
		assertFalse(user.getRoles().contains(users.getAdminRole()));
		
		user = users.getLocalUser("admin2");
		users.demoteAdmin(user);
		
	}
	
	@Test(expected = AmbariException.class)
	public void testRemoveUser() throws Exception {
		users.createUser("admin", "admin");
		User user = users.getLocalUser("admin");
		users.promoteToAdmin(user);
		
		user = users.getLocalUser("admin");
		assertTrue(user.getRoles().contains(users.getAdminRole()));
		
		users.removeUser(user);
	}
	
	@Test
	public void testPromoteLdapUser() throws Exception {
		createLdapUser();
		
		User ldapUser = users.getLdapUser("ldapUser");
		users.createUser("localadmin", "admin");
		User localUser = users.getLocalUser("localadmin");
		users.promoteToAdmin(localUser);
		
		users.promoteToAdmin(ldapUser);
		
		ldapUser = users.getLdapUser("ldapUser");
		assertTrue(ldapUser.getRoles().contains(users.getAdminRole()));
		
		users.demoteAdmin(ldapUser);
		
		ldapUser = users.getLdapUser("ldapUser");
		assertFalse(ldapUser.getRoles().contains(users.getAdminRole()));
		
		users.removeUser(ldapUser);
		
		// toggle group mapping
		properties.setProperty(Configuration.LDAP_GROUP_BASE_KEY, "ou=groups,dc=ambari,dc=apache,dc=org");
		createLdapUser();
		
		try {
			users.promoteToAdmin(ldapUser);
			fail("Not allowed with mapping on");
		} catch (AmbariException e) {
		}
		
		try {
			users.demoteAdmin(ldapUser);
			fail("Not allowed with mapping on");
		} catch (AmbariException e) {
		}
		
	}
	
	private void createLdapUser() {
		RoleEntity role = roleDAO.findByName(users.getUserRole());
		UserEntity ldapUser = new UserEntity();
		
		ldapUser.setUserName("ldapUser");
		ldapUser.setLdapUser(true);
		
		userDAO.create(ldapUser);
		
		UserEntity userEntity = userDAO.findLdapUserByName("ldapUser");
		
		userEntity.getRoleEntities().add(role);
		role.getUserEntities().add(ldapUser);
		
		userDAO.merge(ldapUser);
		roleDAO.merge(role);
	}
}
