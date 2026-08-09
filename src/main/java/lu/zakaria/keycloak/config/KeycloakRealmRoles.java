package lu.zakaria.keycloak.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Bridges Keycloak's role model to Spring Security's authority model.
 *
 * <p>Keycloak puts realm roles in a <em>nested</em> claim:
 *
 * <pre>
 * {
 *   "realm_access": { "roles": ["app-user", "app-admin"] }
 * }
 * </pre>
 *
 * <p>Spring Security knows nothing about that shape. Out of the box it only
 * reads the flat {@code scope} claim and prefixes each value with
 * {@code SCOPE_}. So a Keycloak user who is unmistakably an admin arrives at
 * your app with zero {@code ROLE_*} authorities and {@code hasRole("app-admin")}
 * returns false. That mismatch is the single most common "why doesn't my
 * Keycloak role work?" bug.
 *
 * <p>The {@code ROLE_} prefix is not decoration: {@code hasRole("app-admin")}
 * literally checks for an authority named {@code ROLE_app-admin}.
 * {@code hasAuthority("app-admin")} checks the unprefixed string. Pick one
 * convention and stay with it.
 */
public final class KeycloakRealmRoles {

	public static final String CLAIM = "realm_access";
	public static final String ROLE_PREFIX = "ROLE_";

	private KeycloakRealmRoles() {
	}

	/**
	 * Reads {@code realm_access.roles} out of any token's claim map. Works
	 * identically for an access token ({@code Jwt}) and an ID token
	 * ({@code OidcIdToken}) because both are just maps of claims underneath.
	 */
	public static Collection<GrantedAuthority> from(Map<String, Object> claims) {
		if (!(claims.get(CLAIM) instanceof Map<?, ?> realmAccess)) {
			return List.of();
		}
		if (!(realmAccess.get("roles") instanceof Collection<?> roles)) {
			return List.of();
		}
		return roles.stream()
			.map(String::valueOf)
			.map((role) -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + role))
			.toList();
	}

	/**
	 * Reads roles Keycloak scoped to one specific client, which live under
	 * {@code resource_access.<clientId>.roles}. Realm roles are global to the
	 * realm; client roles are namespaced per client, so two clients can each
	 * define an "admin" role without colliding.
	 */
	public static Collection<GrantedAuthority> forClient(Map<String, Object> claims, String clientId) {
		if (!(claims.get("resource_access") instanceof Map<?, ?> resourceAccess)) {
			return List.of();
		}
		if (!(resourceAccess.get(clientId) instanceof Map<?, ?> client)) {
			return List.of();
		}
		if (!(client.get("roles") instanceof Collection<?> roles)) {
			return List.of();
		}
		return roles.stream()
			.map(String::valueOf)
			.map((role) -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + role))
			.toList();
	}

}
