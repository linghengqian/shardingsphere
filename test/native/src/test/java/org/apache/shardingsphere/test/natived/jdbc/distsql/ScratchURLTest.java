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

package org.apache.shardingsphere.test.natived.jdbc.distsql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScratchURLTest {
    
    @Test
    void assertScratchURLCreatesDataSource() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.apache.shardingsphere.driver.ShardingSphereDriver");
        config.setJdbcUrl("jdbc:shardingsphere:scratch:scratch_test_db");
        try (
                HikariDataSource dataSource = new HikariDataSource(config);
                Connection connection = dataSource.getConnection()) {
            assertNotNull(connection);
        }
    }
    
    @Test
    void assertDistSQLRegisterStorageUnit() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.apache.shardingsphere.driver.ShardingSphereDriver");
        config.setJdbcUrl("jdbc:shardingsphere:scratch:scratch_distsql_db");
        try (
                HikariDataSource dataSource = new HikariDataSource(config);
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("REGISTER STORAGE UNIT ds_0 (URL='jdbc:h2:mem:scratch_test_ds_0;MODE=MYSQL;IGNORECASE=TRUE', USER='sa', PASSWORD='')");
            try (ResultSet resultSet = statement.executeQuery("SHOW STORAGE UNITS")) {
                assertNotNull(resultSet);
                assertTrue(resultSet.next());
            }
        }
    }
    
    @Test
    void assertExecuteDDLAfterDistSQL() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.apache.shardingsphere.driver.ShardingSphereDriver");
        config.setJdbcUrl("jdbc:shardingsphere:scratch:scratch_ddl_db");
        try (
                HikariDataSource dataSource = new HikariDataSource(config);
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("REGISTER STORAGE UNIT ds_0 ("
                    + "URL='jdbc:h2:mem:scratch_ddl_ds_0;MODE=MYSQL;IGNORECASE=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE', "
                    + "USER='sa', PASSWORD='')");
            try (ResultSet resultSet = statement.executeQuery("SHOW STORAGE UNITS")) {
                assertNotNull(resultSet);
                assertTrue(resultSet.next());
            }
        }
    }
}
