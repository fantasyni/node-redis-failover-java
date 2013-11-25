package com.netease.redisfailover.client;

public class RedisBean {
	private String host;
	private int port;
	private String password;

	public String getPassword() {
		return password;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String toString() {
		return null;
	}
}
