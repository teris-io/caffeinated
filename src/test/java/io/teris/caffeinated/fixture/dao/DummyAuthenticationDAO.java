/*
 * Copyright (c) teris.io & Oleg Sklyar, 2018. All rights reserved
 */

package io.teris.caffeinated.fixture.dao;

import java.util.Base64;

import io.teris.caffeinated.fixture.entity.Session;


public class DummyAuthenticationDAO implements AuthenticationDAO {

	private final UserResolutionDAO userResolutionDAO;

	public long dbAccessLatencyMs = 20;

	public DummyAuthenticationDAO(UserResolutionDAO userResolutionDAO) {
		this.userResolutionDAO = userResolutionDAO;
	}

	@Override
	public void validateUsernameAuth(String username, String password) {
		try {
			Thread.sleep(dbAccessLatencyMs);
			String hash = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
			if (!DummyState.users.contains(hash)) {
				throw new IllegalArgumentException("access denied");
			}
		}
		catch (InterruptedException ex) {
			throw new IllegalStateException("operation interrupted");
		}
	}

	@Override
	public Session authByUsername(String username, String password) {
		validateUsernameAuth(username, password);
		return new Session(username);
	}

	@Override
	public void validateApiKeyAuth(String apiKey) {
		userResolutionDAO.resolveForApiKey(apiKey);
	}

	@Override
	public Session authByApiKey(String apiKey) {
		try {
			Thread.sleep(dbAccessLatencyMs);
			String username = userResolutionDAO.resolveForApiKey(apiKey);
			return new Session(username);
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("access denied");
		}
		catch (InterruptedException ex) {
			throw new IllegalStateException("operation interrupted");
		}
	}
}
