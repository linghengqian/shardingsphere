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

package org.apache.shardingsphere.infra.url.scratch;

import org.apache.shardingsphere.infra.config.mode.ModeConfiguration;
import org.apache.shardingsphere.infra.spi.type.typed.TypedSPILoader;
import org.apache.shardingsphere.infra.url.spi.ShardingSphereModeConfigurationURLLoader;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class ScratchModeConfigurationURLLoaderTest {
    
    private final ShardingSphereModeConfigurationURLLoader urlLoader = TypedSPILoader.getService(ShardingSphereModeConfigurationURLLoader.class, "scratch:");
    
    @Test
    void assertLoad() {
        Properties queryProps = new Properties();
        ModeConfiguration actual = urlLoader.load("logic_db", queryProps);
        assertThat(actual.getType(), is("Standalone"));
        assertThat(actual.getRepository(), is(nullValue()));
        assertThat(queryProps.getProperty("databaseName"), is("logic_db"));
    }
    
    @Test
    void assertGetType() {
        assertThat(urlLoader.getType(), is("scratch:"));
    }
}
