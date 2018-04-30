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
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;


class CaffeineMultikeyCache<K, PK, V> implements AsyncMultikeyCache<K, PK, V> {

	final AsyncLoadingCache<K, Multikey<K, PK>> preCache;

	final AsyncLoadingCache<Multikey<K, PK>, V> primaryCache;

	private CaffeineMultikeyCache(BuilderImpl<K, PK, V> builder) {
		preCache = Caffeine.newBuilder()
			.executor(builder.executor)
			.buildAsync(key -> new Multikey<>(builder.voidPrimaryKeySupplier.get(), key));
		primaryCache = builder.caffeine
			.executor(builder.executor)
			.removalListener(new MultikeyRemovalListener<>(preCache, builder.removalListener))
			.buildAsync($ -> builder.voidValueSupplier.get());
	}

	@Override
	@Nonnull
	public CompletableFuture<V> get(@Nonnull K key, @Nonnull Function<K, PK> keyMapper, @Nonnull Function<K, V> valueLoader) {
		return preCache
			.get(key, $ -> {
				// TODO is this cheap, if yes, take out from the callback?
				Set<Multikey<K, PK>> primaryKeyset = primaryCache.synchronous().asMap().keySet();
				Multikey<K, PK> newPrimaryKey = new Multikey<>(keyMapper.apply(key), key);
				if (primaryKeyset.contains(newPrimaryKey)) {
					for (Multikey<K, PK> primaryKey : primaryKeyset) {
						if (newPrimaryKey.equals(primaryKey)) {
							primaryKey.addKey(key);
							return primaryKey;
						}
					}
				}
				return newPrimaryKey;
			})
			.thenCompose(multikey -> primaryCache.get(multikey, $ -> valueLoader.apply(key)));
	}

	@Nullable
	@Override
	public PK getPrimaryKeyIfPresent(@Nonnull K key) {
		Multikey<K, PK> multikey = preCache.synchronous().getIfPresent(key);
		return multikey != null ? multikey.getPrimaryKey() : null;
	}

	@Nullable
	@Override
	public V getIfPresent(@Nonnull PK primaryKey) {
		return primaryCache.synchronous().getIfPresent(new Multikey<>(primaryKey));
	}

	static class BuilderImpl<K, PK, V> implements Builder<K, PK, V> {

		private final Caffeine<Object, Object> caffeine;

		private RemovalListener<Set<K>, V> removalListener = null;

		private Supplier<PK> voidPrimaryKeySupplier;

		private Supplier<V> voidValueSupplier;

		private Executor executor = Executors.newCachedThreadPool();

		BuilderImpl(Caffeine<Object, Object> caffeine) {
			this.caffeine = caffeine;
		}

		@Nonnull
		@Override
		public Builder<K, PK, V> removalListener(@Nonnull RemovalListener<Set<K>, V> removalListener) {
			this.removalListener = removalListener;
			return this;
		}

		@Nonnull
		@Override
		public Builder<K, PK, V> executor(@Nonnull Executor executor) {
			this.executor = executor;
			return this;
		}

		@Nonnull
		@Override
		public Builder<K, PK, V> voidPrimaryKeySupplier(@Nonnull Supplier<PK> voidPrimaryKeySupplier) {
			this.voidPrimaryKeySupplier = voidPrimaryKeySupplier;
			return this;
		}

		@Nonnull
		@Override
		public Builder<K, PK, V> voidValueSupplier(@Nonnull Supplier<V> voidValueSupplier) {
			this.voidValueSupplier = voidValueSupplier;
			return this;
		}

		@Nonnull
		@Override
		public AsyncMultikeyCache<K, PK, V> build() {
			Objects.requireNonNull(caffeine, "caffeine builder must not be null");
			Objects.requireNonNull(voidPrimaryKeySupplier, "void primary-key supplier must not be null");
			Objects.requireNonNull(voidValueSupplier, "void value supplier must not be null");
			return new CaffeineMultikeyCache<>(this);
		}
	}
}
