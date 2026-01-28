/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.infra.metadata.database.schema.builder;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.database.connector.core.GlobalDataSourceRegistry;
import org.apache.shardingsphere.database.connector.core.metadata.data.loader.MetaDataLoader;
import org.apache.shardingsphere.database.connector.core.metadata.data.loader.MetaDataLoaderMaterial;
import org.apache.shardingsphere.database.connector.core.metadata.data.model.SchemaMetaData;
import org.apache.shardingsphere.database.connector.core.metadata.data.model.TableMetaData;
import org.apache.shardingsphere.database.connector.core.metadata.database.enums.TableType;
import org.apache.shardingsphere.database.connector.core.metadata.database.metadata.DialectDatabaseMetaData;
import org.apache.shardingsphere.database.connector.core.metadata.database.system.SystemDatabase;
import org.apache.shardingsphere.database.connector.core.metadata.database.system.SystemTable;
import org.apache.shardingsphere.database.connector.core.type.DatabaseType;
import org.apache.shardingsphere.database.connector.core.type.DatabaseTypeRegistry;
import org.apache.shardingsphere.infra.config.props.ConfigurationProperties;
import org.apache.shardingsphere.infra.config.props.temporary.TemporaryConfigurationPropertyKey;
import org.apache.shardingsphere.infra.metadata.database.schema.manager.SystemSchemaManager;
import org.apache.shardingsphere.infra.metadata.database.schema.model.ShardingSphereColumn;
import org.apache.shardingsphere.infra.metadata.database.schema.model.ShardingSphereConstraint;
import org.apache.shardingsphere.infra.metadata.database.schema.model.ShardingSphereIndex;
import org.apache.shardingsphere.infra.metadata.database.schema.model.ShardingSphereSchema;
import org.apache.shardingsphere.infra.metadata.database.schema.model.ShardingSphereTable;
import org.apache.shardingsphere.infra.yaml.schema.pojo.YamlShardingSphereTable;
import org.apache.shardingsphere.infra.yaml.schema.swapper.YamlTableSwapper;
import org.apache.shardingsphere.infra.yaml.schema.swapper.YamlColumnSwapper;
import org.apache.shardingsphere.infra.yaml.schema.swapper.YamlConstraintSwapper;
import org.apache.shardingsphere.infra.yaml.schema.swapper.YamlIndexSwapper;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * System schema builder.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class SystemSchemaBuilder {
    
    private static final YamlTableSwapper TABLE_SWAPPER = new YamlTableSwapper();
    
    private static final YamlColumnSwapper COLUMN_SWAPPER = new YamlColumnSwapper();
    
    private static final YamlIndexSwapper INDEX_SWAPPER = new YamlIndexSwapper();
    
    private static final YamlConstraintSwapper CONSTRAINT_SWAPPER = new YamlConstraintSwapper();
    
    /**
     * Build system schema.
     *
     * @param databaseName database name
     * @param databaseType database type
     * @param props configuration properties
     * @return ShardingSphere system schema map
     */
    public static Map<String, ShardingSphereSchema> build(final String databaseName, final DatabaseType databaseType, final ConfigurationProperties props) {
        SystemDatabase systemDatabase = new SystemDatabase(databaseType);
        boolean isSystemSchemaMetaDataEnabled = isSystemSchemaMetaDataEnabled(props.getProps());
        return getSystemSchemas(databaseName, databaseType, systemDatabase).stream()
                .collect(Collectors.toMap(String::toLowerCase, each -> createSchema(each, databaseType, isSystemSchemaMetaDataEnabled), (oldValue, currentValue) -> currentValue, LinkedHashMap::new));
    }
    
    private static boolean isSystemSchemaMetaDataEnabled(final Properties props) {
        TemporaryConfigurationPropertyKey configKey = TemporaryConfigurationPropertyKey.SYSTEM_SCHEMA_METADATA_ASSEMBLY_ENABLED;
        return Boolean.parseBoolean(props.getOrDefault(configKey.getKey(), configKey.getDefaultValue()).toString());
    }
    
    private static Collection<String> getSystemSchemas(final String originalDatabaseName, final DatabaseType databaseType, final SystemDatabase systemDatabase) {
        DialectDatabaseMetaData dialectDatabaseMetaData = new DatabaseTypeRegistry(databaseType).getDialectDatabaseMetaData();
        return systemDatabase.getSystemSchemas(dialectDatabaseMetaData.getSchemaOption().getDefaultSchema().isPresent() ? "postgres" : originalDatabaseName);
    }
    
    private static ShardingSphereSchema createSchema(final String schemaName, final DatabaseType databaseType, final boolean isSystemSchemaMetadataEnabled) {
        Optional<ShardingSphereSchema> metaDataSchema = loadSystemSchemaFromMetaData(schemaName, databaseType, isSystemSchemaMetadataEnabled);
        if (metaDataSchema.isPresent()) {
            return metaDataSchema.get();
        }
        Collection<ShardingSphereTable> tables = new LinkedList<>();
        SystemTable systemTable = new SystemTable(databaseType);
        for (InputStream each : SystemSchemaManager.getAllInputStreams(databaseType.getType(), schemaName)) {
            YamlShardingSphereTable metaData = new Yaml().loadAs(each, YamlShardingSphereTable.class);
            if (isSystemSchemaMetadataEnabled || systemTable.isSupportedSystemTable(schemaName, metaData.getName())) {
                tables.add(TABLE_SWAPPER.swapToObject(metaData));
            }
        }
        return new ShardingSphereSchema(schemaName, tables, Collections.emptyList(), databaseType);
    }
    
    private static Optional<ShardingSphereSchema> loadSystemSchemaFromMetaData(final String schemaName, final DatabaseType databaseType, final boolean isSystemSchemaMetadataEnabled) {
        if (!"MySQL".equalsIgnoreCase(databaseType.getType())) {
            return Optional.empty();
        }
        Optional<MetaDataLoaderMaterial> material = findMetaDataLoaderMaterial(databaseType, schemaName);
        if (!material.isPresent()) {
            return Optional.empty();
        }
        try {
            Map<String, SchemaMetaData> schemaMetaDataMap = MetaDataLoader.load(Collections.singleton(material.get()));
            SchemaMetaData schemaMetaData = schemaMetaDataMap.get(schemaName);
            if (null == schemaMetaData) {
                return Optional.empty();
            }
            return Optional.of(new ShardingSphereSchema(schemaName, convertToTables(schemaMetaData.getTables(), schemaName, databaseType, isSystemSchemaMetadataEnabled),
                    Collections.emptyList(), databaseType));
        } catch (final SQLException ex) {
            log.warn("Load system schema metadata failed for {}.{}", databaseType.getType(), schemaName, ex);
            return Optional.empty();
        }
    }
    
    private static Optional<MetaDataLoaderMaterial> findMetaDataLoaderMaterial(final DatabaseType databaseType, final String schemaName) {
        Map<String, javax.sql.DataSource> cachedDataSources = GlobalDataSourceRegistry.getInstance().getCachedDataSources();
        if (cachedDataSources.isEmpty()) {
            return Optional.empty();
        }
        for (Entry<String, javax.sql.DataSource> entry : cachedDataSources.entrySet()) {
            MetaDataLoaderMaterial material = new MetaDataLoaderMaterial(SystemSchemaManager.getTables(databaseType.getType(), schemaName),
                    entry.getKey(), entry.getValue(), databaseType, schemaName);
            return Optional.of(material);
        }
        return Optional.empty();
    }
    
    private static Collection<ShardingSphereTable> convertToTables(final Collection<TableMetaData> tableMetaDataList, final String schemaName,
                                                                   final DatabaseType databaseType, final boolean isSystemSchemaMetadataEnabled) {
        SystemTable systemTable = new SystemTable(databaseType);
        return tableMetaDataList.stream()
                .filter(each -> isSystemSchemaMetadataEnabled || systemTable.isSupportedSystemTable(schemaName, each.getName()))
                .map(each -> new ShardingSphereTable(each.getName(), convertToColumns(each.getColumns()), convertToIndexes(each.getIndexes()),
                        convertToConstraints(each.getConstraints()), getTableType(each.getType())))
                .collect(Collectors.toList());
    }
    
    private static TableType getTableType(final TableType tableType) {
        return null == tableType ? TableType.TABLE : tableType;
    }
    
    private static Collection<ShardingSphereColumn> convertToColumns(final Collection<org.apache.shardingsphere.database.connector.core.metadata.data.model.ColumnMetaData> columnMetaDataList) {
        return columnMetaDataList.stream()
                .map(COLUMN_SWAPPER::swapToYamlConfiguration)
                .map(COLUMN_SWAPPER::swapToObject)
                .collect(Collectors.toList());
    }
    
    private static Collection<ShardingSphereIndex> convertToIndexes(final Collection<org.apache.shardingsphere.database.connector.core.metadata.data.model.IndexMetaData> indexMetaDataList) {
        return indexMetaDataList.stream()
                .map(INDEX_SWAPPER::swapToYamlConfiguration)
                .map(INDEX_SWAPPER::swapToObject)
                .collect(Collectors.toList());
    }
    
    private static Collection<ShardingSphereConstraint> convertToConstraints(final Collection<org.apache.shardingsphere.database.connector.core.metadata.data.model.ConstraintMetaData> constraintMetaDataList) {
        return constraintMetaDataList.stream()
                .map(CONSTRAINT_SWAPPER::swapToYamlConfiguration)
                .map(CONSTRAINT_SWAPPER::swapToObject)
                .collect(Collectors.toList());
    }
}
