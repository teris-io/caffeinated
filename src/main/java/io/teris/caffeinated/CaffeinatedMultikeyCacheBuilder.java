/*
 * Copyright (c) teris.io & Oleg Sklyar, 2018. All rights reserved
 */

package io.teris.caffeinated;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import javax.annotation.Nonnull;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;


class CaffeinatedMultikeyCacheBuilder<K, DK, V> implements MultikeyCacheBuilder<K, DK, V> {

	final Caffeine<Object, Object> caffeine;

	RemovalListener<Set<K>, V> removalListener = null;

	Function<K, DK> keyMapper = $ -> {
		throw new IllegalStateException("missing default key mapper");
	};

	Function<K, V> valueLoader = $ -> {
		throw new IllegalStateException("missing default value loader");
	};

	Executor executor = Executors.newCachedThreadPool();

	CaffeinatedMultikeyCacheBuilder(Caffeine<Object, Object> caffeine) {
		this.caffeine = caffeine;
	}

	@Nonnull
	@Override
	public MultikeyCacheBuilder<K, DK, V> removalListener(@Nonnull RemovalListener<Set<K>, V> removalListener) {
		this.removalListener = removalListener;
		return this;
	}

	@Nonnull
	@Override
	public MultikeyCacheBuilder<K, DK, V> keyMapper(@Nonnull Function<K, DK> keyMapper) {
		this.keyMapper = keyMapper;
		return this;
	}

	@Nonnull
	@Override
	public MultikeyCacheBuilder<K, DK, V> valueLoader(@Nonnull Function<K, V> valueLoader) {
		this.valueLoader = valueLoader;
		return this;
	}

	@Nonnull
	@Override
	public MultikeyCacheBuilder<K, DK, V> executor(@Nonnull Executor executor) {
		this.executor = executor;
		return this;
	}

	@Nonnull
	@Override
	public AsyncMultikeyCache<K, DK, V> buildAsync() {
		Objects.requireNonNull(caffeine, "caffeine builder must not be null");
		return new CaffeinatedMultikeyCache<>(this);
	}
}
