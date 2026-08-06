package com.web.newstech.ingest;

import lombok.experimental.UtilityClass;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@UtilityClass
public class ContentHasher {

	private static final String[] TRACKING_PARAM_PREFIXES = {
			"utm_", "fbclid", "gclid", "mc_cid", "mc_eid", "ref", "source", "at_medium", "at_campaign"
	};

	public static String hash(String title, String url) {
		String normalized = normalizeTitle(title) + "|" + canonicalUrl(url);
		return sha256(normalized);
	}

	public static String normalizeTitle(String title) {
		if (isNull(title)) {
			return "";
		}
		String withoutAccents = Normalizer.normalize(title, Normalizer.Form.NFD)
				.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
		return withoutAccents.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9\\s]", " ")
				.replaceAll("\\s+", " ")
				.trim();
	}

	public static String canonicalUrl(String url) {
		if (isNull(url) || url.isBlank()) {
			return "";
		}
		try {
			URI uri = new URI(url.trim());
			String query = cleanQuery(uri.getQuery());
			String path = isNull(uri.getPath()) ? "" : uri.getPath();
			if (path.length() > 1 && path.endsWith("/")) {
				path = path.substring(0, path.length() - 1);
			}
			URI canonical = new URI(
					lowerOrNull(uri.getScheme()),
					null,
					lowerOrNull(uri.getHost()),
					uri.getPort(),
					path,
					query,
					null);
			return canonical.toString();
		}
		catch (URISyntaxException ex) {
			return url.trim();
		}
	}

	private static String cleanQuery(String query) {
		if (isNull(query) || query.isBlank()) {
			return null;
		}
		String cleaned = Arrays.stream(query.split("&"))
				.filter(param -> !isTracking(param))
				.sorted()
				.collect(Collectors.joining("&"));
		return cleaned.isBlank() ? null : cleaned;
	}

	private static boolean isTracking(String param) {
		String name = param.split("=", 2)[0].toLowerCase(Locale.ROOT);
		return Arrays.stream(TRACKING_PARAM_PREFIXES).anyMatch(name::startsWith);
	}

	private static String lowerOrNull(String value) {
		return isNull(value) ? null : value.toLowerCase(Locale.ROOT);
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 indisponivel nesta JVM", ex);
		}
	}

}
