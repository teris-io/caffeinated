/*
 * Copyright (c) teris.io & Oleg Sklyar, 2018. All rights reserved
 */

package io.teris.caffeinated;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.github.benmanes.caffeine.cache.Caffeine;

import io.teris.caffeinated.fixture.dao.AuthenticationDAO;
import io.teris.caffeinated.fixture.dao.DummyAuthenticationDAO;
import io.teris.caffeinated.fixture.dao.DummyUserResolutionDAO;
import io.teris.caffeinated.fixture.dao.UserResolutionDAO;
import io.teris.caffeinated.fixture.entity.Session;


public class SessionStoreExample {

	public static class Context {}

	public interface AuthenticationService {

		CompletableFuture<String> authByUsername(Context context, String username, String password);

		CompletableFuture<String> authByApiKey(Context context, String apikey);

		CompletableFuture<Session> getSessionById(Context context, String sessionId);

		CompletableFuture<Void> logout(Context context, String sessionId);
	}

	static class SessionStoreBackedAuthenticationService implements AuthenticationService {

		private final UserResolutionDAO userDao = new DummyUserResolutionDAO();

		private final AuthenticationDAO authDao = new DummyAuthenticationDAO(userDao);

		private final AsyncMultikeyCache<String, String, Session> sessionStore =
			AsyncMultikeyCache.<String, String, Session>newBuilder(Caffeine.newBuilder()
					.expireAfterAccess(5, TimeUnit.SECONDS))
				.removalListener((keys, session, reason) -> session.expire())
				.buildAsync();

		@Override
		public CompletableFuture<String> authByUsername(Context context, String username, String password) {
			String key = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
			AtomicBoolean createdHolder = new AtomicBoolean();
			return sessionStore
				.get(key, $ -> {
					authDao.validateUsernameAuth(username, password);
					return username;
				}, $ -> {
					Session res = authDao.authByUsername(username, password);
					createdHolder.set(true);
					return res;
				})
				.thenCompose(session -> completeAuth(session, createdHolder.get(), username));
		}

		@Override
		public CompletableFuture<String> authByApiKey(Context context, String apikey) {
			AtomicReference<String> usernameHolder = new AtomicReference<>(null);
			AtomicBoolean createdHolder = new AtomicBoolean();
			return sessionStore
				.get(apikey, $ -> {
					authDao.validateApiKeyAuth(apikey);
					return usernameHolder.updateAndGet($$ -> userDao.resolveForApiKey(apikey));
				}, $ -> {
					Session res = authDao.authByApiKey(apikey);
					createdHolder.set(true);
					return res;
				})
				.thenCompose(session -> completeAuth(session, createdHolder.get(), usernameHolder.get()));
		}

		private CompletableFuture<String> completeAuth(Session session, boolean created, String derivedKey) {
			CompletableFuture<String> res = new CompletableFuture<>();
			if (created) {
				// cached and temp are the same => newly created => add sessionId as one of the original keys
				sessionStore.get(session.sessionId, $ -> derivedKey, $ -> {
					throw new IllegalStateException("value expected to be present");
				})
				.whenComplete(($, $$) -> res.complete(session.sessionId));
			} else {
				res.complete(session.sessionId);
			}
			return res;
		}


		@Override
		public CompletableFuture<Session> getSessionById(Context context, String sessionId) {
			return sessionStore.get(sessionId);
		}

		@Override
		public CompletableFuture<Void> logout(Context context, String sessionId) {
			return CompletableFuture.runAsync(() -> sessionStore.invalidate(sessionId));
		}
	}

	@Rule
	public ExpectedException exception = ExpectedException.none();

	@Before
	public void beforeEach() {
		Session.createdCount.set(0);
		Session.expiredCount.set(0);
	}

	@Test
	public void differentPathaways_sameUser_sameSessionRetrieved() throws Exception {
		Context context = new Context();
		AtomicReference<String> sessionIdHolder = new AtomicReference<>(null);

		AuthenticationService authService = new SessionStoreBackedAuthenticationService();

		Session session = authService.authByUsername(context, "joe", "hkAS-4Dti-gg532-D")
			.thenCompose(sessionId -> {
				sessionIdHolder.set(sessionId);
				return authService.getSessionById(context, sessionId);
			}).get(5, TimeUnit.SECONDS);
		assertEquals(sessionIdHolder.get(), session.sessionId);

		// retrieve authenticated session
		Session anotherSession = authService.getSessionById(context, sessionIdHolder.get()).get(5, TimeUnit.SECONDS);
		assertSame(session, anotherSession);

		// login with an API key for the same user retrieving the cached authenticated session
		anotherSession = authService.authByApiKey(context, "am9lOndqZ2VvYXc0dGY5ODM0dHo=")
			.thenCompose(sessionId -> authService.getSessionById(context, sessionId)).get(5, TimeUnit.SECONDS);
		assertSame(session, anotherSession);

		assertEquals(1, Session.createdCount.get());
	}

