/*
 * Copyright (c) teris.io & Oleg Sklyar, 2018. All rights reserved
 */

package io.teris.caffeinated;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;


/**
 * A wrapper structure for derived keys. It uses the derived key value for equality and
 * hashing while can be mutated with respect to original keys that it holds.
 */
class Multikey<K, DK> {

	private final DK derivedKey;

	private final Set<K> keys = ConcurrentHashMap.newKeySet();

	Multikey(@Nonnull DK derivedKey, @Nonnull K key) {
		this.derivedKey = derivedKey;
		this.keys.add(key);
	}

	Multikey(@Nonnull DK derivedKey) {
		this.derivedKey = derivedKey;
	}

	@Nonnull
	DK getDerivedKey() {
		return derivedKey;
	}

	@Nonnull
	Set<K> getKeys() {
		return keys;
	}

	@Nonnull
	Multikey<K, DK> addKey(@Nonnull K key) {
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
		// it is essential that only the derivedKey is used for hashing and equality
		// the equality must be guaranteed even if original keys are mutated
		return Objects.equals(derivedKey, ((Multikey<?, ?>) other).derivedKey);
	}

	@Override
	public int hashCode() {
		// it is essential that only the derivedKey is used for hashing and equality
		// the equality must be guaranteed even if original keys are mutated
		return Objects.hash(derivedKey);
	}
}
