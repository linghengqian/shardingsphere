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

package org.apache.shardingsphere.infra.metadata.database.schema.manager;

import com.cedarsoftware.util.CaseInsensitiveMap;
import com.cedarsoftware.util.CaseInsensitiveSet;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;
import org.apache.shardingsphere.database.connector.core.GlobalDataSourceRegistry;
import org.apache.shardingsphere.database.connector.core.metadata.database.system.SystemDatabase;
import org.apache.shardingsphere.database.connector.core.type.DatabaseType;
import org.apache.shardingsphere.infra.spi.type.typed.TypedSPILoader;
import org.apache.shardingsphere.infra.util.directory.ClasspathResourceDirectoryReader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * System schema manager.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class SystemSchemaManager {
    
    private static final Map<String, DialectSystemSchemaManager> DATABASE_TYPE_AND_SYSTEM_SCHEMA_MANAGER_MAP;
    
    private static final String COMMON = "common";
    
    private static final String MYSQL_DATABASE_TYPE = "MySQL";
    
    private static final String MYSQL_SYSTEM_TABLE_SQL = "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=?";
    
    private static final String MYSQL_TABLE_TYPE_SQL = "SELECT TABLE_TYPE FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?";
    
    private static final String MYSQL_TABLE_TYPE_VIEW = "VIEW";
    
    private static final String MYSQL_YAML_TABLE_TEMPLATE = "name: %s%s%stype: %s%scolumns: {}%s";
    
    static {
        List<String> resourceNames;
        try (Stream<String> resourceNameStream = ClasspathResourceDirectoryReader.read("schema")) {
            resourceNames = resourceNameStream.filter(each -> each.endsWith(".yaml")).collect(Collectors.toList());
        }
        DATABASE_TYPE_AND_SYSTEM_SCHEMA_MANAGER_MAP = new CaseInsensitiveMap<>();
        for (String each : resourceNames) {
            String[] pathParts = each.split("/");
            if (4 == pathParts.length) {
                String databaseType = pathParts[1];
                String schemaName = pathParts[2];
                String tableName = Strings.CS.removeEnd(pathParts[3], ".yaml");
                String resourcePath = String.join("/", pathParts);
                DialectSystemSchemaManager dialectSystemSchemaManager = DATABASE_TYPE_AND_SYSTEM_SCHEMA_MANAGER_MAP.computeIfAbsent(databaseType, key -> new DialectSystemSchemaManager());
                dialectSystemSchemaManager.putTable(schemaName, tableName);
                dialectSystemSchemaManager.putResource(schemaName, resourcePath);
            }
        }
    }
    
    /**
     * Judge whether the current table is system table.
     *
     * @param schema schema
     * @param tableName table name
     * @return is system table or not
     */
    public static boolean isSystemTable(final String schema, final String tableName) {
        return DATABASE_TYPE_AND_SYSTEM_SCHEMA_MANAGER_MAP.entrySet().stream().anyMatch(entry -> entry.getValue().getTables(schema).contains(tableName))
                || loadMySQLSystemTableNames(schema).contains(tableName);
    }
    
    /**
     * Judge whether the current table is system table.
     *
     * @param databaseType database type
     * @param schema schema
     * @param tableName table name
     * @return is system table or not
     */
    public static boolean isSystemTable(final String databaseType, final String schema, final String tableName) {
        if (isMySQLDatabaseType(databaseType)) {
            if (null == schema) {
                return isMySQLSystemTableWithNullSchema(tableName);
            }
            return loadMySQLSystemTableNames(schema).contains(tableName) || getDialectSystemSchemaManager(COMMON).isSystemTable(schema, tableName);
        }
        return getDialectSystemSchemaManager(databaseType).isSystemTable(schema, tableName)
                || getDialectSystemSchemaManager(COMMON).isSystemTable(schema, tableName);
    }
    
    /**
     * Judge whether the current table is system table.
     *
     * @param databaseType database type
     * @param schema schema
     * @param tableNames table names
     * @return is system table or not
     */
    public static boolean isSystemTable(final String databaseType, final String schema, final Collection<String> tableNames) {
        if (isMySQLDatabaseType(databaseType)) {
            return loadMySQLSystemTableNames(schema).containsAll(tableNames) || getDialectSystemSchemaManager(COMMON).isSystemTable(schema, tableNames);
        }
        return getDialectSystemSchemaManager(databaseType).isSystemTable(schema, tableNames)
                || getDialectSystemSchemaManager(COMMON).isSystemTable(schema, tableNames);
    }
    
    /**
     * Get tables.
     *
     * @param databaseType database type
     * @param schema schema
     * @return optional tables
     */
    public static Collection<String> getTables(final String databaseType, final String schema) {
        Collection<String> result = new LinkedList<>();
        if (isMySQLDatabaseType(databaseType)) {
            result.addAll(loadMySQLSystemTableNames(schema));
        } else {
            result.addAll(getDialectSystemSchemaManager(databaseType).getTables(schema));
        }
        result.addAll(getDialectSystemSchemaManager(COMMON).getTables(schema));
        return result;
    }
    
    /**
     * Get all input streams.
     *
     * @param databaseType database type
     * @param schema schema
     * @return input streams
     */
    public static Collection<InputStream> getAllInputStreams(final String databaseType, final String schema) {
        Collection<InputStream> result = new LinkedList<>();
        result.addAll(getDialectSystemSchemaManager(databaseType).getAllInputStreams(schema));
        result.addAll(getDialectSystemSchemaManager(COMMON).getAllInputStreams(schema));
        return result;
    }
    
    /**
     * Get all input streams with optional live system schema support.
     *
     * @param databaseType database type
     * @param schema schema
     * @param systemSchemaMetadataEnabled system schema metadata enabled
     * @return input streams
     */
    public static Collection<InputStream> getAllInputStreams(final String databaseType, final String schema, final boolean systemSchemaMetadataEnabled) {
        if (!systemSchemaMetadataEnabled) {
            return getAllInputStreams(databaseType, schema);
        }
        if (isMySQLDatabaseType(databaseType)) {
            Optional<Collection<InputStream>> liveSchema = loadMySQLSystemTableInputStreams(schema);
            if (liveSchema.isPresent()) {
                return liveSchema.get();
            }
        }
        return getAllInputStreams(databaseType, schema);
    }
    
    private static boolean isMySQLSystemTableWithNullSchema(final String tableName) {
        DatabaseType databaseType = TypedSPILoader.getService(DatabaseType.class, MYSQL_DATABASE_TYPE);
        SystemDatabase systemDatabase = new SystemDatabase(databaseType);
        for (String schemaName : systemDatabase.getSystemSchemas()) {
            if (loadMySQLSystemTableNames(schemaName).contains(tableName)) {
                return true;
            }
        }
        return false;
    }
    
    private static boolean isMySQLDatabaseType(final String databaseType) {
        return MYSQL_DATABASE_TYPE.equalsIgnoreCase(databaseType);
    }
    
    private static DialectSystemSchemaManager getDialectSystemSchemaManager(final String databaseType) {
        return DATABASE_TYPE_AND_SYSTEM_SCHEMA_MANAGER_MAP.getOrDefault(databaseType, new DialectSystemSchemaManager());
    }
    
    private static Collection<String> loadMySQLSystemTableNames(final String schemaName) {
        if (null == schemaName) {
            return Collections.emptyList();
        }
        Map<String, javax.sql.DataSource> cachedDataSources = GlobalDataSourceRegistry.getInstance().getCachedDataSources();
        if (cachedDataSources.isEmpty()) {
            return Collections.emptyList();
        }
        javax.sql.DataSource dataSource = cachedDataSources.values().iterator().next();
        return loadMySQLSystemTableNames(schemaName, dataSource).orElseGet(Collections::emptyList);
    }
    
    private static Optional<Collection<InputStream>> loadMySQLSystemTableInputStreams(final String schemaName) {
        if (null == schemaName) {
            return Optional.empty();
        }
        Map<String, javax.sql.DataSource> cachedDataSources = GlobalDataSourceRegistry.getInstance().getCachedDataSources();
        if (cachedDataSources.isEmpty()) {
            return Optional.empty();
        }
        javax.sql.DataSource dataSource = cachedDataSources.values().iterator().next();
        Optional<Collection<String>> tableNames = loadMySQLSystemTableNames(schemaName, dataSource);
        if (!tableNames.isPresent()) {
            return Optional.empty();
        }
        Collection<InputStream> result = new LinkedList<>();
        for (String tableName : tableNames.get()) {
            String tableType = loadMySQLTableType(schemaName, tableName, dataSource);
            result.add(new ByteArrayInputStream(buildMySQLTableYaml(tableName, tableType).getBytes(StandardCharsets.UTF_8)));
        }
        return Optional.of(result);
    }
    
    private static Optional<Collection<String>> loadMySQLSystemTableNames(final String schemaName, final javax.sql.DataSource dataSource) {
        Collection<String> result = new CaseInsensitiveSet<>();
        try (
                java.sql.Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(MYSQL_SYSTEM_TABLE_SQL)) {
            preparedStatement.setString(1, schemaName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(resultSet.getString("TABLE_NAME"));
                }
            }
        } catch (final SQLException ex) {
            log.warn("Load MySQL system tables failed for schema {}", schemaName, ex);
            return Optional.empty();
        }
        return Optional.of(result);
    }
    
    private static String loadMySQLTableType(final String schemaName, final String tableName, final javax.sql.DataSource dataSource) {
        try (
                java.sql.Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(MYSQL_TABLE_TYPE_SQL)) {
            preparedStatement.setString(1, schemaName);
            preparedStatement.setString(2, tableName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return MYSQL_TABLE_TYPE_VIEW.equalsIgnoreCase(resultSet.getString("TABLE_TYPE")) ? "VIEW" : "TABLE";
                }
            }
        } catch (final SQLException ex) {
            log.warn("Load MySQL system table type failed for {}.{}", schemaName, tableName, ex);
        }
        return "TABLE";
    }
    
    private static String buildMySQLTableYaml(final String tableName, final String tableType) {
        return String.format(MYSQL_YAML_TABLE_TEMPLATE, tableName, System.lineSeparator(), System.lineSeparator(), tableType, System.lineSeparator(), System.lineSeparator());
    }
}
