package org.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.nexus.config.jwt.JwtConfig;
import org.nexus.config.jwt.JwtService;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JwtServiceTest {

  private static final Path TEMP_ENV_DIR = Path.of("target/test-env");
  private static Path envFile;

  private JwtService jwtService;

  @BeforeAll
  static void createTempEnvDir() throws Exception {
    Files.createDirectories(TEMP_ENV_DIR);
  }

  private static void assertDurationApproximately(Duration expected, Duration actual) {
    long diff = Math.abs(expected.getSeconds() - actual.getSeconds());
    assertTrue(diff <= 5,
        () -> String.format(
            "Token lifetime mismatch – expected ~%s but was %s (difference %,d seconds)",
            expected, actual, diff));
  }

  @BeforeEach
  void setUp() throws Exception {
    // Create a fresh .env file for each test
    envFile = TEMP_ENV_DIR.resolve(".env." + System.nanoTime());

    String envContent = """
        JWT_ACCESS_TOKEN_SECRET=access-secret-32-bytes-long!!123456
        JWT_REFRESH_TOKEN_SECRET=refresh-secret-32-bytes-long!!789012
        JWT_ACCESS_EXPIRATION_MINUTES=15
        JWT_REFRESH_EXPIRATION_DAYS=7
        JWT_ISSUER=nexus-server
        """;

    Files.writeString(envFile, envContent);

    // Reset and reconfigure NexusConfig
    NexusConfig.closeInstance();
    var config = NexusConfig.getInstance();
    config.setEnvFilePath(envFile.toString());
    config.init(new String[0]);

    jwtService = new JwtService(new JwtConfig(config));
  }

  @AfterEach
  void tearDown() throws Exception {
    if (Files.exists(envFile)) {
      Files.delete(envFile);
    }
  }

  @Test
  @DisplayName("Should generate valid access token with correct claims")
  void shouldGenerateValidAccessToken() throws Exception {
    String token = jwtService.generateAccessToken("user-42",
        Map.of("role", "ADMIN", "scope", "read:users write:posts"));

    assertNotNull(token);
    assertEquals(3, token.split("\\.").length);

    SignedJWT parsed = SignedJWT.parse(token);
    JWTClaimsSet claims = parsed.getJWTClaimsSet();

    assertEquals("user-42", claims.getSubject());
    assertEquals("nexus-server", claims.getIssuer());
    assertEquals("ADMIN", claims.getStringClaim("role"));
    assertEquals("read:users write:posts", claims.getStringClaim("scope"));

    Instant iat = claims.getIssueTime().toInstant();
    Instant exp = claims.getExpirationTime().toInstant();

    Duration actual = Duration.between(iat, exp);
    assertDurationApproximately(Duration.ofMinutes(15), actual);
  }

  @Test
  @DisplayName("Should generate refresh token with longer expiration")
  void shouldGenerateValidRefreshToken() throws Exception {
    String token = jwtService.generateRefreshToken("user-42");

    SignedJWT parsed = SignedJWT.parse(token);
    JWTClaimsSet claims = parsed.getJWTClaimsSet();

    assertEquals("user-42", claims.getSubject());
    assertEquals("nexus-server", claims.getIssuer());

    Instant exp = claims.getExpirationTime().toInstant();
    Duration actual = Duration.between(Instant.now(), exp);

    // Refresh token is 7 days → allow generous tolerance
    assertDurationApproximately(Duration.ofDays(7), actual);
  }

  @Test
  @DisplayName("Access and refresh tokens must use different secrets")
  void accessAndRefreshTokensMustUseDifferentSecrets() {
    String accessToken = jwtService.generateAccessToken("user", null);
    String refreshToken = jwtService.generateRefreshToken("user");

    // Trying to validate access token with refresh verifier should fail
    assertFalse(jwtService.validateRefreshToken(accessToken));
    assertFalse(jwtService.validateAccessToken(refreshToken));
  }

  @Test
  @DisplayName("Should validate only non-expired tokens")
  void shouldRejectExpiredTokens() throws Exception {
    // Override expiration to 1 second for this test
    Files.writeString(envFile, """
        JWT_ACCESS_TOKEN_SECRET=access-secret-32-bytes-long!!123456
        JWT_REFRESH_TOKEN_SECRET=refresh-secret-32-bytes-long!!789012
        JWT_ACCESS_EXPIRATION_MINUTES=0
        JWT_ACCESS_EXPIRATION_SECONDS=1
        JWT_ISSUER=nexus-server
        """);

    NexusConfig.closeInstance();
    var config = NexusConfig.getInstance();
    config.setEnvFilePath(envFile.toString());
    config.init(new String[0]);

    JwtService shortLived = new JwtService(new JwtConfig(config));
    String token = shortLived.generateAccessToken("temp", null);

    Thread.sleep(1100); // Wait for expiration

    assertFalse(shortLived.validateAccessToken(token));
  }

  @Test
  @DisplayName("Should reject malformed or tampered tokens")
  void shouldRejectInvalidTokens() {
    assertFalse(jwtService.validateAccessToken(""));
    assertFalse(jwtService.validateAccessToken("not.a.jwt"));
    assertFalse(jwtService.validateAccessToken(
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjMifQ.invalid"));

    String valid = jwtService.generateAccessToken("user", null);
    String tampered = valid.substring(0, valid.length() - 5) + "xxxxx";

    assertFalse(jwtService.validateAccessToken(tampered));
  }

  @Test
  @DisplayName("Should extract subject and custom claims correctly")
  void shouldExtractClaimsFromValidToken() {
    String token = jwtService.generateAccessToken("alice", Map.of(
        "name", "Alice Wonder",
        "admin", true,
        "permissions", Map.of("read", true, "write", false)
    ));

    assertEquals("alice", jwtService.getSubjectFromToken(token, false));
    assertEquals("Alice Wonder", jwtService.getClaim(token, "name", false));
    assertEquals(true, jwtService.getClaim(token, "admin", false));

    // Nested map should be preserved
    @SuppressWarnings("unchecked")
    var perms = (Map<String, Boolean>) jwtService.getClaim(token, "permissions", false);
    assertNotNull(perms);
    assertTrue(perms.get("read"));
    assertFalse(perms.get("write"));
  }

  @Test
  @DisplayName("Claim extraction should return null on invalid/expired/tampered token")
  void claimExtractionShouldFailGracefully() {
    String validToken = jwtService.generateAccessToken("bob", null);
    String tampered = validToken + "x";

    assertNull(jwtService.getSubjectFromToken("invalid", false));
    assertNull(jwtService.getSubjectFromToken(tampered, false));
    assertNull(jwtService.getClaim(validToken, "nonexistent", true)); // wrong token type
  }
}