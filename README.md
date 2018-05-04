# caffeinated -- tiny multi-key caching layer based on `caffeine`  

[![Build Status](https://travis-ci.org/teris-io/caffeinated.svg?branch=master)](https://travis-ci.org/teris-io/caffeinated)
[![Code Coverage](https://img.shields.io/codecov/c/github/teris-io/caffeinated.svg)](https://codecov.io/gh/teris-io/caffeinated)

The `caffeinated` library provides an asynchronous loading cache for a caching problem 
where multiple keys point to the same cached value (via same shared derived key) and are
treated as a single entity for the purpose of expiration or eviction. In effect, it
implements a caching cascade of `K1,K2,K3->DK->V` based on key mappers (`Function<K,DK>`)
and value loaders (`BiFunction<K,DK,V`).

Given a cache instance one can retrieve values via the `get` methods, that exist in two 
variants: 


* with default loaders registered on the cached during construction (none are registered
by default and the future will complete exceptionally):


    CompletableFuture<V> get(K key) 

* with explicit loaders:


    CompletableFuture<V> get(K key, Function<K,DK> keyMapper, BiFunction<K,DK,V> valueLoader)


The underlying cache is based on [AsyncLoadingCache][1] from [`caffeine`][2] and can be configured
via the [`Caffeine` builder][3], e.g.

    Caffeine caffeine = Caffeine.newBuilder()
      .expireAfterAccess(5, TimeUnit.SECONDS);

    AsyncMultikeyCache.<String, String, Session> sessionStore =
      AsyncMultikeyCache.<String, String, Session>newBuilder(caffeine)
    	  .removalListener((keys, session, reason) -> session.expire())
    		.buildAsync();

For this sort of session store the cache usage may look like this:


    private final AuthenticationDAO authDao;

    @Override
    public CompletableFuture<String> authenticate(String username, String password) {
      String key = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
      return sessionStore
        .get(key, $ -> {
          // make sure users with wrong passwords cannot hijack sessions of correctly 
          // authenticated users as shared key is just username
          authDao.validateUsernameAuth(username, password);
          return username;
        }, ($, $$) -> authDao.authByUsername(username, password))
        .thenCompose(session -> CompletableFuture.completedFuture(session.sessionId));
    }

A complete example can be found in the [SessionStoreExample][4] test class.


### License and copyright

	Copyright (c) 2018. Oleg Sklyar and teris.io. All rights reserved. MIT license applies

[1]: https://github.com/ben-manes/caffeine/blob/master/caffeine/src/main/java/com/github/benmanes/caffeine/cache/AsyncLoadingCache.java
[2]: https://github.com/ben-manes/caffeine
[3]: https://github.com/ben-manes/caffeine/blob/master/caffeine/src/main/java/com/github/benmanes/caffeine/cache/Caffeine.java
[4]: https://github.com/teris-io/caffeinated/blob/master/src/test/java/io/teris/caffeinated/SessionStoreExample.java

