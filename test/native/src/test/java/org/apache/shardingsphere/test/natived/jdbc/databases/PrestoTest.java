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

package org.apache.shardingsphere.test.natived.jdbc.databases;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.shardingsphere.test.natived.jdbc.commons.TestShardingService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledInNativeImage;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Unable to use `org.testcontainers:presto:1.20.1` under GraalVM Native Image.
 * Background comes from <a href="https://github.com/testcontainers/testcontainers-java/issues/8657">testcontainers/testcontainers-java#8657</a>.
 */
@SuppressWarnings("resource")
@EnabledInNativeImage
@Testcontainers
public class PrestoTest {
    
    private static final String SYSTEM_PROP_KEY_PREFIX = "fixture.test-native.yaml.database.presto.";
    
    private String baseJdbcUrl;
    
    private TestShardingService testShardingService;
    
    @Container
    public static final GenericContainer<?> CONTAINER = new GenericContainer<>(DockerImageName.parse("prestodb/presto:0.289"))
            .withExposedPorts(8080)
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("/test-native/properties/presto-catalog-memory.properties"),
                    "/opt/presto-server/etc/catalog/memory.properties")
            .waitingFor(Wait.forLogMessage(".*======== SERVER STARTED ========.*", 1))
            .withStartupTimeout(Duration.ofSeconds(120L));
    
    @BeforeAll
    static void beforeAll() {
        assertThat(System.getProperty(SYSTEM_PROP_KEY_PREFIX + "ds0.jdbc-url"), is(nullValue()));
        assertThat(System.getProperty(SYSTEM_PROP_KEY_PREFIX + "ds1.jdbc-url"), is(nullValue()));
        assertThat(System.getProperty(SYSTEM_PROP_KEY_PREFIX + "ds2.jdbc-url"), is(nullValue()));
    }
    
    @AfterAll
    static void afterAll() {
        System.clearProperty(SYSTEM_PROP_KEY_PREFIX + "ds0.jdbc-url");
        System.clearProperty(SYSTEM_PROP_KEY_PREFIX + "ds1.jdbc-url");
        System.clearProperty(SYSTEM_PROP_KEY_PREFIX + "ds2.jdbc-url");
    }
    
    /**
     * TODO The use of {@link TimeUnit#sleep(long)} is indeed humorous, as this comes directly from the processing on the presto side.
     *  See {@code com.facebook.presto.nativeworker.ContainerQueryRunner} in <a href="https://github.com/prestodb/presto/pull/23094">prestodb/presto#23094</a>.
     *  Waiting for <a href="https://github.com/prestodb/presto/issues/23226">prestodb/presto#23226</a> to be resolved.
     *
     * @throws SQLException         An exception that provides information on a database access error or other errors.
     * @throws InterruptedException Thrown when a thread is waiting, sleeping, or otherwise occupied, and the thread is interrupted, either before or during the activity.
     */
    @Test
    void assertShardingInLocalTransactions() throws SQLException, InterruptedException {
        TimeUnit.SECONDS.sleep(5);
        baseJdbcUrl = "jdbc:presto://localhost:" + CONTAINER.getMappedPort(8080) + "/memory";
        DataSource dataSource = createDataSource();
        testShardingService = new TestShardingService(dataSource);
        initEnvironment();
        testShardingService.processSuccessInPresto();
        testShardingService.cleanEnvironment();
    }
    
    /**
     * Presto Memory Connector does not support truncating tables.
     *
     * @throws SQLException SQL exception
     */
    private void initEnvironment() throws SQLException {
        testShardingService.getOrderRepository().createTableIfNotExistsInPresto();
        testShardingService.getOrderItemRepository().createTableIfNotExistsInPresto();
        testShardingService.getAddressRepository().createTableIfNotExistsInPresto();
    }
    
    @SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
    private DataSource createDataSource() throws SQLException {
        try (
                Connection connection = DriverManager.getConnection(baseJdbcUrl, "test", null);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE SCHEMA memory.demo_ds_0");
            statement.executeUpdate("CREATE SCHEMA memory.demo_ds_1");
            statement.executeUpdate("CREATE SCHEMA memory.demo_ds_2");
        }
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.apache.shardingsphere.driver.ShardingSphereDriver");
        config.setJdbcUrl("jdbc:shardingsphere:classpath:test-native/yaml/databases/presto.yaml?placeholder-type=system_props");
        System.setProperty(SYSTEM_PROP_KEY_PREFIX + "ds0.jdbc-url", baseJdbcUrl + "/demo_ds_0");
        System.setProperty(SYSTEM_PROP_KEY_PREFIX + "ds1.jdbc-url", baseJdbcUrl + "/demo_ds_1");
        System.setProperty(SYSTEM_PROP_KEY_PREFIX + "ds2.jdbc-url", baseJdbcUrl + "/demo_ds_2");
        return new HikariDataSource(config);
    }
}
