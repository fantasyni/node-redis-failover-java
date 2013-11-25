package com.netease.redisfailover.client.zookeeper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

public class ConnectionWatcher implements Watcher {

	private static final int SESSION_TIMEOUT = 5000;
	protected ZooKeeper zk;
	private CountDownLatch _connectedSignal = new CountDownLatch(1);
	private static final Charset CHARSET = Charset.forName("UTF-8");

	public void connect(String hosts) throws IOException, InterruptedException {
		zk = new ZooKeeper(hosts, SESSION_TIMEOUT, this);
		_connectedSignal.await();
	}

	public void addAuthInfo(String scheme, byte[] auth) {
		zk.addAuthInfo(scheme, auth);
	}

	@Override
	public void process(WatchedEvent event) {
		if (event.getState() == Event.KeeperState.SyncConnected) {
			_connectedSignal.countDown();
		}
	}

	public void close() throws InterruptedException {
		zk.close();
	}

	public String read(String path, Watcher watcher)
			throws InterruptedException, KeeperException {
		byte[] data = zk.getData(path, watcher, null /* stat */);
		return new String(data, CHARSET);
	}

}
