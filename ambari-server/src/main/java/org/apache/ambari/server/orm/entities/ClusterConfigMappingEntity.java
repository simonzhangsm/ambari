/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package org.apache.ambari.server.orm.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * Entity that maps to a cluster config mapping.
 */
@IdClass(org.apache.ambari.server.orm.entities.ClusterConfigMappingEntityPK.class)
@Table(name = "clusterconfigmapping")
@Entity
public class ClusterConfigMappingEntity {
	
	@Id
	@Column(name = "cluster_id", insertable = false, updatable = false, nullable = false)
	private Long clusterId;
	
	@Id
	@Column(name = "type_name", insertable = true, updatable = false, nullable = false)
	private String typeName;
	
	@Id
	@Column(name = "create_timestamp", insertable = true, updatable = false, nullable = false)
	private Long createTimestamp;
	
	@Column(name = "version_tag", insertable = true, updatable = false, nullable = false)
	private String versionTag;
	
	@Column(name = "selected", insertable = true, updatable = true, nullable = false)
	private int selectedInd = 0;
	
	@Column(name = "user_name", insertable = true, updatable = true, nullable = false)
	private String user;
	
	@ManyToOne
	@JoinColumn(name = "cluster_id", referencedColumnName = "cluster_id", nullable = false)
	private ClusterEntity clusterEntity;
	
	public Long getClusterId() {
		return this.clusterId;
	}
	
	public void setClusterId(Long id) {
		this.clusterId = id;
	}
	
	public String getType() {
		return this.typeName;
	}
	
	public void setType(String type) {
		this.typeName = type;
	}
	
	public Long getCreateTimestamp() {
		return this.createTimestamp;
	}
	
	public void setCreateTimestamp(Long timestamp) {
		this.createTimestamp = timestamp;
	}
	
	public String getVersion() {
		return this.versionTag;
	}
	
	public void setVersion(String version) {
		this.versionTag = version;
	}
	
	public int isSelected() {
		return this.selectedInd;
	}
	
	public void setSelected(int selected) {
		this.selectedInd = selected;
	}
	
	/**
	 * @return the user
	 */
	public String getUser() {
		return this.user;
	}
	
	/**
	 * @param userName the user
	 */
	public void setUser(String userName) {
		this.user = userName;
	}
	
	public ClusterEntity getClusterEntity() {
		return this.clusterEntity;
	}
	
	public void setClusterEntity(ClusterEntity entity) {
		this.clusterEntity = entity;
	}
	
}
