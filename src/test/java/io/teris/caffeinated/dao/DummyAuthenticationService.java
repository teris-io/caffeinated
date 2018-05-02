/*
 * Copyright (c) teris.io & Oleg Sklyar, 2018. All rights reserved
 */

package io.teris.caffeinated.dao;

import java.util.Base64;


public class DummyAuthenticationService implements AuthenticationService {

	private final UserResolutionService userResolutionService;

	public long dbAccessLatencyMs = 100;

	public DummyAuthenticationService(UserResolutionService userResolutionService) {
		this.userResolutionService = userResolutionService;
	}

	@Override
	public Session authByUsername(Context context, String username, String password) {
		try {
			Thread.sleep(dbAccessLatencyMs);
			String hash = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
			if (!DummyState.users.contains(hash)) {
				throw new IllegalArgumentException("access denied");
			}
			return new Session(username);
		}
		catch (InterruptedException ex) {
			throw new IllegalStateException("operation interrupted");
		}
	}

	@Override
	public Session authByApiKey(Context context, String apiKey) {
		try {
			Thread.sleep(dbAccessLatencyMs);
			String username = userResolutionService.resolveForApiKey(context, apiKey);
			return new Session(username);
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("access denied");
		}
		catch (InterruptedException ex) {
			throw new IllegalStateException("operation interrupted");
		}
	}
}
