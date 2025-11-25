package org.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nexus.config.ServerConfig;
import org.nexus.interfaces.Middleware;

@DisplayName("ServerConfig Tests")
class ServerConfigTest {

  @TempDir
  Path tempDir;

  private Path envFile;

  // Same reset method you already use in NexusConfigTest
  private static void resetNexusConfig() {
    try {
      Field instance = NexusConfig.class.getDeclaredField("instance");
      instance.setAccessible(true);
      instance.set(null, null);
    } catch (Exception e) {
      throw new RuntimeException("Failed to reset NexusConfig singleton", e);
    }
  }

  @BeforeEach
  void setUp() throws IOException {
    resetNexusConfig();

    // Start with empty .env
    envFile = tempDir.resolve(".env");
    Files.writeString(envFile, "# empty\n");
  }

  @AfterEach
  void tearDown() {
    resetNexusConfig();
  }

  private void writeEnv(String content) throws IOException {
    Files.writeString(envFile, content.stripIndent());
    NexusConfig config = NexusConfig.getInstance();
    config.setEnvFilePath(envFile.toString());
    config.init(new String[]{});
  }

  private void initConfig(String envContent, String... cliArgs) throws IOException {
    Files.writeString(envFile, envContent.stripIndent());

    NexusConfig config = NexusConfig.getInstance();
    config.setEnvFilePath(envFile.toString());
    config.init(cliArgs); // CLI args have highest priority
  }

  @Nested
  @DisplayName("Builder")
  class BuilderTests {

    @Test
    @DisplayName("Should use correct defaults")
    void defaults() {
      ServerConfig config = ServerConfig.builder().build();

      assertEquals("0.0.0.0", config.getBindAddress());
      assertEquals(15000, config.getPort());
      assertEquals(300, config.getIdleTimeoutSeconds());
      assertEquals(10_485_760, config.getMaxContentLength());
      assertNull(config.getSslConfig());
      assertFalse(config.isSslEnabled());
      assertTrue(config.getMiddlewares().isEmpty());
    }

    @Test
    @DisplayName("SSL config is created only when SSL_ENABLED=true and required vars exist")
    void sslConfigCreatedOnlyWhenEnabledAndValid() throws IOException {
      // Case 1: SSL_ENABLED=true BUT missing keystore → should STILL create SslConfig instance
      // (ServerConfig should NOT swallow the exception — that's correct!)
      writeEnv("""
          SSL_ENABLED=true
          SSL_KEYSTORE_PATH=/fake/path/to/keystore.p12
          SSL_KEYSTORE_PASSWORD=changeit
          """);

      // This should succeed — SslConfig instance is created
      ServerConfig config = ServerConfig.from(NexusConfig.getInstance());
      assertNotNull(config.getSslConfig());
      assertTrue(config.isSslEnabled());

      // But when we actually try to use it → it will fail at runtime (correct behavior)
      assertThrows(RuntimeException.class, config.getSslConfig()::getSslContext);
    }

    @Test
    @DisplayName("SSL config is null when SSL_ENABLED=false even if keystore vars exist")
    void sslConfigNullWhenDisabledEvenIfKeystoreConfigured() throws IOException {
      writeEnv("""
          SSL_ENABLED=false
          SSL_KEYSTORE_PATH=/real/keystore.p12
          SSL_KEYSTORE_PASSWORD=secret
          """);

      ServerConfig config = ServerConfig.from(NexusConfig.getInstance());
      assertNull(config.getSslConfig());
      assertFalse(config.isSslEnabled());
    }

    @Test
    @DisplayName("SSL config is null when SSL_ENABLED missing (defaults to false)")
    void sslConfigNullWhenFlagMissing() throws IOException {
      writeEnv("""
          SSL_KEYSTORE_PATH=/some/path
          SSL_KEYSTORE_PASSWORD=pass
          """);

      ServerConfig config = ServerConfig.from(NexusConfig.getInstance());
      assertNull(config.getSslConfig());
    }

    @Test
    @DisplayName("Middlewares list should be unmodifiable")
    void middlewaresImmutable() {
      ServerConfig config = ServerConfig.builder()
          .middleware((ctx, chain) -> {
          })
          .build();

      var list = config.getMiddlewares();
      assertEquals(1, list.size());
      assertThrows(UnsupportedOperationException.class, () -> list.add(null));
    }

    @Test
    @DisplayName("Builder should reject null middlewares")
    void nullRejection() {
      ServerConfig.Builder builder = ServerConfig.builder();

      assertThrows(NullPointerException.class, () -> builder.middleware(null));
      assertThrows(NullPointerException.class, () -> builder.middlewares((Middleware) null));
      assertThrows(NullPointerException.class,
          () -> builder.middlewares((java.util.List<Middleware>) null));
    }
  }

  @Nested
  @DisplayName("from(NexusConfig) factory")
  class FromNexusConfigTests {

    @Test
    @DisplayName("Uses defaults when nothing configured")
    void usesDefaults() throws IOException {
      initConfig("");

      ServerConfig config = ServerConfig.from(NexusConfig.getInstance());

      assertEquals("0.0.0.0", config.getBindAddress());
      assertEquals(15000, config.getPort());
      assertNull(config.getSslConfig());
    }

    @Test
    @DisplayName("Reads all values from .env")
    void readsFromEnv() throws IOException {
      initConfig("""
              BIND_ADDRESS=10.0.0.50
              SERVER_PORT=8088
              IDLE_TIMEOUT_SECONDS=120
              MAX_CONTENT_LENGTH=5242880
              SSL_ENABLED=true
              """,
          // These fake values satisfy SslConfig.fromConfig() — no exception!
          "--SSL_KEYSTORE_PATH=/fake/test.p12",
          "--SSL_KEYSTORE_PASSWORD=changeit"
      );

      ServerConfig config = ServerConfig.from(NexusConfig.getInstance());

      assertEquals("10.0.0.50", config.getBindAddress());
      assertEquals(8088, config.getPort());
      assertEquals(120, config.getIdleTimeoutSeconds());
      assertEquals(5_242_880, config.getMaxContentLength());
      assertNotNull(config.getSslConfig());
      assertTrue(config.isSslEnabled());
    }

    @Test
    @DisplayName("SSL disabled when flag is false (even if keystore vars exist)")
    void sslDisabled() throws IOException {
      initConfig("""
          SSL_ENABLED=false
          SSL_KEYSTORE_PATH=anything
          SSL_KEYSTORE_PASSWORD=anything
          """);

      ServerConfig config = ServerConfig.from(NexusConfig.getInstance());
      assertNull(config.getSslConfig());
      assertFalse(config.isSslEnabled());
    }

    @Test
    @DisplayName("SSL disabled when flag missing (defaults to false)")
    void sslFlagMissing() throws IOException {
      initConfig("""
          SSL_KEYSTORE_PATH=whatever
          SSL_KEYSTORE_PASSWORD=whatever
          """);

      ServerConfig config = ServerConfig.from(NexusConfig.getInstance());
      assertNull(config.getSslConfig());
    }

    @Test
    @DisplayName("SSL enabled only when SSL_ENABLED=true + required vars present (via CLI)")
    void sslEnabled() throws IOException {
      initConfig("""
              SSL_ENABLED=true
              """,
          "--SSL_KEYSTORE_PATH=dummy.p12",
          "--SSL_KEYSTORE_PASSWORD=secret"
      );

      ServerConfig config = ServerConfig.from(NexusConfig.getInstance());
      assertNotNull(config.getSslConfig());
      assertTrue(config.isSslEnabled());
    }
  }
}