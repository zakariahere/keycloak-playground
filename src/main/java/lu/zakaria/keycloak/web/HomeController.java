package lu.zakaria.keycloak.web;

import java.time.Duration;
import java.time.Instant;

import lu.zakaria.keycloak.config.JwtPeek;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

	private final OAuth2AuthorizedClientService authorizedClients;

	private final JwtPeek jwtPeek;

	public HomeController(OAuth2AuthorizedClientService authorizedClients, JwtPeek jwtPeek) {
		this.authorizedClients = authorizedClients;
		this.jwtPeek = jwtPeek;
	}

	@GetMapping("/")
	public String index() {
		return "index";
	}

	/**
	 * {@code OidcUser} is what {@code oauth2Login} builds for you. It is backed
	 * by the ID token: a statement from Keycloak saying "this person
	 * authenticated, here is who they are". Note what it is NOT -- it is not a
	 * credential for calling other services. That is the access token's job.
	 */
	@GetMapping("/profile")
	public String profile(@AuthenticationPrincipal OidcUser user, Authentication authentication, Model model) {
		model.addAttribute("user", user);
		model.addAttribute("authorities", authentication.getAuthorities());
		model.addAttribute("authType", authentication.getClass().getSimpleName());
		return "profile";
	}

	/**
	 * The token inspector: shows the ID token and the access token side by side
	 * so the difference stops being abstract.
	 */
	@GetMapping("/tokens")
	public String tokens(@AuthenticationPrincipal OidcUser user, Authentication authentication, Model model) {
		// Spring stashed the tokens here at the end of the login flow, keyed by
		// (registrationId, username). In production you swap the default
		// in-memory store for a JDBC one so tokens survive a restart.
		OAuth2AuthorizedClient client = this.authorizedClients.loadAuthorizedClient("keycloak",
				authentication.getName());

		model.addAttribute("idToken", this.jwtPeek.peek(user.getIdToken().getTokenValue()));
		model.addAttribute("idTokenRaw", user.getIdToken().getTokenValue());

		if (client != null) {
			OAuth2AccessToken accessToken = client.getAccessToken();
			model.addAttribute("accessToken", this.jwtPeek.peek(accessToken.getTokenValue()));
			model.addAttribute("accessTokenRaw", accessToken.getTokenValue());
			model.addAttribute("accessTokenScopes", accessToken.getScopes());
			model.addAttribute("accessTokenExpiresIn", secondsUntil(accessToken.getExpiresAt()));

			OAuth2RefreshToken refreshToken = client.getRefreshToken();
			model.addAttribute("hasRefreshToken", refreshToken != null);
			// The refresh token is the long-lived one. It never leaves the
			// server and is the reason the access token can be short-lived.
			model.addAttribute("refreshTokenPrefix",
					(refreshToken != null) ? abbreviate(refreshToken.getTokenValue()) : null);
		}
		model.addAttribute("idTokenExpiresIn", secondsUntil(user.getIdToken().getExpiresAt()));
		return "tokens";
	}

	@GetMapping("/admin")
	public String admin(Authentication authentication, Model model) {
		model.addAttribute("authorities", authentication.getAuthorities());
		return "admin";
	}

	private static Long secondsUntil(Instant instant) {
		return (instant != null) ? Duration.between(Instant.now(), instant).toSeconds() : null;
	}

	private static String abbreviate(String value) {
		return (value.length() <= 24) ? value : value.substring(0, 24) + "...";
	}

}
