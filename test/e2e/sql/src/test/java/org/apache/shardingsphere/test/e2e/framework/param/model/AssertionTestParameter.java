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

package org.apache.shardingsphere.test.e2e.framework.param.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.infra.database.core.type.DatabaseType;
import org.apache.shardingsphere.test.e2e.cases.casse.SQLE2ETestCaseContext;
import org.apache.shardingsphere.test.e2e.framework.type.SQLCommandType;
import org.apache.shardingsphere.test.e2e.framework.type.SQLExecuteType;
import org.apache.shardingsphere.test.e2e.cases.casse.assertion.SQLE2ETestCaseAssertion;

/**
 * Assertion test parameter.
 */
@RequiredArgsConstructor
@Getter
public final class AssertionTestParameter implements E2ETestParameter {
    
    private final SQLE2ETestCaseContext testCaseContext;
    
    private final SQLE2ETestCaseAssertion assertion;
    
    private final String adapter;
    
    private final String scenario;
    
    private final String mode;
    
    private final DatabaseType databaseType;
    
    private final SQLExecuteType sqlExecuteType;
    
    private final SQLCommandType sqlCommandType;
    
    @Override
    public String toString() {
        String sql = null == testCaseContext ? null : testCaseContext.getTestCase().getSql();
        String type = null == databaseType ? null : databaseType.getType();
        return String.format("%s: %s -> %s -> %s -> %s", adapter, scenario, type, sqlExecuteType, sql);
    }
}
