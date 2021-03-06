/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.apache.ambari.server.orm.helpers.dbms;

import org.apache.ambari.server.orm.DBAccessor;
import org.eclipse.persistence.internal.databaseaccess.FieldTypeDefinition;
import org.eclipse.persistence.internal.databaseaccess.Platform;
import org.eclipse.persistence.internal.sessions.AbstractSession;
import org.eclipse.persistence.platform.database.DatabasePlatform;
import org.eclipse.persistence.tools.schemaframework.FieldDefinition;
import org.eclipse.persistence.tools.schemaframework.ForeignKeyConstraint;
import org.eclipse.persistence.tools.schemaframework.TableDefinition;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;

public class GenericDbmsHelper implements DbmsHelper {
	protected final DatabasePlatform databasePlatform;
	
	public GenericDbmsHelper(DatabasePlatform databasePlatform) {
		this.databasePlatform = databasePlatform;
	}
	
	@Override
	public boolean supportsColumnTypeChange() {
		return false;
	}
	
	@Override
	public String getRenameColumnStatement(String tableName, String oldName, DBAccessor.DBColumnInfo columnInfo) {
		StringBuilder stringBuilder = new StringBuilder();
		
		writeAlterTableClause(stringBuilder, tableName);
		writeColumnRenameString(stringBuilder, oldName, columnInfo);
		
		return stringBuilder.toString();
	}
	
	@Override
	public String getAlterColumnStatement(String tableName, DBAccessor.DBColumnInfo columnInfo) {
		StringBuilder stringBuilder = new StringBuilder();
		writeAlterTableClause(stringBuilder, tableName);
		writeColumnModifyString(stringBuilder, columnInfo);
		
		return stringBuilder.toString();
	}
	
	public StringBuilder writeAlterTableClause(StringBuilder builder, String tableName) {
		builder.append("ALTER TABLE ").append(tableName).append(" ");
		return builder;
	}
	
	public StringBuilder writeColumnModifyString(StringBuilder builder, DBAccessor.DBColumnInfo columnInfo) {
		throw new UnsupportedOperationException("Column type modification not supported for generic DB");
	}
	
	public StringBuilder writeColumnRenameString(StringBuilder builder, String oldName, DBAccessor.DBColumnInfo newColumnInfo) {
		throw new UnsupportedOperationException("Column rename not supported for generic DB");
	}
	
	public StringBuilder writeColumnType(StringBuilder builder, DBAccessor.DBColumnInfo columnInfo) {
		FieldTypeDefinition fieldType;
		
		fieldType = databasePlatform.getFieldTypeDefinition(columnInfo.getType());
		
		if (fieldType == null) { throw new IllegalArgumentException("Unable to convert data type"); }
		
		FieldDefinition definition = convertToFieldDefinition(columnInfo);
		
		StringWriter writer = new StringWriter();
		
		try {
			databasePlatform.printFieldTypeSize(writer, definition, fieldType, false); // Ambari doesn't use identity fields
		} catch (IOException ignored) {
			// no writing to file
		}
		
		builder.append(writer.toString());
		
		return builder;
	}
	
	/**
	 * get create table statement
	 * 
	 * @param tableName
	 * @param columns
	 * @param primaryKeyColumns
	 * @return
	 */
	@Override
	public String getCreateTableStatement(String tableName, List<DBAccessor.DBColumnInfo> columns, List<String> primaryKeyColumns) {
		Writer stringWriter = new StringWriter();
		writeCreateTableStatement(stringWriter, tableName, columns, primaryKeyColumns);
		return stringWriter.toString();
	}
	
	/**
	 * Write create table statement to writer TODO default Value of column not supported
	 */
	public Writer writeCreateTableStatement(Writer writer, String tableName, List<DBAccessor.DBColumnInfo> columns, List<String> primaryKeyColumns) {
		// TODO validateNames(String tableName, List<DBAccessor.DBColumnInfo> columns)
		// TODO validatePKNames(List<DBAccessor.DBColumnInfo> columns, String... primaryKeyColumns)
		
		TableDefinition tableDefinition = new TableDefinition();
		tableDefinition.setName(tableName);
		for (DBAccessor.DBColumnInfo columnInfo : columns) {
			// TODO case-sensitive for now
			int length = columnInfo.getLength() != null ? columnInfo.getLength() : 0;
			
			if (primaryKeyColumns.contains(columnInfo.getName())) {
				tableDefinition.addIdentityField(columnInfo.getName(), columnInfo.getType(), length);
			} else {
				FieldDefinition fieldDefinition = convertToFieldDefinition(columnInfo);
				tableDefinition.addField(fieldDefinition);
			}
		}
		
		// TODO possibly move code to avoid unnecessary dependencies and allow extension
		tableDefinition.buildCreationWriter(createStubAbstractSessionFromPlatform(databasePlatform), writer);
		
		return writer;
	}
	
