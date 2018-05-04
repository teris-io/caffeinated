/*
 * Copyright (c) teris.io & Oleg Sklyar, 2018. All rights reserved
 */

package io.teris.caffeinated;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import com.github.benmanes.caffeine.cache.Caffeine;


/**
 * AsyncMultikeyCache defines an asynchronous loading cache mapping from keys of type <K>
 * values of type <V> via a derived key of type <DK>. That is it provides support for
 * multiple keys mapping to the same value, e.g. K1, K2, K3 -> DK1 -> V1; K4, K5 -> DK2 -> V2,
 * supporting simultaneous eviction of all the keys for the same value.
 * <p>
 * Retrieving a value for a key for the first time will not necessarily trigger a new
 * loading of the value if the derived key that the key maps to is already present in the
 * cache and the corresponding value is already loaded, independently if it was loading in
 * reaction to retrieving a value for this same or a different key.
 * <p>
 * Implementations of this interface are expected to be thread-safe, and can be safely
 * accessed by multiple concurrent threads.
 *
 * @param <K> the type of keys maintained by the cache
 * @param <DK> the type of derived keys used as common denominator to access cached values
 * @param <V> the type of mapped values
 */
@ThreadSafe
public interface AsyncMultikeyCache<K, DK, V> {

	/**
	 * The default factory method to get an instance of cache builder for constructing instances of
	 * {@code AsyncMultikeyCache} based on {@code com.github.benmanes.caffeine.cache.AsyncLoadingCache}.
	 *
	 * @param caffeine the instance of {@code caffeine} builder with preconfigured for
	 *                 required cache expiries etc.
	 * @param <K> the type of keys maintained by the cache
	 * @param <DK> the type of derived keys used as common denominator to access cached values
	 * @param <V> the type of mapped values
	 * @return the builder instance
	 */
	@Nonnull
	static <K, DK, V> MultikeyCacheBuilder<K, DK, V> newBuilder(Caffeine<Object, Object> caffeine) {
		return new CaffeinatedMultikeyCacheBuilder<>(caffeine);
	}

	/**
	 * Returns a completable future with the cached value associated with the derived key
	 * mapped to by the {@code keyMapper} from the original {@code key}. If there is no
	 * cached derived key, it is computed asynchronously using the {@code keyMapper}. If,
	 * in turn, there is no cached value for the derived key the former is loaded
	 * asynchronously using the {@code valueLoader}.
	 *
	 * This method should not throw delivering all exceptions via exceptional future
	 * completion.
	 *
	 * @param key the key whose cached value is to be retrieved
	 * @param keyMapper the mapper from the key to a derived key used to access the cached
	 *                  values
	 * @param valueLoader the function to compute the value for the key
	 * @return a completable future completed asynchronously with a cached value, null if
	 *         value is missing (or key is null), or completed exceptionally otherwise
	 */
	@Nonnull
	CompletableFuture<V> get(@Nonnull K key, @Nonnull Function<K, DK> keyMapper, @Nonnull BiFunction<K, DK, V> valueLoader);

	/**
	 * Returns a completable future with the cached value associated with the derived key
	 * mapped to by the default {@code keyMapper} registered on the cache. If there is no
	 * cached derived key, it is computed asynchronously using the default {@code keyMapper}.
	 * If, in turn, there is no cached value for the derived key the former is loaded
	 * asynchronously using the default {@code valueLoader} registered on the cache. If
	 * either {@code keyMapper} or {@code valueLoader} is not registered, the future will be
	 * completed exceptionall with an {@code IllegalStateException}.
	 *
	 * This method should not throw delivering all exceptions via exceptional future
	 * completion.
	 *
	 * @param key the key whose cached value is to be retrieved
	 * @return the completable future completed asynchronously with a cached value, null if
	 *         value is missing (or key is null), or completed exceptionally otherwise
	 */
	@Nonnull
	CompletableFuture<V> get(@Nonnull K key);

	/**
	 * Returns the cached value for the given {@code key} resolving it via the derived key.
	 * If either the value for the derived key or the derived key for the original key
	 * are missing a null is returned. The operation takes place synchronously on the current
	 * thread.
	 *
	 * @param key the key whose cached value is to be retrieved
	 * @return the cached value or null
	 */
	@Nullable
	V getIfPresent(@Nonnull K key);

	/**
	 * Returns the cached derived key for the given {@code key} or null if none available.
	 * The operation takes place synchronously on the current thread.
	 *
	 * @param key the key whose cached value is to be retrieved
	 * @return the cached derived key or null
	 */
	@Nullable
	DK getDerivedKeyIfPresent(@Nonnull K key);

	/**
	 * Returns the cached value for the derived key or null if none available.
	 * The operation takes place synchronously on the current thread.
	 *
	 * @param derivedKey the derived key whose cached value is to be retrieved
	 * @return the cached value or null
	 */
	@Nullable
	V getByDerivedKeyIfPresent(@Nonnull DK derivedKey);

	/**
	 * Invalidates the key evicting the corresponding derived key, the value and all other
	 * keys associated with the same derived key.
	 *
	 * @param key the key to invalidate
	 */
	void invalidate(@Nonnull K key);

	/**
	 * Invalidates the keys evicting the corresponding derived keys, the values and all other
	 * keys associated with the same derived keys.
	 *
	 * @param keys the keys to invalidate
	 */
	void invalidateAll(@Nonnull Iterable<K> keys);
}
