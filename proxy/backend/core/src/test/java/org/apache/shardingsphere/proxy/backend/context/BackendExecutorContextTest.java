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

package org.apache.shardingsphere.proxy.backend.context;

import org.apache.shardingsphere.infra.config.props.ConfigurationPropertyKey;
import org.apache.shardingsphere.infra.config.props.ConfigurationProperties;
import org.apache.shardingsphere.infra.executor.kernel.ExecutorEngine;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.infra.metadata.database.resource.ResourceMetaData;
import org.apache.shardingsphere.infra.metadata.database.rule.RuleMetaData;
import org.apache.shardingsphere.infra.metadata.statistics.ShardingSphereStatistics;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.mode.metadata.MetaDataContexts;
import org.apache.shardingsphere.test.infra.framework.extension.mock.AutoMockExtension;
import org.apache.shardingsphere.test.infra.framework.extension.mock.StaticMockSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.internal.configuration.plugins.Plugins;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Properties;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(AutoMockExtension.class)
@StaticMockSettings(ProxyContext.class)
class BackendExecutorContextTest {
    
    @AfterEach
    void tearDown() {
        BackendExecutorContext.getInstance().close();
    }
    
    @Test
    void assertGetExecutorEngine() {
        ContextManager contextManager = mockContextManager();
        when(ProxyContext.getInstance().getContextManager()).thenReturn(contextManager);
        ExecutorEngine actual = BackendExecutorContext.getInstance().getExecutorEngine();
        assertThat(actual, is(BackendExecutorContext.getInstance().getExecutorEngine()));
    }
    
    @Test
    void assertInit() {
        ContextManager contextManager = mockContextManager();
        when(ProxyContext.getInstance().getContextManager()).thenReturn(contextManager);
        BackendExecutorContext.getInstance().init();
        ExecutorEngine actual = BackendExecutorContext.getInstance().getExecutorEngine();
        BackendExecutorContext.getInstance().init();
        assertThat(BackendExecutorContext.getInstance().getExecutorEngine(), is(not(actual)));
    }
    
    @Test
    void assertClose() throws ReflectiveOperationException {
        ContextManager contextManager = mockContextManager();
        when(ProxyContext.getInstance().getContextManager()).thenReturn(contextManager);
        BackendExecutorContext.getInstance().init();
        BackendExecutorContext.getInstance().close();
        assertThat(getExecutorEngine(), is((ExecutorEngine) null));
    }
    
    private ContextManager mockContextManager() {
        Properties props = new Properties();
        props.setProperty(ConfigurationPropertyKey.KERNEL_EXECUTOR_SIZE.getKey(), "0");
        ShardingSphereMetaData metaData = new ShardingSphereMetaData(Collections.emptyList(), new ResourceMetaData(Collections.emptyMap()),
                new RuleMetaData(Collections.emptyList()), new ConfigurationProperties(props));
        MetaDataContexts metaDataContexts = new MetaDataContexts(metaData, new ShardingSphereStatistics());
        ContextManager result = mock(ContextManager.class, RETURNS_DEEP_STUBS);
        when(result.getMetaDataContexts()).thenReturn(metaDataContexts);
        return result;
    }
    
    private ExecutorEngine getExecutorEngine() throws ReflectiveOperationException {
        Field executorEngineField = BackendExecutorContext.class.getDeclaredField("executorEngine");
        return (ExecutorEngine) Plugins.getMemberAccessor().get(executorEngineField, BackendExecutorContext.getInstance());
    }
}
