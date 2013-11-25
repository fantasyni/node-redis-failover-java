package com.netease.redisfailover.client;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import com.fasterxml.jackson.databind.JsonNode;

public interface ClientOperations {
	public void createZKClient();

	public JsonNode getZKData();

	public JedisPool createRedisClient(RedisBean redisClient);

	public void addClient(String name, String password);

	public Jedis getClient(String role);

	public Jedis masterClient();

	public JsonNode getRedisState();

	public void setJedisPoolConfig(JedisPoolConfig config);

	public void setJedisTimeout(int timeout);

	public void createClient();

}
