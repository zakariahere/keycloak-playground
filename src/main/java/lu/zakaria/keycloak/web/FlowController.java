package lu.zakaria.keycloak.web;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Front-channel inspector for lesson 001.
 *
 * <p>Clicking "Log in" hands you to Keycloak too fast to read the URL you were
 * sent to. This page builds <em>the very same</em> authorization request using
 * the very same Spring component &mdash;
 * {@link DefaultOAuth2AuthorizationRequestResolver}, the one
 * {@link OAuth2AuthorizationRequestRedirectFilter} uses &mdash; and then just
 * renders it instead of redirecting.
 *
 * <p>So this is not a mock-up of the request. It is the request.
 *
 * <p>Note that resolving one here is a side-effect-free dead end: the real flow
 * also <em>saves</em> the request (state, nonce, PKCE verifier) in the session
 * so the callback can match it later. Nothing is saved here, which is why
 * hitting this page never breaks a login in another tab.
 */
@Controller
public class FlowController {

	/** Explanations keyed by query parameter, in the order we want to show them. */
	private static final Map<String, String> WHAT_IT_DOES = Map.ofEntries(
			Map.entry("response_type",
					"Asks for a CODE, not a token. This one word is what makes it the Authorization Code flow."),
			Map.entry("client_id", "Which client is asking. Public information, safe in the URL."),
			Map.entry("scope", "How much of the user's power the client wants. openid is what makes it OIDC."),
			Map.entry("state", "Anti-CSRF. Random per request, stored in the session, must come back unchanged."),
			Map.entry("nonce", "Anti-replay for the ID token. Keycloak copies it into the token so the app can match it."),
			Map.entry("redirect_uri", "Where to send the code. Keycloak refuses any value not whitelisted on the client."),
			Map.entry("code_challenge", "PKCE. A SHA-256 hash of a secret this app kept to itself."),
			Map.entry("code_challenge_method", "S256 = the challenge is hashed. 'plain' would send the secret itself."));

	private static final List<String> SECURITY_CRITICAL = List.of("state", "nonce", "code_challenge",
			"code_challenge_method");

	private final DefaultOAuth2AuthorizationRequestResolver resolver;

	public FlowController(ClientRegistrationRepository clientRegistrations) {
		this.resolver = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrations,
				OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
	}

	@GetMapping("/flow")
	public String flow(HttpServletRequest request, Model model) {
		OAuth2AuthorizationRequest authorizationRequest = this.resolver.resolve(request, "keycloak");
		if (authorizationRequest == null) {
			model.addAttribute("error", "Resolver returned null -- no registration named 'keycloak'.");
			return "flow";
		}

		model.addAttribute("uri", authorizationRequest.getAuthorizationRequestUri());
		model.addAttribute("params", parseQuery(authorizationRequest.getAuthorizationRequestUri()));

		// PKCE keeps the verifier client-side and sends only its hash. Spring
		// stashes the verifier in the request attributes so the callback can
		// present it during the token exchange.
		Object codeVerifier = authorizationRequest.getAttributes().get("code_verifier");
		model.addAttribute("codeVerifier", codeVerifier);
		model.addAttribute("pkceApplied", codeVerifier != null);
		model.addAttribute("attributes", authorizationRequest.getAttributes());
		return "flow";
	}

	/** Splits the query string in wire order, percent-decoding each value. */
	private static List<Param> parseQuery(String uri) {
		int q = uri.indexOf('?');
		if (q < 0) {
			return List.of();
		}
		List<Param> params = new ArrayList<>();
		for (String pair : uri.substring(q + 1).split("&")) {
			int eq = pair.indexOf('=');
			String name = (eq < 0) ? pair : pair.substring(0, eq);
			String value = (eq < 0) ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
			params.add(new Param(name, value, WHAT_IT_DOES.getOrDefault(name, ""),
					SECURITY_CRITICAL.contains(name)));
		}
		return params;
	}

	public record Param(String name, String value, String explanation, boolean critical) {
	}

	/** Kept for the template: ordered view of the resolver's own attributes. */
	public static Map<String, Object> ordered(Map<String, Object> source) {
		return new LinkedHashMap<>(source);
	}

}
