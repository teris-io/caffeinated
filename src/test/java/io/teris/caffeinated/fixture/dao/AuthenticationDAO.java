/*
 * Copyright (c) teris.io & Oleg Sklyar, 2018. All rights reserved
 */

package io.teris.caffeinated.fixture.dao;

import io.teris.caffeinated.fixture.entity.Session;


public interface AuthenticationDAO {

	void validateUsernameAuth(String username, String password);

	Session authByUsername(String username, String password);

	void validateApiKeyAuth(String apiKey);

	Session authByApiKey(String apikey);
}
