package com.github.miemiedev.mybatis.crud;

import org.apache.ibatis.builder.xml.dynamic.DynamicSqlSource;
import org.apache.ibatis.builder.xml.dynamic.TextSqlNode;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.plugin.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Intercepts({@Signature(
        type= Executor.class,
        method = "update",
        args = {MappedStatement.class, Object.class})})
public class UpdateInterceptor implements Interceptor {
    Logger logger = LoggerFactory.getLogger(UpdateInterceptor.class);
    static int MAPPED_STATEMENT_INDEX = 0;
    static int PARAMETER_INDEX = 1;

    public Object intercept(Invocation invocation) throws Throwable {
        final Object[] queryArgs = invocation.getArgs();
        final MappedStatement ms = (MappedStatement)queryArgs[MAPPED_STATEMENT_INDEX];
        final Object parameter = queryArgs[PARAMETER_INDEX];

        final BoundSql boundSql = ms.getBoundSql(parameter);
        String sql = boundSql.getSql();
        if(!sql.startsWith("#")){
            return invocation.proceed();
        }
        sql = sql.substring(1);
        if(sql.indexOf("(") == -1 || sql.indexOf(")") == -1){
            throw new RuntimeException("Expression need set ResultMapId.");
        }
        String resultMapId = sql.substring(sql.indexOf("(")+1,sql.indexOf(")"));
        String tableName = sql.substring(0,sql.indexOf("("));

        if(resultMapId.indexOf(".") == -1){
            resultMapId = ms.getId().substring(0,ms.getId().lastIndexOf(".")+1)+resultMapId;
        }

        ResultMap resultMap = ms.getConfiguration().getResultMap(resultMapId);

        if(resultMap == null){
            throw new RuntimeException("Can not find ResultMap by id: "+resultMapId);
        }


        Set<ResultMapping> mappings = new HashSet<ResultMapping>();
        if(parameter instanceof Map){
            for(ResultMapping mapping : resultMap.getResultMappings()){
                for(String property : ((Map<String,Object>)parameter).keySet()){
                    if(mapping.getProperty().toUpperCase(Locale.US).equals(property.toUpperCase(Locale.US))){
                        mappings.add(mapping);
                    }
                }
            }
        }else{
            mappings.addAll(resultMap.getResultMappings());
        }

       Iterator<ResultMapping> mappingsIterator  = mappings.iterator();
        while (mappingsIterator.hasNext()) {
            ResultMapping mapping =  mappingsIterator.next();
            if(mapping.getNestedQueryId() != null || mapping.getNestedResultMapId() !=null){
                mappingsIterator.remove();
            }
        }

        if(ms.getSqlCommandType().equals(SqlCommandType.INSERT)) {
            sql = insert(tableName,mappings);
        }else if(ms.getSqlCommandType().equals(SqlCommandType.UPDATE)){
            sql = update(tableName, mappings);
        }else if(ms.getSqlCommandType().equals(SqlCommandType.DELETE)){
            sql = delete(tableName, resultMap);
        }

        logger.debug("Generate SQL expression => {}" ,sql);
        TextSqlNode sqlNode = new TextSqlNode(sql);
        DynamicSqlSource sqlSource = new DynamicSqlSource(ms.getConfiguration(),sqlNode);


        queryArgs[MAPPED_STATEMENT_INDEX] = copyFromMappedStatement(ms, sqlSource);
        return invocation.proceed();
    }

    public void addColumn(StringBuffer sql, ResultMapping resultMapping){
        sql.append(resultMapping.getColumn().toUpperCase(Locale.US));
    }

    public void addProperty(StringBuffer sql, ResultMapping resultMapping){
        sql.append("#{").append(resultMapping.getProperty()).append(",");
        if(resultMapping.getJdbcType()!= null){
            sql.append("jdbcType=").append(resultMapping.getJdbcType().name()).append(",");
        }
        if(resultMapping.getTypeHandler()!=null){
            sql.append("typeHandler=").append(resultMapping.getTypeHandler().getClass().getName()).append(",");
        }
        sql.deleteCharAt(sql.length()-1);
        sql.append("}");
    }


