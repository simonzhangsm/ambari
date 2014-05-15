/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package org.apache.ambari.server.upgrade;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import org.apache.ambari.server.AmbariException;
import org.apache.ambari.server.configuration.Configuration;
import org.apache.ambari.server.controller.AmbariManagementController;
import org.apache.ambari.server.controller.ConfigurationRequest;
import org.apache.ambari.server.orm.DBAccessor;
import org.apache.ambari.server.orm.dao.MetainfoDAO;
import org.apache.ambari.server.orm.entities.MetainfoEntity;
import org.apache.ambari.server.state.Cluster;
import org.apache.ambari.server.state.Clusters;
import org.apache.ambari.server.state.Config;
import org.apache.ambari.server.utils.VersionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractUpgradeCatalog implements UpgradeCatalog {
	@Inject
	protected DBAccessor dbAccessor;
	@Inject
	protected Configuration configuration;
	@Inject
	protected StackUpgradeUtil stackUpgradeUtil;
	
	private Injector injector;
	private static final Logger LOG = LoggerFactory.getLogger(AbstractUpgradeCatalog.class);
	private static final Map<String, UpgradeCatalog> upgradeCatalogMap = new HashMap<String, UpgradeCatalog>();
	
	@Inject
	public AbstractUpgradeCatalog(Injector injector) {
		this.injector = injector;
		registerCatalog(this);
	}
	
	/**
	 * Every subclass needs to register itself
	 */
	protected void registerCatalog(UpgradeCatalog upgradeCatalog) {
		upgradeCatalogMap.put(upgradeCatalog.getTargetVersion(), upgradeCatalog);
	}
	
	@Override
	public String getSourceVersion() {
		return null;
	}
	
	protected static UpgradeCatalog getUpgradeCatalog(String version) {
		return upgradeCatalogMap.get(version);
	}
	
	protected static class VersionComparator implements Comparator<UpgradeCatalog> {
		
		@Override
		public int compare(UpgradeCatalog upgradeCatalog1, UpgradeCatalog upgradeCatalog2) {
			return VersionUtils.compareVersions(upgradeCatalog1.getTargetVersion(), upgradeCatalog2.getTargetVersion(), 3);
		}
	}
	
	/**
	 * Update metainfo to new version.
	 */
	@Transactional
	public int updateMetaInfoVersion(String version) {
		int rows = 0;
		if (version != null) {
			MetainfoDAO metainfoDAO = injector.getInstance(MetainfoDAO.class);
			
			MetainfoEntity versionEntity = metainfoDAO.findByKey("version");
			
			if (versionEntity != null) {
				versionEntity.setMetainfoValue(version);
				metainfoDAO.merge(versionEntity);
			} else {
				versionEntity = new MetainfoEntity();
				versionEntity.setMetainfoName("version");
				versionEntity.setMetainfoValue(version);
				metainfoDAO.create(versionEntity);
			}
			
		}
		
		return rows;
	}
	
	protected String getDbType() {
		String dbUrl = configuration.getDatabaseUrl();
		String dbType;
		
		if (dbUrl.contains(Configuration.POSTGRES_DB_NAME)) {
			dbType = Configuration.POSTGRES_DB_NAME;
		} else if (dbUrl.contains(Configuration.ORACLE_DB_NAME)) {
			dbType = Configuration.ORACLE_DB_NAME;
		} else if (dbUrl.contains(Configuration.MYSQL_DB_NAME)) {
			dbType = Configuration.MYSQL_DB_NAME;
		} else if (dbUrl.contains(Configuration.DERBY_DB_NAME)) {
			dbType = Configuration.DERBY_DB_NAME;
		} else {
			throw new RuntimeException("Unable to determine database type.");
		}
		
		return dbType;
	}
	
	protected Provider<EntityManager> getEntityManagerProvider() {
		return injector.getProvider(EntityManager.class);
	}
	
	protected void executeInTransaction(Runnable func) {
		EntityManager entityManager = getEntityManagerProvider().get();
		if (entityManager.getTransaction().isActive()) { // already started, reuse
			func.run();
		} else {
			entityManager.getTransaction().begin();
			try {
				func.run();
				entityManager.getTransaction().commit();
			} catch (Exception e) {
				LOG.error("Error in transaction ", e);
				if (entityManager.getTransaction().isActive()) {
					entityManager.getTransaction().rollback();
				}
				throw new RuntimeException(e);
			}
			
		}
	}
	
	protected void changePostgresSearchPath() throws SQLException {
		String dbUser = configuration.getDatabaseUser();
		String dbName = configuration.getServerDBName();
		
		// wrap username with double quotes to accept old username "ambari-server"
		if (!dbUser.contains("\"")) {
			dbUser = String.format("\"%s\"", dbUser);
		}
		
		dbAccessor.executeQuery(String.format("ALTER SCHEMA %s OWNER TO %s;", dbName, dbUser));
		
		dbAccessor.executeQuery(String.format("ALTER ROLE %s SET search_path to '%s';", dbUser, dbName));
	}
	
	/**
	 * Create a new cluster scoped configuration with the new properties added to the existing set of properties.
	 * 
	 * @param configType Configuration type. (hdfs-site, etc.)
	 * @param properties Map of key value pairs to add / update.
	 */
	protected void updateConfigurationProperties(String configType, Map<String, String> properties, boolean updateIfExists) throws AmbariException {
		AmbariManagementController controller = injector.getInstance(AmbariManagementController.class);
		String newTag = "version" + System.currentTimeMillis();
		
		Clusters clusters = controller.getClusters();
		if (clusters == null) { return; }
		Map<String, Cluster> clusterMap = clusters.getClusters();
		
		if (clusterMap != null && !clusterMap.isEmpty()) {
			for (Cluster cluster : clusterMap.values()) {
				Config oldConfig = cluster.getDesiredConfigByType(configType);
				
				if (properties != null) {
					Map<String, Config> all = cluster.getConfigsByType(configType);
					if (all == null || !all.containsKey(newTag) || properties.size() > 0) {
						
						Map<String, String> mergedProperties = mergeProperties(oldConfig.getProperties(), properties, updateIfExists);
						
						LOG.info("Applying configuration with tag '%s' to " + "cluster '%s'", newTag, cluster.getClusterName());
						
						ConfigurationRequest cr = new ConfigurationRequest();
						cr.setClusterName(cluster.getClusterName());
						cr.setVersionTag(newTag);
						cr.setType(configType);
						cr.setProperties(mergedProperties);
						controller.createConfiguration(cr);
						
						Config baseConfig = cluster.getConfig(cr.getType(), cr.getVersionTag());
						if (baseConfig != null) {
							String authName = "ambari-upgrade";
							
							if (cluster.addDesiredConfig(authName, baseConfig)) {
								LOG.info("cluster '" + cluster.getClusterName() + "' " + "changed by: '" + authName + "'; " + "type='" + baseConfig.getType() + "' " + "tag='" + baseConfig.getVersionTag() + "'" + " from='" + oldConfig.getVersionTag() + "'");
							}
						}
					}
				}
			}
		}
	}
	
	private Map<String, String> mergeProperties(Map<String, String> originalProperties, Map<String, String> newProperties, boolean updateIfExists) {
		
		Map<String, String> properties = new HashMap<String, String>(originalProperties);
		for (Map.Entry<String, String> entry : newProperties.entrySet()) {
			if (!properties.containsKey(entry.getKey()) || updateIfExists) {
				properties.put(entry.getKey(), entry.getValue());
			}
		}
		return properties;
	}
	
	@Override
	public void upgradeSchema() throws AmbariException, SQLException {
		if (getDbType().equals(Configuration.POSTGRES_DB_NAME)) {
			changePostgresSearchPath();
		}
		
		this.executeDDLUpdates();
	}
	
	@Override
	public void upgradeData() throws AmbariException, SQLException {
		executeDMLUpdates();
		updateMetaInfoVersion(getTargetVersion());
	}
	
	protected abstract void executeDDLUpdates() throws AmbariException, SQLException;
	
	protected abstract void executeDMLUpdates() throws AmbariException, SQLException;
	
	@Override
	public String toString() {
		return "{ " + this.getClass().getCanonicalName() + ": " + "sourceVersion = " + getSourceVersion() + ", " + "targetVersion = " + getTargetVersion() + " }";
	}
}
