package com.github.miemiedev.mybatis.crud;

import org.apache.ibatis.session.SqlSession;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */

public class CrudTester extends SimulateBaseDao{
    @Test
    public void insert(){
        SqlSession session = sessionFactory.openSession();

        User user = new User();
        user.setCity("BeiJing");
        user.setGender(true);
        user.setName("newbie");
        user.setScore(96D);
        user.setAge(35);

        int count = session.insert("db.table.user.addOld", user);
        assertEquals(count,1);
        System.out.println(user);
        session.commit();
        session.close();
    }

    @Test
    public void crud(){
        SqlSession session = sessionFactory.openSession();

        User user = new User();
        user.setCity("BeiJing");
        user.setGender(true);
        user.setName("newbie");
        user.setScore(96D);
        user.setAge(35);

        int count = session.insert("db.table.user.add", user);
        assertEquals(count,1);

        user.setName("Test");
        count = session.update("db.table.user.update",user);
        assertEquals(count,1);

        count = session.delete("db.table.user.delete",user.getId());
        assertEquals(count,1);
        System.out.println(user);
        session.commit();
        session.close();
    }

    @Test
    public void insertMap(){
        SqlSession session = sessionFactory.openSession();

        Map<String,Object> user = new HashMap<String,Object>();
        user.put("id", null);
        user.put("name", "MapTest");
        user.put("gender", true);

        int count = session.insert("db.table.user.add", user);
        assertEquals(count,1);
        System.out.println(user);
        session.commit();
        session.close();
    }

    @Test
    public void updateMap(){
        SqlSession session = sessionFactory.openSession();

        Map<String,Object> user = new HashMap<String,Object>();
        user.put("id", 189L);
        user.put("name", "Test");

        int count = session.update("db.table.user.update", user);
        assertEquals(count,1);
        System.out.println(user);
        session.commit();
        session.close();
    }
}
