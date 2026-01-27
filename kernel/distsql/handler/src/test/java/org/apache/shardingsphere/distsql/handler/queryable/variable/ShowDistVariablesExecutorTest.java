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

package org.apache.shardingsphere.distsql.handler.queryable.variable;

import org.apache.shardingsphere.database.connector.core.type.DatabaseType;
import org.apache.shardingsphere.distsql.handler.engine.DistSQLConnectionContext;
import org.apache.shardingsphere.distsql.statement.type.ral.queryable.show.ShowDistVariablesStatement;
import org.apache.shardingsphere.infra.config.props.ConfigurationProperties;
import org.apache.shardingsphere.infra.config.props.temporary.TemporaryConfigurationProperties;
import org.apache.shardingsphere.infra.executor.sql.prepare.driver.DatabaseConnectionManager;
import org.apache.shardingsphere.infra.executor.sql.prepare.driver.ExecutorStatementManager;
import org.apache.shardingsphere.infra.merge.result.impl.local.LocalDataQueryResultRow;
import org.apache.shardingsphere.infra.session.query.QueryContext;
import org.apache.shardingsphere.infra.util.props.PropertiesBuilder;
import org.apache.shardingsphere.infra.util.props.PropertiesBuilder.Property;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShowDistVariablesExecutorTest {
    
    private final ContextManager contextManager = mock(ContextManager.class, RETURNS_DEEP_STUBS);
    
    @Test
    void assertShowTemporaryVariables() {
        Properties props = PropertiesBuilder.build(new Property("proxy-meta-data-collector-enabled", Boolean.TRUE.toString()), new Property("instance-connection-enabled", Boolean.TRUE.toString()));
        when(contextManager.getMetaDataContexts().getMetaData().getTemporaryProps()).thenReturn(new TemporaryConfigurationProperties(props));
        ShowDistVariablesExecutor executor = new ShowDistVariablesExecutor();
        Collection<LocalDataQueryResultRow> actual = executor.getRows(new ShowDistVariablesStatement(true, null), contextManager);
        assertThat(actual.size() >= 4, is(true));
        Iterator<LocalDataQueryResultRow> iterator = actual.iterator();
        LocalDataQueryResultRow firstRow = iterator.next();
        LocalDataQueryResultRow secondRow = iterator.next();
        LocalDataQueryResultRow thirdRow = iterator.next();
        LocalDataQueryResultRow fourthRow = iterator.next();
        assertThat(firstRow.getCell(1), is("instance_connection_enabled"));
        assertThat(secondRow.getCell(1), is("proxy_meta_data_collector_cron"));
        assertThat(thirdRow.getCell(1), is("proxy_meta_data_collector_enabled"));
        assertThat(fourthRow.getCell(1), is("system_schema_metadata_assembly_enabled"));
    }
    
    @Test
    void assertShowNormalVariables() {
        when(contextManager.getMetaDataContexts().getMetaData().getProps())
                .thenReturn(new ConfigurationProperties(PropertiesBuilder.build(new Property("sql-show", Boolean.TRUE.toString()), new Property("sql-simple", Boolean.TRUE.toString()),
                        new Property("agent-plugins-enabled", Boolean.TRUE.toString()))));
        ShowDistVariablesExecutor executor = new ShowDistVariablesExecutor();
        executor.setConnectionContext(new DistSQLConnectionContext(mock(QueryContext.class), 3,
                mock(DatabaseType.class), mock(DatabaseConnectionManager.class), mock(ExecutorStatementManager.class)));
        Collection<LocalDataQueryResultRow> actual = executor.getRows(new ShowDistVariablesStatement(false, null), contextManager);
        assertThat(actual.size(), is(4));
        assertThat(actual.stream().anyMatch(row -> "cached_connections".equals(row.getCell(1)) && Integer.valueOf(3).equals(row.getCell(2))), is(true));
        assertThat(actual.stream().anyMatch(row -> "agent_plugins_enabled".equals(row.getCell(1)) && "true".equals(row.getCell(2))), is(true));
        assertThat(actual.stream().anyMatch(row -> "sql_show".equals(row.getCell(1)) && "true".equals(row.getCell(2))), is(true));
        assertThat(actual.stream().anyMatch(row -> "sql_simple".equals(row.getCell(1)) && "true".equals(row.getCell(2))), is(true));
    }
    
    @Test
    void assertShowVariablesWithPattern() {
        when(contextManager.getMetaDataContexts().getMetaData().getProps())
                .thenReturn(new ConfigurationProperties(PropertiesBuilder.build(new Property("sql-show", Boolean.TRUE.toString()))));
        ShowDistVariablesExecutor executor = new ShowDistVariablesExecutor();
        executor.setConnectionContext(new DistSQLConnectionContext(mock(QueryContext.class), 0,
                mock(DatabaseType.class), mock(DatabaseConnectionManager.class), mock(ExecutorStatementManager.class)));
        Collection<LocalDataQueryResultRow> actual = executor.getRows(new ShowDistVariablesStatement(false, "sql_%"), contextManager);
        assertThat(actual.size() >= 2, is(true));
        assertThat(actual.stream().anyMatch(row -> "sql_show".equals(row.getCell(1))), is(true));
        assertThat(actual.stream().anyMatch(row -> "sql_simple".equals(row.getCell(1))), is(true));
    }
}
