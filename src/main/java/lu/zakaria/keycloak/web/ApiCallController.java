package lu.zakaria.keycloak.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;

/**
 * Demonstrates the Backend-For-Frontend (BFF) pattern.
 *
 * <p>The browser holds nothing but an ordinary session cookie. When the page
 * needs data from the API, the <em>server</em> pulls the access token out of
 * its own store and attaches it to an outgoing call. Tokens never touch
 * JavaScript, so an XSS bug cannot steal one.
 *
 * <p>This is the currently recommended shape for server-rendered apps. The
 * older advice -- ship the access token to the SPA and let {@code fetch()}
 * carry it -- puts a bearer credential somewhere every script on the page can
 * read.
 */
@Controller
public class ApiCallController {

	private final RestClient restClient;

	private final OAuth2AuthorizedClientService authorizedClients;

	public ApiCallController(RestClient.Builder restClientBuilder, OAuth2AuthorizedClientService authorizedClients) {
		this.restClient = restClientBuilder.build();
		this.authorizedClients = authorizedClients;
	}

	@GetMapping("/playground/call")
	public String call(@RequestParam(defaultValue = "/api/me") String path, Authentication authentication,
			Model model) {

		OAuth2AuthorizedClient client = this.authorizedClients.loadAuthorizedClient("keycloak",
				authentication.getName());

		model.addAttribute("path", path);
		if (client == null) {
			model.addAttribute("status", "n/a");
			model.addAttribute("body", "No authorized client found -- log in first.");
			return "call";
		}

		String bearer = client.getAccessToken().getTokenValue();
		// A genuine HTTP round trip back into this same JVM, so the request
		// really does pass through the resource-server filter chain.
		ResponseEntity<String> response = this.restClient.get()
			.uri("http://localhost:8090" + path)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
			.retrieve()
			// Without this, 4xx/5xx would throw instead of being shown -- and
			// seeing the 401 and 403 is exactly the point of this page.
			.onStatus((status) -> true, (request, res) -> {
			})
			.toEntity(String.class);

		model.addAttribute("status", response.getStatusCode().value());
		model.addAttribute("body", (response.getBody() != null) ? response.getBody() : "(empty body)");
		model.addAttribute("wwwAuthenticate", response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
		return "call";
	}

}
