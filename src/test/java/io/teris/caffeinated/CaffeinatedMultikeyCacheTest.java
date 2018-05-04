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
import java.util.function.BiFunction;
import java.util.function.Function;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.github.benmanes.caffeine.cache.Caffeine;


public class CaffeinatedMultikeyCacheTest {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	@Test
	public void get_samePrimaryKey_loaderCountOnMultipleAccess_once() throws Exception {
		AtomicInteger mapperCalled = new AtomicInteger(0);
		Function<String, String> keyMapper = (key) -> {
			mapperCalled.addAndGet(1);
			return key.toUpperCase();
		};

		AtomicInteger loaderCalled = new AtomicInteger(0);
		BiFunction<String, String, Integer> valueLoader = (primaryKey, derivedKey) -> {
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
				futures.add(cache.get(key, String::toUpperCase, (primaryKey, derivedKey) -> primaryKey.length()));
			}
		}

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, TimeUnit.SECONDS);

		cache.invalidate("aaa");
		assertEquals(keys, removed.get(5, TimeUnit.SECONDS));
	}
}
