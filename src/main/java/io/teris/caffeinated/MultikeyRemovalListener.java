/*
 * Copyright (c) 2018 Profidata AG. All rights reserved
 */

package io.teris.caffeinated;

import java.util.Collections;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;


class MultikeyRemovalListener<K, PK, V> implements RemovalListener<Multikey<K ,PK>, V> {

	private final AsyncLoadingCache<K, Multikey<K, PK>> preCache;

	private final RemovalListener<Set<K>, V> removalListener;

	MultikeyRemovalListener(@Nonnull AsyncLoadingCache<K, Multikey<K, PK>> preCache, @Nullable RemovalListener<Set<K>, V> removalListener) {
		this.preCache = preCache;
		this.removalListener = removalListener;
	}

	@Override
	public void onRemoval(@Nullable Multikey<K, PK> pk, @Nullable V v, @Nonnull RemovalCause cause) {
		if (pk != null) {
			Set<K> keys = Collections.unmodifiableSet(pk.getKeys());
			if (removalListener != null) {
				removalListener.onRemoval(keys, v, cause);
			}
			preCache.synchronous().invalidateAll(keys);
		}
	}
}
