package lu.zakaria.keycloak.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The resource-server side of the playground. Every endpoint here is served by
 * the {@code apiFilterChain}: no session, no login redirect. An unauthenticated
 * request gets {@code 401} with a {@code WWW-Authenticate: Bearer} header rather
 * than a {@code 302} to Keycloak.
 *
 * <p>Get a token to play with:
 *
 * <pre>
 * curl -s -d grant_type=password -d client_id=spring-web \
 *      -d client_secret=spring-web-secret -d username=bob -d password=bob \
 *      http://localhost:8180/realms/playground/protocol/openid-connect/token
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class ApiController {

	/** Reachable with no credentials at all -- {@code permitAll()} in the chain. */
	@GetMapping("/public")
	public Map<String, Object> publicEndpoint() {
		return Map.of("message", "No token required. Anyone can read this.");
	}

	/**
	 * Requires {@code ROLE_app-user}. The {@code Jwt} argument is the validated
	 * token: signature checked against Keycloak's JWKS, issuer matched, audience
	 * matched, expiry enforced -- all before this method was ever called.
	 */
	@GetMapping("/me")
	public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("subject", jwt.getSubject());
		body.put("preferredUsername", jwt.getClaimAsString("preferred_username"));
		body.put("email", jwt.getClaimAsString("email"));
		body.put("issuer", String.valueOf(jwt.getIssuer()));
		body.put("audience", jwt.getAudience());
		body.put("scope", jwt.getClaimAsString("scope"));
		body.put("issuedAt", String.valueOf(jwt.getIssuedAt()));
		body.put("expiresAt", String.valueOf(jwt.getExpiresAt()));
		// azp = "authorized party": which client asked for this token. Useful
		// for telling a human login apart from a machine-to-machine call.
		body.put("authorizedParty", jwt.getClaimAsString("azp"));
		body.put("grantedAuthorities", authorities(authentication));
		return body;
	}

	/** Requires {@code ROLE_app-admin}. alice gets 403 here, bob gets 200. */
	@GetMapping("/admin/report")
	public Map<String, Object> adminReport(Authentication authentication) {
		return Map.of(
				"report", "Sensitive numbers only an admin should see",
				"requestedBy", authentication.getName(),
				"grantedAuthorities", authorities(authentication));
	}

	/**
	 * Roles answer "who is the user?". Scopes answer "how much of that user's
	 * power did they delegate to this client?". They are independent checks and
	 * a well-built API often wants both.
	 */
	@GetMapping("/scoped")
	@PreAuthorize("hasAuthority('SCOPE_email')")
	public Map<String, Object> scoped(@AuthenticationPrincipal Jwt jwt) {
		return Map.of(
				"message", "The client was granted the 'email' scope for this user",
				"email", String.valueOf(jwt.getClaimAsString("email")));
	}

	private static List<String> authorities(Authentication authentication) {
		return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList();
	}

}
