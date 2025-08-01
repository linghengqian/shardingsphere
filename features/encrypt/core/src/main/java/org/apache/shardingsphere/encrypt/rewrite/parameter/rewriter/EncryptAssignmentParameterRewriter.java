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

package org.apache.shardingsphere.encrypt.rewrite.parameter.rewriter;

import com.google.common.base.Preconditions;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.encrypt.rule.EncryptRule;
import org.apache.shardingsphere.encrypt.rule.column.EncryptColumn;
import org.apache.shardingsphere.infra.binder.context.statement.SQLStatementContext;
import org.apache.shardingsphere.infra.binder.context.statement.type.dml.InsertStatementContext;
import org.apache.shardingsphere.infra.binder.context.statement.type.dml.UpdateStatementContext;
import org.apache.shardingsphere.infra.database.core.type.DatabaseTypeRegistry;
import org.apache.shardingsphere.infra.rewrite.parameter.builder.ParameterBuilder;
import org.apache.shardingsphere.infra.rewrite.parameter.builder.impl.GroupedParameterBuilder;
import org.apache.shardingsphere.infra.rewrite.parameter.builder.impl.StandardParameterBuilder;
import org.apache.shardingsphere.infra.rewrite.parameter.rewriter.ParameterRewriter;
import org.apache.shardingsphere.sql.parser.statement.core.segment.dml.assignment.ColumnAssignmentSegment;
import org.apache.shardingsphere.sql.parser.statement.core.segment.dml.assignment.SetAssignmentSegment;
import org.apache.shardingsphere.sql.parser.statement.core.segment.dml.expr.simple.ParameterMarkerExpressionSegment;
import org.apache.shardingsphere.sql.parser.statement.core.statement.SQLStatement;
import org.apache.shardingsphere.sql.parser.statement.core.statement.type.dml.InsertStatement;
import org.apache.shardingsphere.sql.parser.statement.core.statement.type.dml.UpdateStatement;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * Assignment parameter rewriter for encrypt.
 */
@RequiredArgsConstructor
public final class EncryptAssignmentParameterRewriter implements ParameterRewriter {
    
    private final EncryptRule rule;
    
    private final String databaseName;
    
    @Override
    public boolean isNeedRewrite(final SQLStatementContext sqlStatementContext) {
        if (sqlStatementContext instanceof UpdateStatementContext) {
            return true;
        }
        if (sqlStatementContext instanceof InsertStatementContext) {
            return (((InsertStatementContext) sqlStatementContext).getSqlStatement()).getSetAssignment().isPresent();
        }
        return false;
    }
    
    @Override
    public void rewrite(final ParameterBuilder paramBuilder, final SQLStatementContext sqlStatementContext, final List<Object> params) {
        String tableName = sqlStatementContext.getTablesContext().getSimpleTables().iterator().next().getTableName().getIdentifier().getValue();
        String schemaName = sqlStatementContext.getTablesContext().getSchemaName()
                .orElseGet(() -> new DatabaseTypeRegistry(sqlStatementContext.getSqlStatement().getDatabaseType()).getDefaultSchemaName(databaseName));
        for (ColumnAssignmentSegment each : getSetAssignmentSegment(sqlStatementContext.getSqlStatement()).getAssignments()) {
            String columnName = each.getColumns().get(0).getIdentifier().getValue();
            if (each.getValue() instanceof ParameterMarkerExpressionSegment && rule.findEncryptTable(tableName).map(optional -> optional.isEncryptColumn(columnName)).orElse(false)) {
                EncryptColumn encryptColumn = rule.getEncryptTable(tableName).getEncryptColumn(columnName);
                StandardParameterBuilder standardParamBuilder = paramBuilder instanceof StandardParameterBuilder
                        ? (StandardParameterBuilder) paramBuilder
                        : ((GroupedParameterBuilder) paramBuilder).getParameterBuilders().get(0);
                encryptParameters(standardParamBuilder, schemaName, tableName, encryptColumn, ((ParameterMarkerExpressionSegment) each.getValue()).getParameterMarkerIndex(), params);
            }
        }
    }
    
    private SetAssignmentSegment getSetAssignmentSegment(final SQLStatement sqlStatement) {
        if (sqlStatement instanceof InsertStatement) {
            Optional<SetAssignmentSegment> result = ((InsertStatement) sqlStatement).getSetAssignment();
            Preconditions.checkState(result.isPresent());
            return result.get();
        }
        return ((UpdateStatement) sqlStatement).getSetAssignment();
    }
    
    private void encryptParameters(final StandardParameterBuilder paramBuilder, final String schemaName,
                                   final String tableName, final EncryptColumn encryptColumn, final int parameterMarkerIndex, final List<Object> params) {
        String columnName = encryptColumn.getName();
        Object originalValue = params.get(parameterMarkerIndex);
        Object cipherValue = encryptColumn.getCipher().encrypt(databaseName, schemaName, tableName, columnName, Collections.singletonList(originalValue)).iterator().next();
        paramBuilder.addReplacedParameters(parameterMarkerIndex, cipherValue);
        Collection<Object> addedParams = new LinkedList<>();
        if (encryptColumn.getAssistedQuery().isPresent()) {
            addedParams.add(encryptColumn.getAssistedQuery().get().encrypt(databaseName, schemaName, tableName, columnName, Collections.singletonList(originalValue)).iterator().next());
        }
        if (encryptColumn.getLikeQuery().isPresent()) {
            addedParams.add(encryptColumn.getLikeQuery().get().encrypt(databaseName, schemaName, tableName, columnName, Collections.singletonList(originalValue)).iterator().next());
        }
        if (!addedParams.isEmpty()) {
            paramBuilder.addAddedParameters(parameterMarkerIndex, addedParams);
        }
    }
}
