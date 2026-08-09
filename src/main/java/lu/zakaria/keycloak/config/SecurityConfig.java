package lu.zakaria.keycloak.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

/**
 * This one application plays BOTH OAuth2 roles at once, which is unusual but
 * makes the contrast obvious:
 *
 * <ul>
 * <li>{@code /api/**} is a <b>resource server</b>: stateless, no cookies, no
 * login page. It expects {@code Authorization: Bearer <jwt>} and validates the
 * signature locally against Keycloak's published public keys.</li>
 * <li>Everything else is an <b>OAuth2 client</b>: stateful, cookie session,
 * redirects anonymous users to Keycloak's login page.</li>
 * </ul>
 *
 * <p>Two {@link SecurityFilterChain} beans make that split. Spring evaluates
 * them in {@link Order} and picks the <b>first</b> one whose
 * {@code securityMatcher} matches -- it never falls through to the second. A
 * chain with no {@code securityMatcher} matches everything, so it must be last.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // turns on @PreAuthorize / @PostAuthorize on beans
public class SecurityConfig {

	// ---------------------------------------------------------------------
	// Chain 1: the API. Bearer tokens only.
	// ---------------------------------------------------------------------
	@Bean
	@Order(1)
	SecurityFilterChain apiFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtConverter) throws Exception {
		http
			.securityMatcher("/api/**")
			.authorizeHttpRequests((authorize) -> authorize
				.requestMatchers("/api/public").permitAll()
				// hasRole("app-admin") looks for the authority "ROLE_app-admin".
				// That prefix is added by KeycloakRealmRoles, not by Keycloak.
				.requestMatchers("/api/admin/**").hasRole("app-admin")
				.requestMatchers("/api/me").hasRole("app-user")
				.anyRequest().authenticated())
			.oauth2ResourceServer((oauth2) -> oauth2
				.jwt((jwt) -> jwt.jwtAuthenticationConverter(jwtConverter)))
			// A bearer-token API must not create or trust an HTTP session: the
			// token is the entire credential, presented fresh on every request.
			.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// CSRF protects cookie-based auth. There is no cookie here -- an
			// attacker's page cannot make the browser attach a bearer header.
			.csrf((csrf) -> csrf.disable());
		return http.build();
	}

	// ---------------------------------------------------------------------
	// Chain 2 (default): the browser app. Redirect-to-Keycloak login.
	// ---------------------------------------------------------------------
	@Bean
	SecurityFilterChain webFilterChain(HttpSecurity http, ClientRegistrationRepository clients) throws Exception {
		http
			.authorizeHttpRequests((authorize) -> authorize
				.requestMatchers("/", "/error", "/css/**", "/actuator/**").permitAll()
				.requestMatchers("/admin/**").hasRole("app-admin")
				.anyRequest().authenticated())
			// One line = the whole Authorization Code flow: the redirect to
			// Keycloak, the /login/oauth2/code/* callback, the code-for-token
			// exchange, ID token validation, and the authenticated session.
			.oauth2Login(Customizer.withDefaults())
			.logout((logout) -> logout
				.logoutSuccessHandler(oidcLogoutSuccessHandler(clients)));
		return http.build();
	}

	/**
	 * Killing the local session is only half a logout: Keycloak still holds an
	 * SSO session, so the next login would silently succeed with no password
	 * prompt. RP-Initiated Logout redirects the browser to Keycloak's
	 * {@code end_session_endpoint} to end the session at the source too.
	 */
	private LogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository clients) {
		OidcClientInitiatedLogoutSuccessHandler handler = new OidcClientInitiatedLogoutSuccessHandler(clients);
		// Must be whitelisted as a post-logout redirect URI on the Keycloak client.
		handler.setPostLogoutRedirectUri("{baseUrl}/");
		return handler;
	}

	// ---------------------------------------------------------------------
	// Authority mapping -- needed twice, because the two chains build their
	// Authentication objects along completely different code paths.
	// ---------------------------------------------------------------------

	/**
	 * Resource server path: {@code Jwt} -> {@code JwtAuthenticationToken}.
	 *
	 * <p>The default converter only emits {@code SCOPE_*} authorities from the
	 * {@code scope} claim. We keep those and add Keycloak's realm roles.
	 */
	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter((jwt) -> {
			Set<GrantedAuthority> authorities = new LinkedHashSet<>();
			Collection<GrantedAuthority> fromScopes = scopes.convert(jwt);
			if (fromScopes != null) {
				authorities.addAll(fromScopes);
			}
			authorities.addAll(KeycloakRealmRoles.from(jwt.getClaims()));
			return authorities;
		});
		// Otherwise Authentication.getName() is the `sub` UUID.
		converter.setPrincipalClaimName("preferred_username");
		return converter;
	}

	/**
	 * Login path: {@code OidcUser} -> {@code OAuth2AuthenticationToken}.
	 *
	 * <p>Keycloak does not put realm roles in the ID token by default -- they go
	 * in the access token, which {@code oauth2Login} never inspects. The realm
	 * import adds a {@code realm-roles-into-id-token} protocol mapper on the
	 * {@code spring-web} client specifically so this hook has something to read.
	 */
	@Bean
	GrantedAuthoritiesMapper userAuthoritiesMapper() {
		return (authorities) -> {
			Set<GrantedAuthority> mapped = new LinkedHashSet<>(authorities);
			for (GrantedAuthority authority : authorities) {
				if (authority instanceof OidcUserAuthority oidc) {
					mapped.addAll(KeycloakRealmRoles.from(oidc.getIdToken().getClaims()));
				}
			}
			return mapped;
		};
	}

}
