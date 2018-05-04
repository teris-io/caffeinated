/*
 * Copyright (c) teris.io & Oleg Sklyar, 2018. All rights reserved
 */

package io.teris.caffeinated;

import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nonnull;

import com.github.benmanes.caffeine.cache.RemovalListener;


/**
 * MultikeyCacheBuilder defines a builder to parametrize and build instances of multikey
 * caches.
 *
 * @param <K> the type of keys maintained by the cache
 * @param <DK> the type of derived keys used as common denominator to access cached values
 * @param <V> the type of mapped values
 */
public interface MultikeyCacheBuilder<K, DK, V> {

	/**
	 * Sets a callback triggered on eviction of a value with all the keys pointing to that value.
	 *
	 * @param removalListener the callback handler
	 * @return the updated builder
	 */
	@Nonnull
	MultikeyCacheBuilder<K, DK, V> removalListener(@Nonnull RemovalListener<Set<K>, V> removalListener);

	/**
	 * Sets the default mapper from keys to derived keys. This value can always be overwritten
	 * when using the {@code get} method supplying the key mapper and the value loader.
	 * There is no default mapper unless explicitly set and the returned future will be
	 * completed exceptionally with an {@code IllegalStateException} if the {@code get}
	 * method without the mapper and loader is used.
	 *
	 * @param keyMapper the function to map from key to derived key
	 * @return the updated builder
	 */
	@Nonnull
	MultikeyCacheBuilder<K, DK, V> keyMapper(@Nonnull Function<K, DK> keyMapper);

	/**
   * Sets the default value loader to map from keys to cached values. This value can always be overwritten
	 * when using the {@code get} method supplying the key mapper and the value loader.
	 * There is no default mapper unless explicitly set and the returned future will be
	 * completed exceptionally with an {@code IllegalStateException} if the {@code get}
	 * method without the mapper and loader is used.
	 *
	 * @param valueLoader the function to map from key/derived-key pair to the cached value
	 * @return the updated builder
	 */
	@Nonnull
	MultikeyCacheBuilder<K, DK, V> valueLoader(@Nonnull BiFunction<K, DK, V> valueLoader);

	/**
	 * Sets the default executor for evaluating key mapper and value loader asynchronously.
	 * By default a cached executor pool is used.
	 *
	 * @param executor the executor to use
	 * @return the updated builder
	 */
	@Nonnull
	MultikeyCacheBuilder<K, DK, V> executor(@Nonnull Executor executor);

	/**
	 * Builds an instance of {@code AsyncMultikeyCache} implementation.
	 *
	 * @return the newly constructed instance of {@code AsyncMultikeyCache} implementation.
	 */
	@Nonnull
	AsyncMultikeyCache<K, DK, V> buildAsync();
}
