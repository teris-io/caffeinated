/*
 * Copyright (c) 2018 Profidata AG. All rights reserved
 */

package io.teris.caffeinated;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;

import io.teris.caffeinated.CaffeineMultikeyCache.BuilderImpl;


public interface AsyncMultikeyCache<K, SK, V> {

	@Nonnull
	CompletableFuture<V> get(@Nonnull K key, @Nonnull Function<K, SK> keyMapper, @Nonnull Function<K, V> valueLoader);

	@Nonnull
	CompletableFuture<V> get(@Nonnull K key);

	@Nonnull
	CompletableFuture<V> getIfPresent(@Nonnull K key);

	@Nullable
	SK getPrimaryKeyIfPresent(@Nonnull K key);

	@Nullable
	V getByPrimaryKeyIfPresent(@Nonnull SK secondaryKey);

	void invalidate(@Nonnull K key);

	void invalidateAll(@Nonnull Iterable<K> keys);

	interface Builder<K, SK, V> {

		@Nonnull
		Builder<K, SK, V> removalListener(@Nonnull RemovalListener<Set<K>, V> removalListener);

		@Nonnull
		Builder<K, SK, V> executor(@Nonnull Executor executor);

		@Nonnull
		Builder<K, SK, V> keyMapper(@Nonnull Function<K, SK> keyMapper);

		@Nonnull
		Builder<K, SK, V> valueLoader(@Nonnull Function<K, V> valueLoader);

		@Nonnull
		AsyncMultikeyCache<K, SK, V> build();
	}

	@Nonnull
	static <K, PK, V> Builder<K, PK, V> newBuilder(Caffeine<Object, Object> caffeine) {
		return new BuilderImpl<>(caffeine);
	}
}
