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
import org.apache.shardingsphere.infra.url.spi.ShardingSphereModeConfigurationURLLoader;

import java.util.Properties;

/**
 * Scratch mode configuration URL loader.
 * 
 * <p>This URL loader allows creating a ShardingSphere JDBC DataSource without a YAML file,
 * enabling DistSQL execution for configuration. The database name is passed as the configuration subject.</p>
 * 
 * <p>Example URL: {@code jdbc:shardingsphere:scratch:logic_db} where {@code logic_db} is the database name.</p>
 */
public final class ScratchModeConfigurationURLLoader implements ShardingSphereModeConfigurationURLLoader {
    
    @Override
    public ModeConfiguration load(final String databaseName, final Properties queryProps) {
        queryProps.setProperty("databaseName", databaseName);
        return new ModeConfiguration("Standalone", null);
    }
    
    @Override
    public String getType() {
        return "scratch:";
    }
}
