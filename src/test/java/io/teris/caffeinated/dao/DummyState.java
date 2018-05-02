/*
 * Copyright (c) teris.io & Oleg Sklyar, 2018. All rights reserved
 */

package io.teris.caffeinated.dao;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


class DummyState {

	static Set<String> users = ConcurrentHashMap.newKeySet();
	static {
		users.add("am9lOmhrQVMtNER0aS1nZzUzMi1E");
		users.add("bWFuZHk6Z3JCQS0zUlRULWZHMUVhLTU=");
	}

	static Set<String> keys = ConcurrentHashMap.newKeySet();
	static {
		keys.add("am9lOndqZ2VvYXc0dGY5ODM0dHo=");
		keys.add("bWFuZHk6d2pnZW9hdzR0Zjk4MzR0eg==");
	}
}
