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

        String tableName = sql.substring(1);
        String resultMapId = null;
        if(tableName.indexOf("(") != -1){
            resultMapId = tableName.substring(tableName.indexOf("(")+1,tableName.indexOf(")"));
            tableName = tableName.substring(0,tableName.indexOf("("));
        }
        resultMapId = ms.getId().substring(0,ms.getId().lastIndexOf(".")+1)+resultMapId;

        ResultMap resultMap = ms.getConfiguration().getResultMap(resultMapId);

        if(resultMap == null){
            throw new RuntimeException("Can not find ResultMap by id: "+resultMapId);
        }

        Set<String> properties = null;
        if(parameter instanceof Map){
            properties = ((Map)parameter).keySet();
        }else{
            properties = new HashSet<String>();
            for(ResultMapping rm : resultMap.getResultMappings()){
                properties.add(rm.getProperty());
            }
        }

        if(ms.getSqlCommandType().equals(SqlCommandType.INSERT)) {
            sql = insert(tableName,resultMap,properties);
        }else if(ms.getSqlCommandType().equals(SqlCommandType.UPDATE)){
            sql = update(tableName, resultMap, properties);
        }if(ms.getSqlCommandType().equals(SqlCommandType.DELETE)){
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


    private String insert(String tableName,ResultMap resultMap,Set<String> properties){
        StringBuffer insertSql = new StringBuffer("INSERT INTO ").append(tableName).append(" (");
        StringBuffer columnSql = new StringBuffer();
        StringBuffer propertySql = new StringBuffer();
        for(ResultMapping rm : resultMap.getResultMappings()){
            for(String property : properties){
                if(rm.getProperty().toUpperCase(Locale.US).equals(property.toUpperCase(Locale.US))){
                    addColumn(columnSql,rm);
                    columnSql.append(",");

                    addProperty(propertySql,rm);
                    propertySql.append(",");
                }
            }
        }
        if(!properties.isEmpty()){
            columnSql.deleteCharAt(columnSql.length()-1);
            propertySql.deleteCharAt(propertySql.length()-1);
        }
        insertSql.append(columnSql).append(") VALUES (").append(propertySql).append(")");
        return insertSql.toString();
    }

    private String update(String tableName,ResultMap resultMap,Set<String> properties){
        StringBuffer updateSql = new StringBuffer("UPDATE ").append(tableName).append(" SET ");
        StringBuffer idSql = new StringBuffer(" WHERE ");
        for(ResultMapping rm : resultMap.getResultMappings()){
            for(String property : properties){
                if(rm.getProperty().toUpperCase(Locale.US).equals(property.toUpperCase(Locale.US))){
                    if(!rm.getFlags().isEmpty()){
                        boolean isId = false;
                        for(ResultFlag flag : rm.getFlags()){
                            if(flag.equals(ResultFlag.ID)){
                                isId = true;
                            }
                        }
                        if(isId){
                            addColumn(idSql,rm);
                            idSql.append("=");
                            addProperty(idSql,rm);
                            continue;
                        }
                    }
                    addColumn(updateSql, rm);
                    updateSql.append("=");
                    addProperty(updateSql,rm);
                    updateSql.append(",");
                }

            }
        }
        if(!resultMap.getResultMappings().isEmpty()){
            updateSql.deleteCharAt(updateSql.length()-1);
        }
        updateSql.append(idSql);
        return updateSql.toString();
    }

    private String delete(String tableName, ResultMap resultMap){
        StringBuffer deleteSql = new StringBuffer("DELETE FROM ").append(tableName).append(" WHERE ");
        for(ResultMapping rm : resultMap.getResultMappings()){
            if(!rm.getFlags().isEmpty()){
                for(ResultFlag flag : rm.getFlags()){
                    if(flag.equals(ResultFlag.ID)){
                        addColumn(deleteSql,rm);
                        deleteSql.append("=");
                        addProperty(deleteSql,rm);
                        deleteSql.append(" and ");
                    }
                }
            }
        }
        if(!resultMap.getResultMappings().isEmpty()){
            deleteSql.delete(deleteSql.length()-5 ,deleteSql.length()-1);
        }
        return deleteSql.toString();
    }





    private MappedStatement copyFromNewSql(MappedStatement ms, BoundSql boundSql,
                                           String sql){
        BoundSql newBoundSql = copyFromBoundSql(ms, boundSql, sql);
        return copyFromMappedStatement(ms, new BoundSqlSqlSource(newBoundSql));
    }

    private BoundSql copyFromBoundSql(MappedStatement ms, BoundSql boundSql,
                                      String sql) {
        BoundSql newBoundSql = new BoundSql(ms.getConfiguration(),sql, boundSql.getParameterMappings(), boundSql.getParameterObject());
        for (ParameterMapping mapping : boundSql.getParameterMappings()) {
            String prop = mapping.getProperty();
            if (boundSql.hasAdditionalParameter(prop)) {
                newBoundSql.setAdditionalParameter(prop, boundSql.getAdditionalParameter(prop));
            }
        }
        return newBoundSql;
    }

    //see: MapperBuilderAssistant
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
