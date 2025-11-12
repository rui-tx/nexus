package org.nexus.config.jwt;

import com.nimbusds.jose.jwk.OctetSequenceKey;
import java.time.Duration;
import org.nexus.NexusConfig;

public class JwtConfig {

  private final JwtSecrets secrets;
  private final Duration accessTokenExpiration;
  private final Duration refreshTokenExpiration;
  private final String issuer;

  public JwtConfig(NexusConfig config) {
    this.secrets = JwtSecrets.fromConfig(config);
    this.accessTokenExpiration = Duration.ofMinutes(
        config.getLong("JWT_ACCESS_EXPIRATION_MINUTES", 15)
    );
    this.refreshTokenExpiration = Duration.ofDays(
        config.getLong("JWT_REFRESH_EXPIRATION_DAYS", 7)
    );
    this.issuer = config.get("JWT_ISSUER", "nexus-server");
  }

  public OctetSequenceKey getAccessTokenSecret() {
    return secrets.accessTokenSecret();
  }

  public OctetSequenceKey getRefreshTokenSecret() {
    return secrets.refreshTokenSecret();
  }

  public Duration getAccessTokenExpiration() {
    return accessTokenExpiration;
  }

  public Duration getRefreshTokenExpiration() {
    return refreshTokenExpiration;
  }

  public String getIssuer() {
    return issuer;
  }
}