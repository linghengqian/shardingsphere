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

package org.apache.shardingsphere.distsql.handler.updatable.variable;

import org.apache.shardingsphere.distsql.statement.type.ral.updatable.SetDistVariableStatement;
import org.apache.shardingsphere.infra.exception.kernel.syntax.InvalidVariableValueException;
import org.apache.shardingsphere.infra.exception.kernel.syntax.UnsupportedVariableException;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;
import org.apache.shardingsphere.infra.metadata.database.resource.ResourceMetaData;
import org.apache.shardingsphere.infra.metadata.database.rule.RuleMetaData;
import org.apache.shardingsphere.infra.metadata.statistics.ShardingSphereStatistics;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.mode.metadata.MetaDataContexts;
import org.apache.shardingsphere.mode.spi.repository.PersistRepository;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SetDistVariableExecutorTest {
    
    private final SetDistVariableExecutor executor = new SetDistVariableExecutor();
    
    @Test
    void assertSetVariable() {
        Properties props = new Properties();
        ContextManager contextManager = mockContextManager(props);
        executor.executeUpdate(new SetDistVariableStatement("SQL_SHOW", "true"), contextManager);
        assertThat(props.getProperty("sql-show"), is("true"));
    }
    
    @Test
    void assertSetTemporaryVariable() {
        Properties props = new Properties();
        ContextManager contextManager = mockContextManager(props);
        executor.executeUpdate(new SetDistVariableStatement("SQL_FEDERATION_ENABLED", "true"), contextManager);
        assertThat(props.getProperty("sql-federation-enabled"), is("true"));
    }
    
    @Test
    void assertSetInvalidVariable() {
        ContextManager contextManager = mockContextManager(new Properties());
        assertThrows(UnsupportedVariableException.class, () -> executor.executeUpdate(new SetDistVariableStatement("not_exist", "true"), contextManager));
    }
    
    @Test
    void assertSetInvalidValue() {
        ContextManager contextManager = mockContextManager(new Properties());
        assertThrows(InvalidVariableValueException.class, () -> executor.executeUpdate(new SetDistVariableStatement("SQL_FEDERATION_ENABLED", "invalid"), contextManager));
    }
    
    private ContextManager mockContextManager(final Properties props) {
        ShardingSphereMetaData metaData = new ShardingSphereMetaData(Collections.singletonList(mock(ShardingSphereDatabase.class)), new ResourceMetaData(Collections.emptyMap()),
                new RuleMetaData(Collections.emptyList()), new org.apache.shardingsphere.infra.config.props.ConfigurationProperties(new Properties()));
        MetaDataContexts metaDataContexts = new MetaDataContexts(metaData, new ShardingSphereStatistics());
        ContextManager contextManager = new ContextManager(metaDataContexts, mock(), mock(), mock(PersistRepository.class));
        when(contextManager.getPersistServiceFacade().getModeFacade().getMetaDataManagerService().loadProperties()).thenReturn(props);
        return contextManager;
    }
}
