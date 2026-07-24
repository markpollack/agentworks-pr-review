package io.github.markpollack.prreview.serving;

import java.security.MessageDigest;
import java.util.HexFormat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.markpollack.workflow.spec.CanonicalJson;
import io.github.markpollack.workflow.spec.v3.WireJson;

/** Test helpers for the v3alpha serving fixtures. */
final class V3ServingFixtures {

	private V3ServingFixtures() {
	}

	/** {@code sha256:<hex>} over the RFC 8785 canonical bytes of a document (C-6). */
	static String jcsDigest(JsonNode document) throws Exception {
		MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
		byte[] canonical = CanonicalJson.canonicalize(WireJson.mapper().writeValueAsBytes(document));
		return "sha256:" + HexFormat.of().formatHex(sha256.digest(canonical));
	}

}
