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

package org.apache.shardingsphere.mode.node.path.type.config.database.root;

import lombok.Getter;
import org.apache.shardingsphere.mode.node.path.engine.searcher.NodePathPattern;

import java.util.regex.Pattern;

/**
 * Database rule root node path.
 */
public final class DatabaseRuleRootNodePath {
    
    private static final String ROOT_NODE = "/metadata";
    
    private static final String RULE_NODE = "rules";
    
    @Getter
    private final String ruleType;
    
    @Getter
    private final String nodePrefix;
    
    private final Pattern pathPattern;
    
    public DatabaseRuleRootNodePath(final String ruleType) {
        this.ruleType = ruleType;
        nodePrefix = String.join("/", ROOT_NODE, NodePathPattern.IDENTIFIER, RULE_NODE, ruleType, "");
        pathPattern = Pattern.compile(nodePrefix + ".*");
    }
    
    /**
     * Judge whether is validated rule path.
     *
     * @param path path to be judged
     * @return is validated rule path or not
     */
    public boolean isValidatedPath(final String path) {
        return pathPattern.matcher(path).find();
    }
}
