package org.nexus.config.jwt;

import com.nimbusds.jose.jwk.OctetSequenceKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.nexus.NexusConfig;

public record JwtSecrets(OctetSequenceKey accessTokenSecret, OctetSequenceKey refreshTokenSecret) {

  public static JwtSecrets fromConfig(NexusConfig config) {
    String accessSecret = ensureKeyLength(config.get("JWT_ACCESS_SECRET",
        "default-access-secret-that-is-at-least-32-characters-long-123"));
    String refreshSecret = ensureKeyLength(config.get("JWT_REFRESH_SECRET",
        "default-refresh-secret-that-is-at-least-32-characters-long-456"));

    return new JwtSecrets(
        createKey(accessSecret),
        createKey(refreshSecret)
    );
  }

  private static String ensureKeyLength(String secret) {
    // If the secret is not base64, encode it to ensure proper length
    if (!isBase64(secret)) {
      // For non-base64 strings, use the raw bytes and pad if needed
      byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
      byte[] paddedKey = new byte[32]; // 256 bits for HS256
      System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 32));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(paddedKey);
    }
    return secret;
  }

  private static boolean isBase64(String value) {
    try {
      Base64.getUrlDecoder().decode(value);
      return true;
    } catch (IllegalArgumentException _) {
      return false;
    }
  }

  private static OctetSequenceKey createKey(String secret) {
    try {
      byte[] keyBytes = getKeyBytes(secret);
      // Create an OctetSequenceKey (symmetric key for HMAC)
      return new OctetSequenceKey.Builder(keyBytes).build();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create JWT key", e);
    }
  }

  private static byte[] getKeyBytes(String secret) {
    try {
      // Try to decode as base64 first
      return Base64.getUrlDecoder().decode(secret);
    } catch (IllegalArgumentException _) {
      // If not base64, use the string bytes directly
      return secret.getBytes(StandardCharsets.UTF_8);
    }
  }
}