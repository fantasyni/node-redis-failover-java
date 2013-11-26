#node-redis-failover-java

`node-redis-failover-java` is java client for [node-redis-failover](https://github.com/numbcoder/node-redis-failover). 

##Usage
[RedisClientTest.java](https://github.com/fantasyni/node-redis-failover-java/blob/master/src/test/java/RedisClientTest.java)
```
	InputStream ips = RedisClientTest.class.getResourceAsStream("config.properties");

	Properties props = new Properties();
	props.load(ips);

	RedisClient redis = new RedisClient(props);

	redis.on("ready", new DataListener() {

		public void receiveData(DataEvent event) {
			System.out.println("ready");
			System.out.println(redis.masterClient().ping());
			System.out.println(redis.getClient("slave").ping());
		}
	});

	redis.on("change", new DataListener() {

		public void receiveData(DataEvent event) {
			System.out.println("change");
			System.out.println(redis.getRedisState().toString());
			Jedis masterRedis = redis.masterClient();
			System.out.println(masterRedis.ping());
			Jedis slaveRedis = redis.getClient("slave");
			System.out.println(slaveRedis.ping());
		}
	});

	redis.on("masterChange", new DataListener() {

		public void receiveData(DataEvent event) {
			System.out.println("masterChange");
			System.out.println(redis.getRedisState().toString());
			Jedis masterRedis = redis.masterClient();
			System.out.println(masterRedis.ping());
			Jedis slaveRedis = redis.getClient("slave");
			System.out.println(slaveRedis.ping());
		}
	});

	JedisPoolConfig config = new JedisPoolConfig();
	config.setMaxActive(5);
	config.setMaxWait(2000);

	redis.setJedisPoolConfig(config);
	redis.setJedisTimeout(2000);

	redis.createClient();

```

