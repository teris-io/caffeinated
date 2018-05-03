/*
 * Copyright (c) teris.io & Oleg Sklyar, 2018. All rights reserved
 */

package io.teris.caffeinated.fixture.entity;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;


public class Session {

	public static final AtomicInteger createdCount = new AtomicInteger();

	public static final AtomicInteger expiredCount = new AtomicInteger();

	public final String username;

	public final String sessionId;

	public final CompletableFuture<Void> expired = new CompletableFuture<>();

	public Session(String username) {
		this.username = username;
		sessionId = UUID.randomUUID().toString();
		createdCount.addAndGet(1);
	}

	public void expire() {
		expiredCount.addAndGet(1);
		expired.complete(null);
	}
}
