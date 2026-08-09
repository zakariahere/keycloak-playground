# 001 — The Authorization Code flow

> Branch: `001_authorization_code_flow` · cut from `main`
>
> Concept 0 (on `main`) established *what* your app ends up holding: a scoped,
> expiring token instead of a password. This lesson is *how* it gets one.

## The artefact

A new page, **http://localhost:8090/flow**, backed by
[`FlowController`](../../src/main/java/lu/zakaria/keycloak/web/FlowController.java).

Clicking "Log in" bounces you to Keycloak far too fast to read the URL. `/flow`
builds **the same authorization request, with the same Spring component**
(`DefaultOAuth2AuthorizationRequestResolver` — the one
`OAuth2AuthorizationRequestRedirectFilter` uses internally) and renders it
instead of redirecting.

It is not a mock-up of the request. It *is* the request.

It's deliberately `permitAll()`, because the whole point is to read it *before*
you have logged in.

## What actually goes on the wire

Captured from a real render:

```
http://localhost:8180/realms/playground/protocol/openid-connect/auth
  ?response_type=code
  &client_id=spring-web
  &scope=openid profile email
  &state=Ca1CyemojepvmjkZpldnbyVoQhecHcuU0taQQFOhJJI=
  &redirect_uri=http://localhost:8090/login/oauth2/code/keycloak
  &nonce=FjT8VLCv2YbJ537FN_RzC1Qb_X25Ll2OtyQn08vRgG0
  &code_challenge=83H_-FF6O2zoOBjuqLWUO4O-P97Rz25_YcE_Dn-dixQ
  &code_challenge_method=S256
```

| Parameter | What it is doing |
| --------- | ---------------- |
| `response_type=code` | Asks for a **code**, not a token. This single word is what makes it the Authorization Code flow rather than the (deprecated) implicit flow. |
| `client_id` | Which client is asking. Public info — safe in a URL. |
| `scope` | How much of the user's power is being requested. `openid` is what upgrades OAuth2 into OIDC and gets you an ID token. |
| `state` | Anti-CSRF. Random per request, stored in the session, must come back unchanged. |
| `nonce` | Anti-replay for the ID token. Keycloak copies it into the token so the app can prove the token answers *this* request. |
| `redirect_uri` | Where to send the code. Keycloak rejects anything not whitelisted on the client. |
| `code_challenge` | PKCE. A SHA-256 hash of a secret the app kept to itself. |
| `code_challenge_method` | `S256` = hashed. `plain` would put the secret itself in the URL. |

Every one of those is visible in the address bar, in proxy logs, and in browser
history. **None of it is secret** — and that is precisely why none of it is
enough to obtain a token.

## Finding: PKCE is on by default, and we did not ask for it

Spring Security ships `OAuth2AuthorizationRequestCustomizers.withPkce()` as an
explicit opt-in, and most tutorials tell you to wire it up for confidential
clients. **We never did**, yet `code_challenge_method=S256` is in the request
above.

So on Spring Security 7.1 the default resolver applies PKCE to confidential
clients too. That is worth knowing in both directions: you get the protection
for free, and any tutorial telling you to add `withPkce()` is describing an
older version.

This mattered here. The `spring-web` client sets
`"pkce.code.challenge.method": "S256"`
([playground-realm.json:89](../../docker/keycloak/import/playground-realm.json#L89)),
which makes Keycloak **require** PKCE. Had Spring not sent a challenge, every
login would have failed. It didn't, which is the empirical proof.

### The verifier never leaves the server

The resolver generated a 128-character `code_verifier` and kept it. Only its
hash went into the URL. Verified directly:

```
verifier   665T4Ozw1ikVfX9bPPbyDYAO4--ipP1j3qNTiyZq-jqw…  (128 chars; RFC 7636 allows 43–128)
sha256 → base64url, '=' stripped
computed   83H_-FF6O2zoOBjuqLWUO4O-P97Rz25_YcE_Dn-dixQ
in the URL 83H_-FF6O2zoOBjuqLWUO4O-P97Rz25_YcE_Dn-dixQ   ← match
```

So: steal the entire URL, steal the code out of the redirect, and you still
cannot exchange it. The token request must present the verifier whose SHA-256
matches the challenge, and that value was never transmitted. PKCE binds the code
to the client that requested it **without any shared secret** — which is why it
works for public clients that cannot hold one.

## The two channels

| | Front channel | Back channel |
| --- | --- | --- |
| Carries | The authorization request, then the code | The code → token exchange |
| Travels via | Browser redirects | Direct server-to-server HTTP |
| Visible to the user | Yes — it's the address bar | No |
| Carries secrets | Never | Client secret **and** PKCE verifier |

The code crosses the exposed channel; redeeming it requires two things that
never did. An attacker needs both halves.

## Exercises

1. **Reload `/flow` a few times.** `state`, `nonce` and `code_challenge` change
   every single time. A value that repeated would be a replay waiting to happen.

2. **Tamper with the redirect URI.** Copy the URL, change `redirect_uri` to
   `http://evil.test/steal`, and open it. Keycloak answers **400** with
   "Invalid parameter: redirect_uri" — before rendering a login form at all
   (the untampered URL returns 302 to the login page). The whitelist at
   [playground-realm.json:85](../../docker/keycloak/import/playground-realm.json#L85)
   is what stops an open redirector from becoming a token thief.

3. **Watch the code get spent.** Log in normally, then look at the address bar
   on the way back: `…/login/oauth2/code/keycloak?code=…&state=…`. Replay that
   exact URL in a new tab. It fails — the code is single-use, and `state` no
   longer matches anything in the session.

4. **Downgrade the challenge method.** Set `pkce.code.challenge.method` to
   `plain` in the realm JSON, `docker compose down -v`, restart, and re-read
   `/flow`. Watch the verifier itself appear in the URL. That is what PKCE looks
   like when it is doing nothing.

## Where the code lives

| Thing | Where |
| ----- | ----- |
| The inspector | [`FlowController.java`](../../src/main/java/lu/zakaria/keycloak/web/FlowController.java) |
| The page | [`flow.html`](../../src/main/resources/templates/flow.html) |
| `/flow` made anonymous | [`SecurityConfig.java`](../../src/main/java/lu/zakaria/keycloak/config/SecurityConfig.java) — added to the `permitAll()` list |
| The real flow, one line | `SecurityConfig.java` — `.oauth2Login(Customizer.withDefaults())` |
| PKCE required by Keycloak | [`playground-realm.json:89`](../../docker/keycloak/import/playground-realm.json#L89) |
| Redirect URI whitelist | [`playground-realm.json:85`](../../docker/keycloak/import/playground-realm.json#L85) |

## Takeaway

`response_type=code` buys you one thing: **the credential never crosses the
browser.** Only a single-use code does, and it is worthless without the client
secret (server-side) plus the PKCE verifier (also server-side, never
transmitted). Everything else in the request — `state`, `nonce`, the redirect
whitelist — exists to stop someone splicing *their* request into *your* session.
