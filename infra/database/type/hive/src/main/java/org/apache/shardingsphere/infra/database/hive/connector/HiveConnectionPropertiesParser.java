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

package org.apache.shardingsphere.infra.database.hive.connector;

import org.apache.shardingsphere.infra.database.core.connector.ConnectionProperties;
import org.apache.shardingsphere.infra.database.core.connector.ConnectionPropertiesParser;
import org.apache.shardingsphere.infra.database.core.connector.StandardConnectionProperties;
import org.apache.shardingsphere.infra.util.reflection.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Properties;

/**
 * Connection properties parser of Hive.
 */
public final class HiveConnectionPropertiesParser implements ConnectionPropertiesParser {
    
    @Override
    public ConnectionProperties parse(final String url, final String username, final String catalog) {
        String host, dbName;
        int port;
        Properties queryProperties = new Properties();
        try {
            Method parseURL = Class.forName("org.apache.hive.jdbc.Utils").getMethod("parseURL", String.class);
            Object jdbcConnectionParams = ReflectionUtils.invokeMethod(parseURL, null, url);
            Class<?> jdbcConnectionParamsClass = Class.forName("org.apache.hive.jdbc.Utils$JdbcConnectionParams");
            host = ReflectionUtils.invokeMethod(jdbcConnectionParamsClass.getMethod("getHost"), jdbcConnectionParams);
            port = ReflectionUtils.invokeMethod(jdbcConnectionParamsClass.getMethod("getPort"), jdbcConnectionParams);
            dbName = ReflectionUtils.invokeMethod(jdbcConnectionParamsClass.getMethod("getDbName"), jdbcConnectionParams);
            queryProperties.putAll(ReflectionUtils.invokeMethod(jdbcConnectionParamsClass.getMethod("getSessionVars"), jdbcConnectionParams));
            queryProperties.putAll(ReflectionUtils.invokeMethod(jdbcConnectionParamsClass.getMethod("getHiveConfs"), jdbcConnectionParams));
            queryProperties.putAll(ReflectionUtils.invokeMethod(jdbcConnectionParamsClass.getMethod("getHiveVars"), jdbcConnectionParams));
        } catch (final ClassNotFoundException | NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
        if (null == host && 0 == port) {
            throw new RuntimeException("HiveServer2 in embedded mode has been deprecated by Apache Hive, "
                    + "See https://issues.apache.org/jira/browse/HIVE-28418 . "
                    + "Users should start local HiveServer2 through Docker Image https://hub.docker.com/r/apache/hive .");
        }
        return new StandardConnectionProperties(host, port, dbName, null, queryProperties, new Properties());
    }
    
    @Override
    public String getDatabaseType() {
        return "Hive";
    }
}