    private String insert(String tableName,Set<ResultMapping> mappings){
        StringBuffer insertSql = new StringBuffer("INSERT INTO ").append(tableName).append(" (");
        StringBuffer columnSql = new StringBuffer();
        StringBuffer propertySql = new StringBuffer();
        for(ResultMapping mapping : mappings){
            addColumn(columnSql,mapping);
            columnSql.append(",");

            addProperty(propertySql,mapping);
            propertySql.append(",");
        }
        if(!mappings.isEmpty()){
            columnSql.deleteCharAt(columnSql.length()-1);
            propertySql.deleteCharAt(propertySql.length()-1);
        }
        insertSql.append(columnSql).append(") VALUES (").append(propertySql).append(")");
        return insertSql.toString();
    }

    private String update(String tableName,Set<ResultMapping> mappings){
        StringBuffer updateSql = new StringBuffer("UPDATE ").append(tableName).append(" SET ");
        StringBuffer idSql = new StringBuffer(" WHERE ");
        for(ResultMapping mapping : mappings){
            if(!mapping.getFlags().isEmpty() && mapping.getFlags().contains(ResultFlag.ID)){
                addColumn(idSql,mapping);
                idSql.append("=");
                addProperty(idSql,mapping);
                continue;
            }
            addColumn(updateSql, mapping);
            updateSql.append("=");
            addProperty(updateSql,mapping);
            updateSql.append(",");
        }
        if(!mappings.isEmpty()){
            updateSql.deleteCharAt(updateSql.length()-1);
        }
        updateSql.append(idSql);
        return updateSql.toString();
    }

    private String delete(String tableName, ResultMap resultMap){
        StringBuffer deleteSql = new StringBuffer("DELETE FROM ").append(tableName).append(" WHERE ");
        for(ResultMapping mapping : resultMap.getResultMappings()){
            if(!mapping.getFlags().isEmpty() && mapping.getFlags().contains(ResultFlag.ID)){
                addColumn(deleteSql,mapping);
                deleteSql.append("=");
                addProperty(deleteSql,mapping);
                continue;
            }
        }
        return deleteSql.toString();
    }


    private MappedStatement copyFromMappedStatement(MappedStatement ms,SqlSource newSqlSource) {
        MappedStatement.Builder builder = new MappedStatement.Builder(ms.getConfiguration(),ms.getId(),newSqlSource,ms.getSqlCommandType());

        builder.resource(ms.getResource());
        builder.fetchSize(ms.getFetchSize());
        builder.statementType(ms.getStatementType());
        builder.keyGenerator(ms.getKeyGenerator());
        if(ms.getKeyProperties() != null && ms.getKeyProperties().length !=0){
            StringBuffer keyProperties = new StringBuffer();
            for(String keyProperty : ms.getKeyProperties()){
                keyProperties.append(keyProperty).append(",");
            }
            keyProperties.delete(keyProperties.length()-1, keyProperties.length());
            builder.keyProperty(keyProperties.toString());
        }

        //setStatementTimeout()
        builder.timeout(ms.getTimeout());

        //setStatementResultMap()
        builder.parameterMap(ms.getParameterMap());

        //setStatementResultMap()
        builder.resultMaps(ms.getResultMaps());
        builder.resultSetType(ms.getResultSetType());

        //setStatementCache()
        builder.cache(ms.getCache());
        builder.flushCacheRequired(ms.isFlushCacheRequired());
        builder.useCache(ms.isUseCache());

        return builder.build();
    }
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
    public void setProperties(Properties properties) {
    }

    public static class BoundSqlSqlSource implements SqlSource {
        BoundSql boundSql;
        public BoundSqlSqlSource(BoundSql boundSql) {
            this.boundSql = boundSql;
        }
        public BoundSql getBoundSql(Object parameterObject) {
            return boundSql;
        }
    }
}
