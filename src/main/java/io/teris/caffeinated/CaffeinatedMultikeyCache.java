/*
 * Copyright (c) teris.io & Oleg Sklyar, 2018. All rights reserved
 */

package io.teris.caffeinated;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;


/**
 * Implements AsyncMultikeyCache on top of two AsyncLoadingCache instances from caffeine,
 * in which the `preCache` mapping from keys to derived keys is used as an async and
 * performant map only (that is without automated eviction etc.) and all the caching
 * parametrization is applied to the `cache` instance mapping from derived keys to values.
 */
class CaffeinatedMultikeyCache<K, DK, V> implements AsyncMultikeyCache<K, DK, V> {

	final AsyncLoadingCache<K, DK> keys2derivedKey;

	final Cache<DK, Set<K>> derivedKey2Keys;

	final AsyncLoadingCache<DK, V> cache;

	private final Function<K, DK> keyMapper;

	private final Function<K, V> valueLoader;

	private final RemovalListener<Set<K>, V> removalListener;

	CaffeinatedMultikeyCache(CaffeinatedMultikeyCacheBuilder<K, DK, V> builder) {
		keys2derivedKey = Caffeine.newBuilder()
			.executor(builder.executor)
			.buildAsync($ -> {
				throw new IllegalStateException("missing default key mapper");
			});
		derivedKey2Keys = Caffeine.newBuilder().build();
		cache = builder.caffeine
			.executor(builder.executor)
			.removalListener(this::onRemoval)
			.buildAsync($ -> {
				throw new IllegalStateException("missing default value loader");
			});
		keyMapper = builder.keyMapper;
		valueLoader = builder.valueLoader;
		removalListener = builder.removalListener;
	}

	@Override
	@Nonnull
	public CompletableFuture<V> get(@Nonnull K key, @Nonnull Function<K, DK> keyMapper, @Nonnull Function<K, V> valueLoader) {
		AtomicReference<DK> derivedKeyHolder = new AtomicReference<>(null);
		return keys2derivedKey
			.get(key, $ -> {
				DK derivedKey = keyMapper.apply(key);
				derivedKey2Keys.get(derivedKey, $$ -> ConcurrentHashMap.newKeySet()).add(key);
				return derivedKey;
			})
			.thenCompose(derivedKey -> {
				derivedKeyHolder.set(derivedKey);
				return cache.get(derivedKey, $ -> valueLoader.apply(key));
			})
			.exceptionally((t) -> {
				DK derivedKey = derivedKeyHolder.get();
				// intentional: only true on successful key mapping
				if (derivedKey != null) {
					try {
						onRemoval(derivedKey, null, RemovalCause.EXPLICIT);
					} catch (Exception ex) {
						// ignored in favour of original exception
					}
				}
				throw t instanceof RuntimeException ? (RuntimeException) t : new RuntimeException(t);
			});
	}

	@Nonnull
	@Override
	public CompletableFuture<V> get(@Nonnull K key) {
		return get(key, keyMapper, valueLoader);
	}

	@Nullable
	@Override
	public V getIfPresent(@Nonnull K key) {
		DK derivedKey= keys2derivedKey.synchronous().getIfPresent(key);
		return derivedKey != null ? cache.synchronous().getIfPresent(derivedKey) : null;
	}

	@Nullable
	@Override
	public DK getDerivedKeyIfPresent(@Nonnull K key) {
		return keys2derivedKey.synchronous().getIfPresent(key);
	}

	@Nullable
	@Override
	public V getByDerivedKeyIfPresent(@Nonnull DK derivedKey) {
		return cache.synchronous().getIfPresent(derivedKey);
	}

	@Override
	public void invalidate(@Nonnull K key) {
		DK derivedKey = keys2derivedKey.synchronous().getIfPresent(key);
		if (derivedKey != null) {
			cache.synchronous().invalidate(derivedKey);
		}
	}

	@Override
	public void invalidateAll(@Nonnull Iterable<K> keys) {
		LoadingCache<K, DK> syncPreCache = keys2derivedKey.synchronous();
		LoadingCache<DK, V> syncCache = cache.synchronous();
		keys.forEach(key -> {
			DK derivedKey = syncPreCache.getIfPresent(key);
			if (derivedKey != null) {
				syncCache.invalidate(derivedKey);
			}
		});
	}

	private void onRemoval(@Nullable DK derivedKey, @Nullable V v, @Nonnull RemovalCause cause) {
		if (derivedKey != null) {
			Set<K> keys = derivedKey2Keys.getIfPresent(derivedKey);
			derivedKey2Keys.invalidate(derivedKey);
			if (keys != null) {
				if (removalListener != null) {
					removalListener.onRemoval(keys, v, cause);
				}
				keys2derivedKey.synchronous().invalidateAll(keys);
			}
		}
	}

}
