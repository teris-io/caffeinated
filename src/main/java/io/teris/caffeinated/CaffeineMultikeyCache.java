/*
 * Copyright (c) 2018 Profidata AG. All rights reserved
 */

package io.teris.caffeinated;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalListener;


class CaffeineMultikeyCache<K, SK, V> implements AsyncMultikeyCache<K, SK, V> {

	final AsyncLoadingCache<K, Multikey<K, SK>> preCache;

	final AsyncLoadingCache<Multikey<K, SK>, V> cache;

	private final Function<K, SK> keyMapper;

	private final Function<K, V> valueLoader;

	private CaffeineMultikeyCache(BuilderImpl<K, SK, V> builder) {
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
	public CompletableFuture<V> get(@Nonnull K key, @Nonnull Function<K, SK> keyMapper, @Nonnull Function<K, V> valueLoader) {
		Set<Multikey<K, SK>> primaryKeyset = cache.synchronous().asMap().keySet();
		return preCache
			.get(key, $ -> {
				Multikey<K, SK> newPrimaryKey = new Multikey<>(keyMapper.apply(key), key);
				if (primaryKeyset.contains(newPrimaryKey)) {
					for (Multikey<K, SK> primaryKey : primaryKeyset) {
						if (newPrimaryKey.equals(primaryKey)) {
							return primaryKey.addKey(key);
						}
					}
				}
				return newPrimaryKey;
			})
			.thenCompose(multikey -> cache.get(multikey, $ -> valueLoader.apply(key)));
	}

	@Nonnull
	@Override
	public CompletableFuture<V> get(@Nonnull K key) {
		return get(key, keyMapper, valueLoader);
	}

	@Nonnull
	@Override
	public CompletableFuture<V> getIfPresent(@Nonnull K key) {
		CompletableFuture<V> res = new CompletableFuture<>();
		Multikey<K, SK> multikey = preCache.synchronous().getIfPresent(key);
		if (multikey == null) {
			res.complete(null);
		}
		else {
			res.complete(cache.synchronous().getIfPresent(multikey));
		}
		return res;
	}

	@Nullable
	@Override
	public SK getPrimaryKeyIfPresent(@Nonnull K key) {
		Multikey<K, SK> multikey = preCache.synchronous().getIfPresent(key);
		return multikey != null ? multikey.getPrimaryKey() : null;
	}

	@Nullable
	@Override
	public V getByPrimaryKeyIfPresent(@Nonnull SK secondaryKey) {
		return cache.synchronous().getIfPresent(new Multikey<>(secondaryKey));
	}

	@Override
	public void invalidate(@Nonnull K key) {
		Multikey<K, SK> primaryKey = preCache.synchronous().getIfPresent(key);
		if (primaryKey != null) {
			cache.synchronous().invalidate(primaryKey);
		}
	}

	@Override
	public void invalidateAll(@Nonnull Iterable<K> keys) {
		LoadingCache<K, Multikey<K, SK>> syncPreCache = preCache.synchronous();
		LoadingCache<Multikey<K, SK>, V> syncPrimaryCache = cache.synchronous();
		keys.forEach(key -> {
			Multikey<K, SK> primaryKey = syncPreCache.getIfPresent(key);
			if (primaryKey != null) {
				syncPrimaryCache.invalidate(primaryKey);
			}
		});
	}

	static class BuilderImpl<K, SK, V> implements Builder<K, SK, V> {

		private final Caffeine<Object, Object> caffeine;

		private RemovalListener<Set<K>, V> removalListener = null;

		private Function<K, SK> keyMapper = $ -> {
			throw new IllegalStateException("cache does not have a default key mapper");
		};

		private Function<K, V> valueLoader = $ -> {
			throw new IllegalStateException("cache does not have a default value loader");
		};

		private Executor executor = Executors.newCachedThreadPool();

		BuilderImpl(Caffeine<Object, Object> caffeine) {
			this.caffeine = caffeine;
		}

		@Nonnull
		@Override
		public Builder<K, SK, V> removalListener(@Nonnull RemovalListener<Set<K>, V> removalListener) {
			this.removalListener = removalListener;
			return this;
		}

		@Nonnull
		@Override
		public Builder<K, SK, V> executor(@Nonnull Executor executor) {
			this.executor = executor;
			return this;
		}

		@Nonnull
		@Override
		public Builder<K, SK, V> keyMapper(@Nonnull Function<K, SK> keyMapper) {
			this.keyMapper = keyMapper;
			return this;
		}

		@Nonnull
		@Override
		public Builder<K, SK, V> valueLoader(@Nonnull Function<K, V> valueLoader) {
			this.valueLoader = valueLoader;
			return this;
		}

		@Nonnull
		@Override
		public AsyncMultikeyCache<K, SK, V> build() {
			Objects.requireNonNull(caffeine, "caffeine builder must not be null");
			return new CaffeineMultikeyCache<>(this);
		}
	}
}
