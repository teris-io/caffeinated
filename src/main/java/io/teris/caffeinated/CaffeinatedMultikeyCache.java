/*
 * Copyright (c) teris.io & Oleg Sklyar, 2018. All rights reserved
 */

package io.teris.caffeinated;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;


/**
 * Implements AsyncMultikeyCache on top of two AsyncLoadingCache instances from caffeine,
 * in which the `preCache` mapping from keys to derived keys is used as an async and
 * performant map only (that is without automated eviction etc.) and all the caching
 * parametrization is applied to the `cache` instance mapping from derived keys to values.
 */
class CaffeinatedMultikeyCache<K, DK, V> implements AsyncMultikeyCache<K, DK, V> {

	final AsyncLoadingCache<K, Multikey<K, DK>> preCache;

	final AsyncLoadingCache<Multikey<K, DK>, V> cache;

	private final Function<K, DK> keyMapper;

	private final Function<K, V> valueLoader;

	CaffeinatedMultikeyCache(CaffeinatedMultikeyCacheBuilder<K, DK, V> builder) {
		preCache = Caffeine.newBuilder()
			.executor(builder.executor)
			.buildAsync($ -> {
				throw new IllegalStateException("cache requires external key mapper");
			});
		cache = builder.caffeine
			.executor(builder.executor)
			.removalListener(new MultikeyRemovalListener<>(preCache, builder.removalListener))
			.buildAsync($ -> {
				throw new IllegalStateException("cache requires external value loader");
			});
		keyMapper = builder.keyMapper;
		valueLoader = builder.valueLoader;
	}

	@Override
	@Nonnull
	public CompletableFuture<V> get(@Nonnull K key, @Nonnull Function<K, DK> keyMapper, @Nonnull Function<K, V> valueLoader) {
		Set<Multikey<K, DK>> multikeySet = cache.synchronous().asMap().keySet();
		return preCache
			.get(key, $ -> {
				Multikey<K, DK> newMultikey = new Multikey<>(keyMapper.apply(key), key);
				if (multikeySet.contains(newMultikey)) {
					for (Multikey<K, DK> multikey : multikeySet) {
						if (newMultikey.equals(multikey)) {
							return multikey.addKey(key);
						}
					}
				}
				return newMultikey;
			})
			.thenCompose(multikey -> cache.get(multikey, $ -> valueLoader.apply(key)));
	}

	@Nonnull
	@Override
	public CompletableFuture<V> get(@Nonnull K key) {
		return get(key, keyMapper, valueLoader);
	}

	@Nullable
	@Override
	public V getIfPresent(@Nonnull K key) {
		Multikey<K, DK> multikey = preCache.synchronous().getIfPresent(key);
		if (multikey == null) {
			return null;
		}
		return cache.synchronous().getIfPresent(multikey);
	}

	@Nullable
	@Override
	public DK getDerivedKeyIfPresent(@Nonnull K key) {
		Multikey<K, DK> multikey = preCache.synchronous().getIfPresent(key);
		return multikey != null ? multikey.getDerivedKey() : null;
	}

	@Nullable
	@Override
	public V getByDerivedKeyIfPresent(@Nonnull DK derivedKey) {
		return cache.synchronous().getIfPresent(new Multikey<>(derivedKey));
	}

	@Override
	public void invalidate(@Nonnull K key) {
		Multikey<K, DK> multikey = preCache.synchronous().getIfPresent(key);
		if (multikey != null) {
			cache.synchronous().invalidate(multikey);
		}
	}

	@Override
	public void invalidateAll(@Nonnull Iterable<K> keys) {
		LoadingCache<K, Multikey<K, DK>> syncPreCache = preCache.synchronous();
		LoadingCache<Multikey<K, DK>, V> syncCache = cache.synchronous();
		keys.forEach(key -> {
			Multikey<K, DK> multikey = syncPreCache.getIfPresent(key);
			if (multikey != null) {
				syncCache.invalidate(multikey);
			}
		});
	}
}
