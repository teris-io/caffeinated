/*
 * Copyright (c) 2018 Profidata AG. All rights reserved
 */

package io.teris.caffeinated;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;

import io.teris.caffeinated.CaffeineMultikeyCache.BuilderImpl;


public interface AsyncMultikeyCache<K, PK, V> {

	@Nonnull
	CompletableFuture<V> get(@Nonnull K key, @Nonnull Function<K, PK> keyMapper, @Nonnull Function<K, V> valueLoader);

	@Nullable
	PK getPrimaryKeyIfPresent(@Nonnull K key);

	@Nullable
	V getIfPresent(@Nonnull PK primaryKey);

	interface Builder<K, PK, V> {

		@Nonnull
		Builder<K, PK, V> removalListener(@Nonnull RemovalListener<Set<K>, V> removalListener);

		@Nonnull
		Builder<K, PK, V> executor(@Nonnull Executor executor);

		// TODO unfortunate that caffeine requires a default loader
		@Nonnull
		Builder<K, PK, V> voidPrimaryKeySupplier(@Nonnull Supplier<PK> voidPrimaryKeySupplier);

		// TODO unfortunate that caffeine requires a default loader
		@Nonnull
		Builder<K, PK, V> voidValueSupplier(@Nonnull Supplier<V> voidValueSupplier);

		@Nonnull
		AsyncMultikeyCache<K, PK, V> build();
	}

	@Nonnull
	static <K, PK, V> Builder<K, PK, V> newBuilder(Caffeine<Object, Object> caffeine) {
		return new BuilderImpl<>(caffeine);
	}
}
