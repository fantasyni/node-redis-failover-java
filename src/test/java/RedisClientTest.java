import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPoolConfig;

import com.netease.redisfailover.client.RedisClient;
import com.netease.redisfailover.listener.DataEvent;
import com.netease.redisfailover.listener.DataListener;

public class RedisClientTest {

	public static void main(String[] args) throws IOException,
			InterruptedException {
		InputStream ips = RedisClientTest.class
				.getResourceAsStream("config.properties");

		Properties props = new Properties();
		props.load(ips);

		final RedisClient redis = new RedisClient(props);

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
				if (masterRedis != null) {
					System.out.println(masterRedis.ping());
				}
				Jedis slaveRedis = redis.getClient("slave");
				if (slaveRedis != null) {
					System.out.println(slaveRedis.ping());
				}
			}
		});

		redis.on("masterChange", new DataListener() {

			public void receiveData(DataEvent event) {
				System.out.println("masterChange");
				System.out.println(redis.getRedisState().toString());
				Jedis masterRedis = redis.masterClient();
				if (masterRedis != null) {
					System.out.println(masterRedis.ping());
				}
				Jedis slaveRedis = redis.getClient("slave");
				if (slaveRedis != null) {
					System.out.println(slaveRedis.ping());
				}
			}
		});

		JedisPoolConfig config = new JedisPoolConfig();
		config.setMaxActive(5);
		config.setMaxWait(2000);

		redis.setJedisPoolConfig(config);
		redis.setJedisTimeout(2000);

		redis.createClient();

		Thread.sleep(Long.MAX_VALUE);
	}

}
