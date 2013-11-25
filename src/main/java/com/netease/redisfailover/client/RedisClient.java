package com.netease.redisfailover.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netease.redisfailover.client.zookeeper.ConnectionWatcher;
import com.netease.redisfailover.listener.DataEvent;
import com.netease.redisfailover.listener.DataListener;

/**
 * @author fantasyni
 * 
 */
public class RedisClient implements Watcher, ClientOperations {
	public static final String DEFAUL_PATH = "/redis_failover/redis";
	private static final String EVENT_CHANGE = "change";
	private static final String EVENT_MASTER_CHANGE = "masterChange";
	private static int JEDIS_DEFAULT_TIMEOUT = 2000;

	private HashMap<String, JedisPool> clientPool = new HashMap<String, JedisPool>();
	private String zkPath;
	private String hosts;
	private String chroot;
	private String zkUsername;
	private String zkPassword;
	private ConnectionWatcher connectionWatcher;
	private Properties props;
	private JsonNode redisState;
	private int slaveIndex;
	private JedisPoolConfig jConfig;
	private int jedis_timeout;

	private Map<String, List<DataListener>> listeners;

	/**
	 * RedisClient constructor function
	 * 
	 * @param props
	 */
	public RedisClient(Properties props) {
		this.props = props;
		this.zkPath = props.getProperty("zkPath"); // zookeeper watch path
		this.hosts = props.getProperty("servers"); // zookeeper cluster servers
		this.chroot = props.getProperty("chroot"); // zookeeper chroot
		this.zkUsername = props.getProperty("username"); // zookeeper username
		this.zkPassword = props.getProperty("password"); // zookeeper password
		this.jConfig = new JedisPoolConfig();
		this.jedis_timeout = JEDIS_DEFAULT_TIMEOUT;
		if (this.zkPath == null) {
			this.zkPath = DEFAUL_PATH;
		}

		connectionWatcher = new ConnectionWatcher();
		listeners = new HashMap<String, List<DataListener>>();
	}

	@Override
	public void process(WatchedEvent event) {
		if (event.getType() == Event.EventType.NodeDataChanged) {
			JsonNode expiredState = this.redisState;
			this.redisState = getZKData();
			String exMasterString = expiredState.get("master").asText();
			String cuMasterString = redisState.get("master").asText();
			JsonNode passNode = redisState.get("password");
			String password = "";
			if (passNode != null) {
				password = passNode.asText();
			}

			addClient(cuMasterString, password);
			JsonNode slaves = redisState.get("slaves");
			Iterator<JsonNode> slavesElements = slaves.iterator();
			while (slavesElements.hasNext()) {
				JsonNode slave = slavesElements.next();
				addClient(slave.asText(), password);
			}

			if (!exMasterString.equals(cuMasterString)) {
				emit(EVENT_MASTER_CHANGE);
			} else {
				emit(EVENT_CHANGE);
			}
		}
	}

	@Override
	public void createClient() {
		createZKClient();
		String cuMasterString = redisState.get("master").asText();
		JsonNode passNode = redisState.get("password");
		String password = "";
		if (passNode != null) {
			password = passNode.asText();
		}
		addClient(cuMasterString, password);
		JsonNode slaves = redisState.get("slaves");
		Iterator<JsonNode> slavesElements = slaves.iterator();
		while (slavesElements.hasNext()) {
			JsonNode slave = slavesElements.next();
			addClient(slave.asText(), password);
		}
		emit("ready");
	}

	@Override
	public void createZKClient() {
		String connectString = this.hosts;
		try {
			if (chroot != null && !chroot.isEmpty()) {
				connectString = connectString + chroot;
			}
			connectionWatcher.connect(connectString);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			String authString = zkUsername + ":" + zkPassword;

			if (zkUsername != null) {
				connectionWatcher.addAuthInfo("digest", authString.getBytes());
			}
			this.redisState = getZKData();
		}
	}

	@Override
	public JsonNode getZKData() {
		try {
			String data = connectionWatcher.read(zkPath, this);
			ObjectMapper objectMapper = new ObjectMapper();
			JsonNode redisState = objectMapper.readTree(data);
			return redisState;
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (KeeperException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public JedisPool createRedisClient(RedisBean redisClient) {
		String host = redisClient.getHost();
		int port = redisClient.getPort();
		String password = redisClient.getPassword();
		JedisPool pool;
		if (password.isEmpty()) {
			pool = new JedisPool(jConfig, host, port, jedis_timeout);
		} else {
			pool = new JedisPool(jConfig, host, port, jedis_timeout, password);
		}

		return pool;
	}

	@Override
	public void setJedisPoolConfig(JedisPoolConfig config) {
		this.jConfig = config;
	}

	@Override
	public void setJedisTimeout(int timeout) {
		this.jedis_timeout = timeout;
	}

	@Override
	public synchronized void addClient(String name, String password) {
		JedisPool client = clientPool.get(name);
		if (client != null) {
			return;
		}

		String[] arry = name.split(":");
		String host = arry[0];
		int port = Integer.valueOf(arry[1]);

		RedisBean redisClient = new RedisBean();
		redisClient.setHost(host);
		redisClient.setPort(port);
		redisClient.setPassword(password);

		JedisPool jedisPool = createRedisClient(redisClient);
		clientPool.put(name, jedisPool);
	}

	@Override
	public Jedis getClient(String role) {
		JedisPool client = null;
		String name;
		if (role.equals("slave")) {
			JsonNode slaves = redisState.get("slaves");
			if (slaves.size() == 0) {
				return masterClient();
			}

			if (slaveIndex >= slaves.size()) {
				slaveIndex = 0;
			}

			name = slaves.get(slaveIndex).asText();
			slaveIndex++;
			client = clientPool.get(name);
		} else {
			JsonNode master = redisState.get("master");
			name = master.asText();
			client = clientPool.get(name);
		}
		return client.getResource();
	}

	@Override
	public Jedis masterClient() {
		JsonNode master = redisState.get("master");
		String name = master.asText();
		JedisPool client = clientPool.get(name);
		return client.getResource();
	}

	@Override
	public JsonNode getRedisState() {
		return redisState;
	}

	/**
	 * Add event listener and wait for broadcast message.
	 * 
	 * @param event
	 * @param listener
	 */
	public synchronized void on(String event, DataListener listener) {
		List<DataListener> list = listeners.get(event);
		if (list == null)
			list = new ArrayList<DataListener>();
		list.add(listener);
		listeners.put(event, list);
	}

	/**
	 * Touch off the event and call listeners corresponding route.
	 * 
	 * @param event
	 * @param message
	 * @return true if call success, false if there is no listeners for this
	 *         route.
	 */
	private void emit(String event) {
		List<DataListener> list = listeners.get(event);
		if (list == null) {
			return;
		}
		for (DataListener listener : list) {
			DataEvent e = new DataEvent(this);
			listener.receiveData(e);
		}
	}
}
