/*
 * Copyright (c) teris.io & Oleg Sklyar, 2018. All rights reserved
 */

package io.teris.caffeinated;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.github.benmanes.caffeine.cache.Caffeine;

import io.teris.caffeinated.dao.AuthenticationService;
import io.teris.caffeinated.dao.DummyAuthenticationService;
import io.teris.caffeinated.dao.DummyUserResolutionService;
import io.teris.caffeinated.dao.UserResolutionService;


public class CaffeinatedMultikeyCacheTest {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	private final UserResolutionService userService = new DummyUserResolutionService();

	private final AuthenticationService authService = new DummyAuthenticationService(userService);



	@Test
	public void get_samePrimaryKey_loaderCountOnMultipleAccess_once() throws Exception {
		AtomicInteger mapperCalled = new AtomicInteger(0);
		Function<String, String> keyMapper = (key) -> {
			mapperCalled.addAndGet(1);
			return key.toUpperCase();
		};

		AtomicInteger loaderCalled = new AtomicInteger(0);
		Function<String, Integer> valueLoader = (primaryKey) -> {
			loaderCalled.addAndGet(1);
			return primaryKey.length();
		};

		AsyncMultikeyCache<String, String, Integer> cache = AsyncMultikeyCache.<String, String, Integer>newBuilder(Caffeine.newBuilder())
			.buildAsync();

		List<CompletableFuture<Integer>> futures = new ArrayList<>();
		for (int i = 0; i < 1000; i++) {
			for (String key: Arrays.asList("aaa", "aAa", "AaA", "AAa", "aaA")) {
				futures.add(cache.get(key, keyMapper, valueLoader));
			}
		}

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, TimeUnit.SECONDS);

		assertEquals(5, mapperCalled.get());
		assertEquals(1, loaderCalled.get());

		CaffeinatedMultikeyCache<String, String, Integer> underTest = (CaffeinatedMultikeyCache<String, String, Integer>) cache;
		assertEquals(5, underTest.preCache.synchronous().asMap().keySet().size());
		assertEquals(1, underTest.cache.synchronous().asMap().keySet().size());
	}

	@Test
	public void invalidate_allKeysInCallback() throws Exception {

		CompletableFuture<Set<String>> removed = new CompletableFuture<>();

		AsyncMultikeyCache<String, String, Integer> cache = AsyncMultikeyCache.<String, String, Integer>newBuilder(Caffeine.newBuilder())
			.removalListener((keys, value, cause) -> removed.complete(keys))
			.buildAsync();

		Set<String> keys = new HashSet<>(Arrays.asList("aaa", "aAa", "AaA", "AAa", "aaA"));

		List<CompletableFuture<Integer>> futures = new ArrayList<>();
		for (int i = 0; i < 1000; i++) {
			for (String key: keys) {
				futures.add(cache.get(key, String::toUpperCase, String::length));
			}
		}

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, TimeUnit.SECONDS);

		cache.invalidate("aaa");
		assertEquals(keys, removed.get(5, TimeUnit.SECONDS));

		CaffeinatedMultikeyCache<String, String, Integer> underTest = (CaffeinatedMultikeyCache<String, String, Integer>) cache;
		assertEquals(0, underTest.preCache.synchronous().asMap().keySet().size());
		assertEquals(0, underTest.cache.synchronous().asMap().keySet().size());
	}
}
