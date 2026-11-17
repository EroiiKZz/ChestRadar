package fr.imero.chestradar.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public final class WorldKeys {
	private static final int MAX_LENGTH = 64;

	private WorldKeys() {
	}

	public static String sanitize(String raw) {
		if (raw == null || raw.isBlank()) {
			return "unknown";
		}
		String cleaned = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
		return cleaned.length() <= MAX_LENGTH ? cleaned : cleaned.substring(0, MAX_LENGTH);
	}

	public static String forSingleplayer(String saveName) {
		if (saveName == null) {
			return sanitize(null);
		}
		return sanitize("sp_" + saveName) + "_" + shortHash(saveName);
	}

	public static String forServer(String address) {
		if (address == null) {
			return sanitize(null);
		}
		return sanitize("mp_" + address) + "_" + shortHash(address);
	}

	private static String shortHash(String raw) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(raw.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(8);
			for (int i = 0; i < 4; i++) {
				sb.append(String.format("%02x", digest[i]));
			}
			return sb.toString();
		} catch (Exception e) {
			throw new IllegalStateException("SHA-256 indisponible", e);
		}
	}
}
