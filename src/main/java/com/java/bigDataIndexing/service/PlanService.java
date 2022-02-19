package com.java.bigDataIndexing.service;

import org.json.JSONObject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class PlanService {

    private JedisPool pool;
    private JedisPool getPool() {
        if (this.pool == null) {
            this.pool = new JedisPool();
        }
        return this.pool;
    }

    public boolean checkIfKeyExists(String key) {
        Jedis jedis = this.getPool().getResource();
        String jsonString = jedis.get(key);
        System.out.println(key + "check if key exist" + jsonString);
        jedis.close();
        if (jsonString == null || jsonString.isEmpty()) {
            return false;
        } else {
            return true;
        }
    }
    public String savePlan(JSONObject json) {
        // Save the Object in Redis
        String objectKey = (String) json.get("objectId");
        Jedis jedis = this.getPool().getResource();
        jedis.set(objectKey, json.toString());
        System.out.println(jedis.get(objectKey) + "jedis..");
        jedis.close();
        return objectKey;
    }

    public JSONObject getPlan(String key) {
        Jedis jedis = this.getPool().getResource();

        String jsonString = jedis.get(key);
        jedis.close();

        if (jsonString == null || jsonString.isEmpty()) {
            return null;
        }

        JSONObject jsonObject = new JSONObject(jsonString);

        return  jsonObject;
    }

    public void deletePlan(String key) {
        Jedis jedis = this.getPool().getResource();
        jedis.del(key);
        jedis.close();
    }
}
