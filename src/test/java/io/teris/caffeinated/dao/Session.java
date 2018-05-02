/*
 * Copyright (c) teris.io & Oleg Sklyar, 2018. All rights reserved
 */

package io.teris.caffeinated.dao;

import java.util.UUID;


public class Session {

	public final String username;

	public final String sessionId;

	public Session(String username) {
		this.username = username;
		sessionId = UUID.randomUUID().toString();
	}
}