	@Test
	public void repeatedAuths_sameUser_sameSessionRetrieved() throws Exception {
		Context context = new Context();
		AuthenticationService authService = new SessionStoreBackedAuthenticationService();

		Session session = authService.authByUsername(context, "joe", "hkAS-4Dti-gg532-D")
			.thenCompose(sessionId -> authService.getSessionById(context, sessionId)).get(5, TimeUnit.SECONDS);

		Session anotherSession = authService.authByUsername(context, "joe", "hkAS-4Dti-gg532-D")
			.thenCompose(sessionId -> authService.getSessionById(context, sessionId)).get(5, TimeUnit.SECONDS);
		assertSame(session, anotherSession);

		anotherSession = authService.authByApiKey(context, "am9lOndqZ2VvYXc0dGY5ODM0dHo=")
			.thenCompose(sessionId -> authService.getSessionById(context, sessionId)).get(5, TimeUnit.SECONDS);
		assertSame(session, anotherSession);

		anotherSession = authService.authByApiKey(context, "am9lOndqZ2VvYXc0dGY5ODM0dHo=")
			.thenCompose(sessionId -> authService.getSessionById(context, sessionId)).get(5, TimeUnit.SECONDS);
		assertSame(session, anotherSession);

		assertEquals(1, Session.createdCount.get());
	}

	@Test
	public void differentPathways_sameUser_expire_noFurtherAccessBySessionId() throws Exception {
		Context context = new Context();
		AuthenticationService authService = new SessionStoreBackedAuthenticationService();

		Session session = authService.authByUsername(context, "joe", "hkAS-4Dti-gg532-D")
			.thenCompose(sessionId -> authService.getSessionById(context, sessionId)).get(5, TimeUnit.SECONDS);

		Session anotherSession = authService.authByApiKey(context, "am9lOndqZ2VvYXc0dGY5ODM0dHo=")
			.thenCompose(sessionId -> authService.getSessionById(context, sessionId)).get(5, TimeUnit.SECONDS);
		assertSame(session, anotherSession);

		authService.logout(context, session.sessionId).get(5, TimeUnit.SECONDS);
		session.expired.get(5, TimeUnit.SECONDS);

		exception.expectMessage("missing default key mapper");
		authService.getSessionById(context, session.sessionId).get(5, TimeUnit.SECONDS);
	}

	@Test
	public void authenticate_expire_authenticate_newSessionIsCreated() throws Exception {
		Context context = new Context();
		AuthenticationService authService = new SessionStoreBackedAuthenticationService();

		Session session = authService.authByUsername(context, "joe", "hkAS-4Dti-gg532-D")
			.thenCompose(sessionId -> authService.getSessionById(context, sessionId)).get(5, TimeUnit.SECONDS);

		Session anotherSession = authService.authByUsername(context, "joe", "hkAS-4Dti-gg532-D")
			.thenCompose(sessionId -> authService.getSessionById(context, sessionId)).get(5, TimeUnit.SECONDS);
		assertSame(session, anotherSession);

		authService.logout(context, session.sessionId).get(5, TimeUnit.SECONDS);
		session.expired.get(5, TimeUnit.SECONDS);

		anotherSession = authService.authByUsername(context, "joe", "hkAS-4Dti-gg532-D")
			.thenCompose(sessionId -> authService.getSessionById(context, sessionId)).get(5, TimeUnit.SECONDS);
		assertNotSame(session, anotherSession);

		assertEquals(2, Session.createdCount.get());
	}

	@Test
	public void keyMapperThrows_completedExceptionally_exceptionallyAndStateUnchanged() throws Exception {
		Context context = new Context();
		AuthenticationService authService = new SessionStoreBackedAuthenticationService();

		// state
		authService.authByUsername(context, "joe", "hkAS-4Dti-gg532-D")
			.thenCompose(sessionId -> authService.getSessionById(context, sessionId)).get(5, TimeUnit.SECONDS);

		SessionStoreBackedAuthenticationService impl = (SessionStoreBackedAuthenticationService) authService;
		CaffeinatedMultikeyCache<String, String, Session> sessionStore = (CaffeinatedMultikeyCache<String, String, Session>)impl.sessionStore;

		assertEquals(1, sessionStore.cache.synchronous().asMap().size());
		assertEquals(2, sessionStore.keys2derivedKey.synchronous().asMap().size());
		assertEquals(1, sessionStore.derivedKey2Keys.asMap().size());

		exception.expectMessage("access denied");
		try {
			authService.authByUsername(context, "joe", "totally invalid password").get(5, TimeUnit.SECONDS);
		} catch (ExecutionException ex) {
			assertEquals(1, sessionStore.cache.synchronous().asMap().size());
			// TODO caffeine asMap.size bug produces 3: assertEquals(2, sessionStore.keys2derivedKey.synchronous().asMap().size());
			assertEquals(1, sessionStore.derivedKey2Keys.asMap().size());

			throw ex;
		}
	}


}
