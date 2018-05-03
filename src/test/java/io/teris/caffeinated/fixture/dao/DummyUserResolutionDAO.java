/*
 * Copyright (c) teris.io & Oleg Sklyar, 2018. All rights reserved
 */

package io.teris.caffeinated.fixture.dao;

import java.util.Base64;


public class DummyUserResolutionDAO implements UserResolutionDAO {

	public long dbAccessLatencyMs = 20;

	@Override
	public String resolveForApiKey(String apiKey) {
		try {
			Thread.sleep(dbAccessLatencyMs);
			if (!DummyState.keys.contains(apiKey)) {
				throw new IllegalArgumentException("no user for API key");
			}
			return new String(Base64.getDecoder().decode(apiKey)).split(":")[0];
		}
		catch (InterruptedException ex) {
			throw new IllegalStateException("operation interrupted");
		}
	}
}
