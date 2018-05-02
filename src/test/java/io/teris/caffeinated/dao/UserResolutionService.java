/*
 * Copyright (c) teris.io & Oleg Sklyar, 2018. All rights reserved
 */

package io.teris.caffeinated.dao;

public interface UserResolutionService {

	String resolveForApiKey(Context context, String apiKey);
}
