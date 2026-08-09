package lu.zakaria.keycloak.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Decodes a compact JWT for display -- <b>without verifying anything</b>.
 *
 * <p>That caveat is the lesson. A JWT is three base64url segments joined by
 * dots: {@code header.payload.signature}. The first two are plain, unencrypted
 * JSON that anybody holding the token can read with no key at all. Only the
 * third segment proves the first two were not tampered with.
 *
 * <p>Two consequences worth internalising:
 * <ol>
 * <li>Never put anything secret in a JWT. It is signed, not encrypted.</li>
 * <li>Never trust claims you decoded this way. Reading is not validating --
 * real validation happens in {@code JwtDecoder}, which checks the signature
 * against Keycloak's JWKS plus expiry, issuer and audience.</li>
 * </ol>
 */
@Component
public class JwtPeek {

	private final ObjectMapper objectMapper;

	public JwtPeek(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public Decoded peek(String compactJwt) {
		if (compactJwt == null || compactJwt.isBlank()) {
			return new Decoded("", "(no token)", "", 0);
		}
		String[] parts = compactJwt.split("\\.");
		if (parts.length < 2) {
			// Keycloak can also issue opaque tokens; those are just a random
			// string and must be introspected server-side instead of decoded.
			return new Decoded("", "(opaque token -- not a JWT)", "", compactJwt.length());
		}
		String signature = (parts.length > 2) ? parts[2] : "";
		return new Decoded(prettyPrint(parts[0]), prettyPrint(parts[1]), signature, compactJwt.length());
	}

	private String prettyPrint(String base64UrlSegment) {
		try {
			byte[] decoded = Base64.getUrlDecoder().decode(base64UrlSegment);
			String json = new String(decoded, StandardCharsets.UTF_8);
			return this.objectMapper.writerWithDefaultPrettyPrinter()
				.writeValueAsString(this.objectMapper.readTree(json));
		}
		catch (RuntimeException ex) {
			return "(could not decode: " + ex.getMessage() + ")";
		}
	}

	/**
	 * @param header the JOSE header -- says which algorithm signed the token
	 * ({@code alg}) and which key did it ({@code kid})
	 * @param payload the claims set -- who the token is about, who issued it,
	 * who it is for, and when it dies
	 * @param signature left raw on purpose; it is bytes, not text
	 * @param length total characters on the wire, sent on every single request
	 */
	public record Decoded(String header, String payload, String signature, int length) {
	}

}
