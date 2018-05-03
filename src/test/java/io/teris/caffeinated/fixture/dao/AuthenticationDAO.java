/*
 * Copyright (c) teris.io & Oleg Sklyar, 2018. All rights reserved
 */

package io.teris.caffeinated.fixture.dao;

import io.teris.caffeinated.fixture.entity.Session;


public interface AuthenticationDAO {

	Session authByUsername(String username, String password);

	Session authByApiKey(String apikey);
}
