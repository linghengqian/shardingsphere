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

package org.apache.shardingsphere.test.natived.jdbc.databases.hive;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.curator.test.InstanceSpec;
import org.apache.shardingsphere.test.natived.commons.TestShardingService;
import org.apache.shardingsphere.test.natived.commons.util.ResourceUtil;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledInNativeImage;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@SuppressWarnings({"resource", "deprecation", "SqlNoDataSourceInspection"})
@EnabledInNativeImage
@Testcontainers
class StandaloneMetastoreTest {
    
    private final int randomPort = InstanceSpec.getRandomPort();
    
    @AutoClose
    private final Network network = Network.newNetwork();
    
    /**
     * TODO {@link org.apache.shardingsphere.infra.database.hive.metadata.data.loader.HiveMetaDataLoader} needs to
     *  actively connect to HMS, which leads to the use of {@link FixedHostPortGenericContainer} .
     *  This is not a reasonable behavior and requires further changes.
     */
    @Container
    @AutoClose
    private final GenericContainer<?> hmsContainer = new FixedHostPortGenericContainer<>("apache/hive:4.0.1")
            .withEnv("SERVICE_NAME", "metastore")
            .withEnv("METASTORE_PORT", String.valueOf(randomPort))
            .withNetwork(network)
            .withNetworkAliases("metastore")
            .withFixedExposedPort(randomPort, randomPort);
    
    @Container
    @AutoClose
    private final GenericContainer<?> hs2Container = new GenericContainer<>("apache/hive:4.0.1")
            .withEnv("SERVICE_NAME", "hiveserver2")
            .withEnv("IS_RESUME", "true")
            .withEnv("SERVICE_OPTS", "-Dhive.metastore.uris=thrift://metastore:" + randomPort)
            .withNetwork(network)
            .withExposedPorts(10000)
            .dependsOn(hmsContainer)
            .withStartupTimeout(Duration.ofMinutes(2L));
    
    private final String systemPropKeyPrefix = "fixture.test-native.yaml.database.hive.hms.";
    
    private DataSource logicDataSource;
    
    private String jdbcUrlPrefix;
    
    private TestShardingService testShardingService;
    
    @BeforeEach
    void beforeEach() {
        assertThat(System.getProperty(systemPropKeyPrefix + "ds0.jdbc-url"), is(nullValue()));
        assertThat(System.getProperty(systemPropKeyPrefix + "ds1.jdbc-url"), is(nullValue()));
        assertThat(System.getProperty(systemPropKeyPrefix + "ds2.jdbc-url"), is(nullValue()));
    }
    
    @AfterEach
    void afterEach() throws SQLException {
        ResourceUtil.closeJdbcDataSource(logicDataSource);
        System.clearProperty(systemPropKeyPrefix + "ds0.jdbc-url");
        System.clearProperty(systemPropKeyPrefix + "ds1.jdbc-url");
        System.clearProperty(systemPropKeyPrefix + "ds2.jdbc-url");
    }
    
    @Test
    void assertShardingInLocalTransactions() throws SQLException {
        jdbcUrlPrefix = "jdbc:hive2://localhost:" + hs2Container.getMappedPort(10000) + "/";
        logicDataSource = createDataSource();
        testShardingService = new TestShardingService(logicDataSource);
        initEnvironment();
        testShardingService.processSuccessInHive();
        testShardingService.cleanEnvironment();
    }
    
    private void initEnvironment() throws SQLException {
        testShardingService.getOrderRepository().truncateTable();
        testShardingService.getOrderItemRepository().truncateTable();
        testShardingService.getAddressRepository().truncateTable();
    }
    
    private DataSource createDataSource() throws SQLException {
        Awaitility.await().atMost(Duration.ofMinutes(1L)).ignoreExceptions().until(() -> {
            DriverManager.getConnection(jdbcUrlPrefix).close();
            return true;
        });
        try (
                Connection connection = DriverManager.getConnection(jdbcUrlPrefix);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE demo_ds_0");
            statement.execute("CREATE DATABASE demo_ds_1");
            statement.execute("CREATE DATABASE demo_ds_2");
        }
        Stream.of("demo_ds_0", "demo_ds_1", "demo_ds_2").parallel().forEach(this::initTable);
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.apache.shardingsphere.driver.ShardingSphereDriver");
        config.setJdbcUrl("jdbc:shardingsphere:classpath:test-native/yaml/jdbc/databases/hive/standalone-hms.yaml?placeholder-type=system_props");
        System.setProperty(systemPropKeyPrefix + "ds0.jdbc-url", jdbcUrlPrefix + "demo_ds_0");
        System.setProperty(systemPropKeyPrefix + "ds1.jdbc-url", jdbcUrlPrefix + "demo_ds_1");
        System.setProperty(systemPropKeyPrefix + "ds2.jdbc-url", jdbcUrlPrefix + "demo_ds_2");
        return new HikariDataSource(config);
    }
    
    /**
     * TODO `shardingsphere-parser-sql-hive` module does not support `set`, `create table` statements yet,
     *  we always need to execute the following Hive Session-level SQL in the current {@link javax.sql.DataSource}.
     * Hive does not support `AUTO_INCREMENT`,
     * refer to <a href="https://issues.apache.org/jira/browse/HIVE-6905">HIVE-6905</a>.
     *
     * @param databaseName database name
     * @throws RuntimeException SQL exception
     */
    private void initTable(final String databaseName) {
        try (
                Connection connection = DriverManager.getConnection(jdbcUrlPrefix + databaseName);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS t_order (\n"
                    + "    order_id   BIGINT NOT NULL,\n"
                    + "    order_type INT,\n"
                    + "    user_id    INT    NOT NULL,\n"
                    + "    address_id BIGINT NOT NULL,\n"
                    + "    status     string,\n"
                    + "    PRIMARY KEY (order_id) disable novalidate\n"
                    + ") STORED BY ICEBERG STORED AS ORC TBLPROPERTIES ('format-version' = '2')");
            statement.execute("CREATE TABLE IF NOT EXISTS t_order_item (\n"
                    + "    order_item_id BIGINT NOT NULL,\n"
                    + "    order_id      BIGINT NOT NULL,\n"
                    + "    user_id       INT    NOT NULL,\n"
                    + "    phone         string,\n"
                    + "    status        string,\n"
                    + "    PRIMARY KEY (order_item_id) disable novalidate\n"
                    + ") STORED BY ICEBERG STORED AS ORC TBLPROPERTIES ('format-version' = '2')");
            statement.execute("CREATE TABLE IF NOT EXISTS t_address (\n"
                    + "    address_id   BIGINT       NOT NULL,\n"
                    + "    address_name string NOT NULL,\n"
                    + "    PRIMARY KEY (address_id) disable novalidate\n"
                    + ") STORED BY ICEBERG STORED AS ORC TBLPROPERTIES ('format-version' = '2')");
        } catch (final SQLException exception) {
            throw new RuntimeException(exception);
        }
    }
}
