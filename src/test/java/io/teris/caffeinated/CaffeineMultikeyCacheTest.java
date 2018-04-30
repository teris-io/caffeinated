/*
 * Copyright (c) 2018 Profidata AG. All rights reserved
 */

package io.teris.caffeinated;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.github.benmanes.caffeine.cache.Caffeine;


public class CaffeineMultikeyCacheTest {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	@Test
	public void get_samePrimaryKey_loaderCountOnMultipleAccess_once() {
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
			.voidPrimaryKeySupplier(() -> UUID.randomUUID().toString())
			.voidValueSupplier(() -> Integer.MIN_VALUE)
			.build();

		List<CompletableFuture<Integer>> futures = new ArrayList<>();
		for (int i = 0; i < 1000; i++) {
			for (String key: Arrays.asList("aaa", "aAa", "AaA", "AAa", "aaA")) {
				futures.add(cache.get(key, keyMapper, valueLoader));
			}
		}
		futures.forEach(f -> {
			try {
				assertEquals(3, f.get(5, TimeUnit.SECONDS).intValue());
			}
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		});

		CaffeineMultikeyCache<String, String, Integer> underTest = (CaffeineMultikeyCache<String, String, Integer>) cache;
		assertEquals(5, underTest.preCache.synchronous().asMap().keySet().size());
		assertEquals(1, underTest.primaryCache.synchronous().asMap().keySet().size());
		assertEquals(5, mapperCalled.get());
		assertEquals(1, loaderCalled.get());
	}

	@Ignore("the removal listener does not get called yet")
	@Test
	public void get_onRemoval_allKeysInCallback() throws Exception {
		Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
			.expireAfterAccess(100, TimeUnit.MILLISECONDS);

		CompletableFuture<Set<String>> removed = new CompletableFuture<>();

		AsyncMultikeyCache<String, String, Integer> cache = AsyncMultikeyCache.<String, String, Integer>newBuilder(caffeine)
			.voidPrimaryKeySupplier(() -> UUID.randomUUID().toString())
			.voidValueSupplier(() -> Integer.MIN_VALUE)
			.removalListener((keys, value, cause) -> removed.complete(keys))
			.build();

		Set<String> keys = new HashSet<>(Arrays.asList("aaa", "aAa", "AaA", "AAa", "aaA"));

		List<CompletableFuture<Integer>> futures = new ArrayList<>();
		for (int i = 0; i < 1000; i++) {
			for (String key: keys) {
				futures.add(cache.get(key, String::toUpperCase, String::length));
			}
		}
		futures.forEach(f -> {
			try {
				f.get(5, TimeUnit.SECONDS);
			}
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		});
		assertEquals(keys, removed.get(5, TimeUnit.SECONDS));
	}
}
