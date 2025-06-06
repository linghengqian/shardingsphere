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

package org.apache.shardingsphere.single.util;

import com.cedarsoftware.util.CaseInsensitiveSet;
import com.google.common.base.Splitter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.shardingsphere.infra.database.core.metadata.database.metadata.DialectDatabaseMetaData;
import org.apache.shardingsphere.infra.database.core.type.DatabaseType;
import org.apache.shardingsphere.infra.database.core.type.DatabaseTypeRegistry;
import org.apache.shardingsphere.infra.datanode.DataNode;
import org.apache.shardingsphere.infra.rule.ShardingSphereRule;
import org.apache.shardingsphere.infra.rule.attribute.table.TableMapperRuleAttribute;
import org.apache.shardingsphere.single.constant.SingleTableConstants;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Single table load utils.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SingleTableLoadUtils {
    
    private static final String DELIMITER = ",";
    
    /**
     * Get excluded tables.
     *
     * @param builtRules built rules
     * @return excluded tables
     */
    public static Collection<String> getExcludedTables(final Collection<ShardingSphereRule> builtRules) {
        Collection<String> result = new CaseInsensitiveSet<>();
        for (ShardingSphereRule each : builtRules) {
            Optional<TableMapperRuleAttribute> ruleAttribute = each.getAttributes().findAttribute(TableMapperRuleAttribute.class);
            if (ruleAttribute.isPresent()) {
                result.addAll(ruleAttribute.get().getDistributedTableNames());
                result.addAll(ruleAttribute.get().getActualTableNames());
            }
        }
        return result;
    }
    
    /**
     * Get feature required single tables.
     *
     * @param builtRules built rules
     * @return feature required single tables
     */
    public static Collection<String> getFeatureRequiredSingleTables(final Collection<ShardingSphereRule> builtRules) {
        Collection<String> result = new CaseInsensitiveSet<>();
        for (ShardingSphereRule each : builtRules) {
            Optional<TableMapperRuleAttribute> ruleAttribute = each.getAttributes().findAttribute(TableMapperRuleAttribute.class);
            if (!ruleAttribute.isPresent()) {
                continue;
            }
            if (!ruleAttribute.get().getEnhancedTableNames().isEmpty() && ruleAttribute.get().getDistributedTableNames().isEmpty()) {
                result.addAll(ruleAttribute.get().getEnhancedTableNames());
            }
        }
        return result;
    }
    
    /**
     * Split table lines.
     *
     * @param tables tables in configuration
     * @return split tables
     */
    public static Collection<String> splitTableLines(final Collection<String> tables) {
        Collection<String> result = new LinkedHashSet<>();
        for (String each : tables) {
            if (each.contains(DELIMITER)) {
                result.addAll(Splitter.on(DELIMITER).omitEmptyStrings().splitToList(each));
            } else {
                result.add(each);
            }
        }
        return result;
    }
    
    /**
     * Convert tables to data nodes.
     *
     * @param databaseName database name
     * @param databaseType database type
     * @param tables tables in configuration
     * @return data nodes
     */
    public static Collection<DataNode> convertToDataNodes(final String databaseName, final DatabaseType databaseType, final Collection<String> tables) {
        return tables.stream().map(each -> new DataNode(databaseName, databaseType, each)).collect(Collectors.toCollection(() -> new LinkedHashSet<>(tables.size(), 1F)));
    }
    
    /**
     * Get all tables node string.
     *
     * @param databaseType database type
     * @return node string for all tables
     */
    public static String getAllTablesNodeStr(final DatabaseType databaseType) {
        DialectDatabaseMetaData dialectDatabaseMetaData = new DatabaseTypeRegistry(databaseType).getDialectDatabaseMetaData();
        return dialectDatabaseMetaData.getSchemaOption().getDefaultSchema().isPresent() ? SingleTableConstants.ALL_SCHEMA_TABLES : SingleTableConstants.ALL_TABLES;
    }
    
    /**
     * Get all tables node string for data source.
     *
     * @param databaseType database type
     * @param dataSourceName data source name
     * @param schemaName schema name
     * @return node string for all tables
     */
    public static String getAllTablesNodeStrFromDataSource(final DatabaseType databaseType, final String dataSourceName, final String schemaName) {
        DialectDatabaseMetaData dialectDatabaseMetaData = new DatabaseTypeRegistry(databaseType).getDialectDatabaseMetaData();
        return dialectDatabaseMetaData.getSchemaOption().getDefaultSchema().isPresent()
                ? formatDataNode(dataSourceName, schemaName, SingleTableConstants.ASTERISK)
                : formatDataNode(dataSourceName, SingleTableConstants.ASTERISK);
    }
    
    /**
     * Get data node String.
     *
     * @param databaseType database type
     * @param dataSourceName data source name
     * @param schemaName schema name
     * @param tableName table name
     * @return data node string
     */
    public static String getDataNodeString(final DatabaseType databaseType, final String dataSourceName, final String schemaName, final String tableName) {
        DialectDatabaseMetaData dialectDatabaseMetaData = new DatabaseTypeRegistry(databaseType).getDialectDatabaseMetaData();
        return dialectDatabaseMetaData.getSchemaOption().getDefaultSchema().isPresent() ? formatDataNode(dataSourceName, schemaName, tableName) : formatDataNode(dataSourceName, tableName);
    }
    
    private static String formatDataNode(final String dataSourceName, final String tableName) {
        return String.format("%s.%s", dataSourceName, tableName);
    }
    
    private static String formatDataNode(final String dataSourceName, final String schemaName, final String tableName) {
        return String.format("%s.%s.%s", dataSourceName, schemaName, tableName);
    }
}
