package com.netease.redisfailover.listener;

import java.util.EventListener;

public interface DataListener extends EventListener {
	void receiveData(DataEvent event);
}
