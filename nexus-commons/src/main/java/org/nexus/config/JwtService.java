package org.nexus.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

public class JwtService {
  
  private final JwtConfig config;
  private final JWSSigner accessSigner;
  private final JWSSigner refreshSigner;
  private final JWSVerifier accessVerifier;
  private final JWSVerifier refreshVerifier;

  public JwtService(JwtConfig config) {
    this.config = config;
    try {
      // Create signers and verifiers once during initialization
      this.accessSigner = new MACSigner(config.getAccessTokenSecret());
      this.refreshSigner = new MACSigner(config.getRefreshTokenSecret());
      this.accessVerifier = new MACVerifier(config.getAccessTokenSecret());
      this.refreshVerifier = new MACVerifier(config.getRefreshTokenSecret());
    } catch (JOSEException e) {
      throw new IllegalStateException("Failed to initialize JWT signers/verifiers", e);
    }
  }

  public String generateAccessToken(String subject, Map<String, Object> claims) {
    try {
      Instant now = Instant.now();
      Instant expiration = now.plus(config.getAccessTokenExpiration());

      JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
          .subject(subject)
          .issuer(config.getIssuer())
          .issueTime(Date.from(now))
          .expirationTime(Date.from(expiration));

      // Add custom claims
      if (claims != null) {
        claims.forEach(claimsBuilder::claim);
      }

      SignedJWT signedJWT = new SignedJWT(
          new JWSHeader(JWSAlgorithm.HS256),
          claimsBuilder.build()
      );

      signedJWT.sign(accessSigner);
      return signedJWT.serialize();
    } catch (JOSEException e) {
      throw new RuntimeException("Failed to generate access token", e);
    }
  }

  public String generateRefreshToken(String subject) {
    try {
      Instant now = Instant.now();
      Instant expiration = now.plus(config.getRefreshTokenExpiration());

      JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
          .subject(subject)
          .issuer(config.getIssuer())
          .issueTime(Date.from(now))
          .expirationTime(Date.from(expiration))
          .build();

      SignedJWT signedJWT = new SignedJWT(
          new JWSHeader(JWSAlgorithm.HS256),
          claimsSet
      );

      signedJWT.sign(refreshSigner);
      return signedJWT.serialize();
    } catch (JOSEException e) {
      throw new RuntimeException("Failed to generate refresh token", e);
    }
  }

  public boolean validateAccessToken(String token) {
    return validateToken(token, false);
  }

  public boolean validateRefreshToken(String token) {
    return validateToken(token, true);
  }

  private boolean validateToken(String token, boolean isRefreshToken) {
    try {
      SignedJWT signedJWT = SignedJWT.parse(token);
      JWSVerifier verifier = isRefreshToken ? refreshVerifier : accessVerifier;

      // Verify signature
      if (!signedJWT.verify(verifier)) {
        return false;
      }

      // Check expiration
      JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
      Date expirationTime = claims.getExpirationTime();
      return expirationTime == null || !expirationTime.before(new Date());

    } catch (ParseException | JOSEException e) {
      return false;
    }
  }

  public String getSubjectFromToken(String token, boolean isRefreshToken) {
    JWTClaimsSet claims = getAllClaimsFromToken(token, isRefreshToken);
    return claims != null ? claims.getSubject() : null;
  }

  public Object getClaim(String token, String claimName, boolean isRefreshToken) {
    JWTClaimsSet claims = getAllClaimsFromToken(token, isRefreshToken);
    return claims != null ? claims.getClaim(claimName) : null;
  }

  private JWTClaimsSet getAllClaimsFromToken(String token, boolean isRefreshToken) {
    try {
      SignedJWT signedJWT = SignedJWT.parse(token);
      JWSVerifier verifier = isRefreshToken ? refreshVerifier : accessVerifier;

      if (!signedJWT.verify(verifier)) {
        return null;
      }

      return signedJWT.getJWTClaimsSet();
    } catch (ParseException | JOSEException e) {
      return null;
    }
  }
}