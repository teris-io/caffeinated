/*
 * Copyright (c) 2018 Profidata AG. All rights reserved
 */

package io.teris.caffeinated;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;


class Multikey<K, PK> {

	private final PK primaryKey;

	private final Set<K> keys = ConcurrentHashMap.newKeySet();

	Multikey(@Nonnull PK primaryKey, @Nonnull K key) {
		this.primaryKey = primaryKey;
		this.keys.add(key);
	}

	Multikey(@Nonnull PK primaryKey) {
		this.primaryKey = primaryKey;
	}

	@Nonnull
	PK getPrimaryKey() {
		return primaryKey;
	}

	@Nonnull
	Set<K> getKeys() {
		return keys;
	}

	@Nonnull
	Multikey<K, PK> addKey(@Nonnull K key) {
		keys.add(key);
		return this;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		return Objects.equals(primaryKey, ((Multikey<?, ?>) other).primaryKey);
	}

	@Override
	public int hashCode() {
		return Objects.hash(primaryKey);
	}
}
