package com.github.miemiedev.mybatis.crud;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author miemiedev
 */
public class SimulateBaseDao {

    protected SqlSessionFactory sessionFactory;

    @Before
    public void init() throws IOException {
        String resource = "mybatis-config.xml";
        InputStream inputStream = Resources.getResourceAsStream(resource);
        sessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

    }

    @After
    public void close(){

    }
}
