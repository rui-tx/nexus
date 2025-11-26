package org.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nexus.config.jwt.JwtService;

class JwtFactoryTest {

  @TempDir
  Path tempDir;

  private Path envFile;
  private JwtFactory factory;

  @BeforeEach
  void setUp() throws Exception {
    factory = new JwtFactory();
    envFile = tempDir.resolve(".env");
    createTestEnvFile();

    NexusConfig config = NexusConfig.getInstance();
    config.setEnvFilePath(envFile.toString());
    config.init(new String[]{});
  }

  @AfterEach
  void tearDown() {
    NexusConfig.closeInstance();
  }

  @Test
  void testJwtService_Creation() {
    // When
    JwtService jwtService = factory.jwtService();

    // Then
    assertNotNull(jwtService, "JwtService should be created");
  }

  @Test
  void testJwtService_CreatesNewInstanceEachTime() {
    // When
    JwtService service1 = factory.jwtService();
    JwtService service2 = factory.jwtService();

    // Then
    assertNotSame(service1, service2,
        "Factory method should create new instances each time");
  }

  @Test
  void testJwtService_WithCommandLineArgs() {
    // Given
    NexusConfig.closeInstance();
    NexusConfig config = NexusConfig.getInstance();
    config.setEnvFilePath(envFile.toString());
    config.init(new String[]{"--JWT_SECRET=cli-secret", "--JWT_EXPIRATION=7200"});

    // When
    JwtService jwtService = factory.jwtService();

    // Then
    assertNotNull(jwtService);
    assertEquals("cli-secret", config.get("JWT_SECRET"));
    assertEquals(7200, config.getInt("JWT_EXPIRATION", 0));
  }

  private void createTestEnvFile() throws IOException {
    String content = """
        JWT_SECRET=factory-test-secret
        JWT_EXPIRATION=3600
        JWT_ISSUER=factory-test
        """;
    Files.writeString(envFile, content);
  }
}
