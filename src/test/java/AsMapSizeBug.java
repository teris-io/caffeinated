/*
 * Copyright (c) teris.io & Oleg Sklyar, 2018. All rights reserved
 */

import static org.junit.Assert.assertEquals;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;


public class AsMapSizeBug {

	@Test
	public void noLoaderExceptions_correctMapSize() throws Exception {
		AsyncLoadingCache<String, Integer> cache = Caffeine.newBuilder().buildAsync(String::length);

		cache.get("a")
			.thenCompose($ -> cache.get("b"))
			.thenCompose($ -> cache.get("c"))
			.get(5, TimeUnit.SECONDS);
		assertEquals(3, cache.synchronous().asMap().size());
	}

	@Test
	public void withLoaderExceptions_incorrectMapSize() throws Exception {
		AsyncLoadingCache<String, Integer> cache = Caffeine.newBuilder().buildAsync(k -> {
			int res = k.length();
			if (res > 1) throw new IllegalArgumentException("loading error");
			return res;
		});

		cache.get("a")
			.thenCompose($ -> cache.get("b"))
			.thenCompose($ -> cache.get("c"))
			.get(5, TimeUnit.SECONDS);
		assertEquals(3, cache.synchronous().asMap().size());

		try {
			cache.get("aa").get(5, TimeUnit.SECONDS);
			throw new AssertionError("unreachable");
		} catch (ExecutionException ex) {
			// ignore
		}

		try {
			cache.get("bb").get(5, TimeUnit.SECONDS);
			throw new AssertionError("unreachable");
		} catch (ExecutionException ex) {
			// ignore
		}

		assertEquals("{a=1, b=1, c=1}", cache.synchronous().asMap().toString());
		// BUG
		assertEquals(5, cache.synchronous().asMap().size());
	}
}