	public FieldDefinition convertToFieldDefinition(DBAccessor.DBColumnInfo columnInfo) {
		int length = columnInfo.getLength() != null ? columnInfo.getLength() : 0;
		FieldDefinition fieldDefinition = new FieldDefinition(columnInfo.getName(), columnInfo.getType(), length);
		fieldDefinition.setShouldAllowNull(columnInfo.isNullable());
		return fieldDefinition;
	}
	
	/**
	 * get create index statement
	 * 
	 * @param indexName
	 * @param tableName
	 * @param columnNames
	 * @return
	 */
	@Override
	public String getCreateIndexStatement(String indexName, String tableName, String... columnNames) {
		// TODO validateColumnNames()
		String createIndex = databasePlatform.buildCreateIndex(tableName, indexName, columnNames);
		return createIndex;
	}
	
	@Override
	public String getAddForeignKeyStatement(String tableName, String constraintName, List<String> keyColumns, String referenceTableName, List<String> referenceColumns) {
		ForeignKeyConstraint foreignKeyConstraint = new ForeignKeyConstraint();
		foreignKeyConstraint.setName(constraintName);
		foreignKeyConstraint.setTargetTable(referenceTableName);
		foreignKeyConstraint.setSourceFields(keyColumns);
		foreignKeyConstraint.setTargetFields(referenceColumns);
		
		TableDefinition tableDefinition = new TableDefinition();
		tableDefinition.setName(tableName);
		
		Writer writer = new StringWriter();
		tableDefinition.buildConstraintCreationWriter(createStubAbstractSessionFromPlatform(databasePlatform), foreignKeyConstraint, writer);
		
		return writer.toString();
		
	}
	
	@Override
	public String getAddColumnStatement(String tableName, DBAccessor.DBColumnInfo columnInfo) {
		Writer writer = new StringWriter();
		
		TableDefinition tableDefinition = new TableDefinition();
		tableDefinition.setName(tableName);
		tableDefinition.buildAddFieldWriter(createStubAbstractSessionFromPlatform(databasePlatform), convertToFieldDefinition(columnInfo), writer);
		
		return writer.toString();
	}
	
	@Override
	public String getRenameColumnStatement(String tableName, String oldColumnName, String newColumnName) {
		
		throw new UnsupportedOperationException("Rename operation not supported.");
	}
	
	@Override
	public String getDropTableStatement(String tableName) {
		Writer writer = new StringWriter();
		
		TableDefinition tableDefinition = new TableDefinition();
		tableDefinition.setName(tableName);
		tableDefinition.buildDeletionWriter(createStubAbstractSessionFromPlatform(databasePlatform), writer);
		
		return writer.toString();
	}
	
	@Override
	public String getDropConstraintStatement(String tableName, String constraintName) {
		Writer writer = new StringWriter();
		
		ForeignKeyConstraint foreignKeyConstraint = new ForeignKeyConstraint();
		foreignKeyConstraint.setName(constraintName);
		foreignKeyConstraint.setTargetTable(tableName);
		
		TableDefinition tableDefinition = new TableDefinition();
		tableDefinition.setName(tableName);
		tableDefinition.buildConstraintDeletionWriter(createStubAbstractSessionFromPlatform(databasePlatform), foreignKeyConstraint, writer);
		
		return writer.toString();
	}
	
	@Override
	public String getDropSequenceStatement(String sequenceName) {
		StringWriter writer = new StringWriter();
		String defaultStmt = String.format("DROP sequence %s", sequenceName);
		
		try {
			Writer w = databasePlatform.buildSequenceObjectDeletionWriter(writer, sequenceName);
			return w != null ? w.toString() : defaultStmt;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return defaultStmt;
	}
	
	public AbstractSession createStubAbstractSessionFromPlatform(final DatabasePlatform databasePlatform) {
		return new AbstractSession() {
			@Override
			public Platform getDatasourcePlatform() {
				return databasePlatform;
			}
			
			@Override
			public DatabasePlatform getPlatform() {
				return databasePlatform;
			}
		};
	}
}
