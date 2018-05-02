/*
 * Copyright (c) teris.io & Oleg Sklyar, 2018. All rights reserved
 */

package io.teris.caffeinated.dao;

public interface AuthenticationService {

	Session authByUsername(Context context, String username, String password);

	Session authByApiKey(Context context, String apikey);
}
