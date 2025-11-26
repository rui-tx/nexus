package org.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nexus.config.jwt.JwtConfig;
import org.nexus.config.jwt.JwtService;

class NexusJwtTest {

  @TempDir
  Path tempDir;

  private Path envFile;

  @BeforeEach
  void setUp() throws Exception {
    resetSingleton();
    envFile = tempDir.resolve(".env");
    createTestEnvFile();
  }

  @AfterEach
  void tearDown() throws Exception {
    resetSingleton();
    NexusConfig.closeInstance();
  }

  @Test
  void testInitialize_Success() {
    // Given
    NexusConfig config = NexusConfig.getInstance();
    config.setEnvFilePath(envFile.toString());
    config.init(new String[]{});

    // When
    NexusJwt.initialize(config);

    // Then
    assertNotNull(NexusJwt.getJwtService());
    assertNotNull(NexusJwt.getJwtConfig());
  }

  @Test
  void testInitialize_NullConfig_ThrowsException() {
    // When/Then
    assertThrows(IllegalArgumentException.class, () -> {
      NexusJwt.initialize(null);
    });
  }

  @Test
  void testInitialize_AlreadyInitialized_ThrowsException() {
    // Given
    NexusConfig config = NexusConfig.getInstance();
    config.setEnvFilePath(envFile.toString());
    config.init(new String[]{});
    NexusJwt.initialize(config);

    // When/Then
    assertThrows(IllegalStateException.class, () -> {
      NexusJwt.initialize(config);
    });
  }

  @Test
  void testGetJwtService_NotInitialized_ThrowsException() {
    // When/Then
    assertThrows(IllegalStateException.class, NexusJwt::getJwtService);
  }

  @Test
  void testGetJwtConfig_NotInitialized_ThrowsException() {
    // When/Then
    assertThrows(IllegalStateException.class, NexusJwt::getJwtConfig);
  }

  @Test
  void testGetJwtService_ReturnsConsistentInstance() {
    // Given
    NexusConfig config = NexusConfig.getInstance();
    config.setEnvFilePath(envFile.toString());
    config.init(new String[]{});
    NexusJwt.initialize(config);

    // When
    JwtService service1 = NexusJwt.getJwtService();
    JwtService service2 = NexusJwt.getJwtService();

    // Then
    assertSame(service1, service2, "Should return the same instance");
  }

  @Test
  void testGetJwtConfig_ReturnsConsistentInstance() {
    // Given
    NexusConfig config = NexusConfig.getInstance();
    config.setEnvFilePath(envFile.toString());
    config.init(new String[]{});
    NexusJwt.initialize(config);

    // When
    JwtConfig config1 = NexusJwt.getJwtConfig();
    JwtConfig config2 = NexusJwt.getJwtConfig();

    // Then
    assertSame(config1, config2, "Should return the same instance");
  }

  @Test
  void testInitialize_WithCustomConfig() throws IOException {
    // Given - Create custom env file with JWT settings
    String customEnv = """
        JWT_SECRET=my-test-secret-key-12345
        JWT_EXPIRATION=3600
        JWT_ISSUER=test-issuer
        """;
    Files.writeString(envFile, customEnv);

    NexusConfig config = NexusConfig.getInstance();
    config.setEnvFilePath(envFile.toString());
    config.init(new String[]{});

    // When
    NexusJwt.initialize(config);

    // Then
    assertNotNull(NexusJwt.getJwtService());
    assertNotNull(NexusJwt.getJwtConfig());
    assertEquals("my-test-secret-key-12345", config.get("JWT_SECRET"));
  }

  private void createTestEnvFile() throws IOException {
    String content = """
        # Test JWT configuration
        JWT_SECRET=test-secret-key
        JWT_EXPIRATION=1800
        """;
    Files.writeString(envFile, content);
  }

  private void resetSingleton() throws Exception {
    Field configField = NexusJwt.class.getDeclaredField("config");
    configField.setAccessible(true);
    configField.set(null, null);

    Field initializedField = NexusJwt.class.getDeclaredField("initialized");
    initializedField.setAccessible(true);
    initializedField.set(null, false);
  }
}