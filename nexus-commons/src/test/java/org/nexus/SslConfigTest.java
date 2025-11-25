package org.nexus;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.netty.buffer.Unpooled;
import io.netty.handler.ssl.SslContext;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.net.ssl.SSLEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nexus.config.SslConfig;

/**
 * For this tests to pass we need a test keystore openssl req -x509 -newkey rsa:2048 -keyout key.pem
 * -out cert.pem -days 365 -nodes -subj "/CN=localhost"
 * <p/>
 * openssl pkcs12 -export -in cert.pem -inkey key.pem -out test-keystore.p12 -name localhost
 * -passout pass:testpass rm key.pem cert.pem
 * </p>
 * mv test-keystore.p12 [test resource folder]
 *
 */
@DisplayName("SslConfig Tests")
class SslConfigTest {

  @TempDir
  Path tempDir;

  private Path keystorePath;

  // Exact same reset method used in your other tests
  private static void resetNexusConfig() {
    try {
      Field instance = NexusConfig.class.getDeclaredField("instance");
      instance.setAccessible(true);
      instance.set(null, null);
    } catch (Exception e) {
      throw new RuntimeException("Failed to reset NexusConfig singleton", e);
    }
  }

  private static <T> T getField(Object obj, String name) throws Exception {
    Field f = obj.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return (T) f.get(obj);
  }

  @BeforeEach
  void setUp() throws Exception {
    resetNexusConfig();

    // Copy embedded test keystore to temp dir (so path is real and predictable)
    URL keystoreResource = getClass().getClassLoader().getResource("test-keystore.p12");
    if (keystoreResource == null) {
      fail("Missing test-keystore.p12 in src/test/resources. Run: make generate-test-keystore");
    }
    keystorePath = tempDir.resolve("test-keystore.p12");
    Files.copy(Path.of(keystoreResource.toURI()), keystorePath);

    // Create .env file with SSL config
    String envContent = """
        SSL_KEYSTORE_PATH=%s
        SSL_KEYSTORE_PASSWORD=testpass
        SSL_KEY_PASSWORD=customkeypass
        SSL_REQUIRE_CLIENT_AUTH=true
        """.formatted(keystorePath.toString().replace("\\", "\\\\")); // Windows-safe

    Path envFile = tempDir.resolve(".env");
    Files.writeString(envFile, envContent);

    // Set the env file path and init
    NexusConfig config = NexusConfig.getInstance();
    config.setEnvFilePath(envFile.toString());
    config.init(new String[0]);
  }

  @AfterEach
  void tearDown() {
    resetNexusConfig();
  }

  @Test
  @DisplayName("constructor rejects null/blank keystore path or password")
  void constructor_validatesInputs() {
    assertThrows(IllegalArgumentException.class, () -> new SslConfig(null, "pass", null, false));
    assertThrows(IllegalArgumentException.class, () -> new SslConfig("  ", "pass", null, false));
    assertThrows(IllegalArgumentException.class, () -> new SslConfig("path", null, null, false));
    assertThrows(IllegalArgumentException.class, () -> new SslConfig("path", "", null, false));
  }

  @Test
  @DisplayName("key password falls back to keystore password when blank")
  void keyPassword_fallsBackCorrectly() {
    SslConfig c1 = new SslConfig("a", "store", null, false);
    SslConfig c2 = new SslConfig("a", "store", "   ", false);
    SslConfig c3 = new SslConfig("a", "store", "real", false);

    assertAll(
        () -> assertEquals("store", getField(c1, "keyPassword")),
        () -> assertEquals("store", getField(c2, "keyPassword")),
        () -> assertEquals("real", getField(c3, "keyPassword"))
    );
  }

  @Test
  @DisplayName("fromConfig() loads values correctly from NexusConfig")
  void fromConfig_loadsFromRealConfig() {
    SslConfig ssl = SslConfig.fromConfig();

    assertAll(
        () -> assertEquals(keystorePath.toString(), getField(ssl, "keystorePath")),
        () -> assertEquals("testpass", getField(ssl, "keystorePassword")),
        () -> assertEquals("customkeypass", getField(ssl, "keyPassword")),
        () -> assertTrue((Boolean) getField(ssl, "requireClientAuth"))
    );
  }

  @Test
  @DisplayName("getSslContext() returns valid hardened server context")
  void sslContext_isValidAndHardened() {
    SslConfig ssl = new SslConfig(
        keystorePath.toString(),
        "testpass",
        "testpass",
        false
    );

    SslContext ctx = ssl.getSslContext();

    assertTrue(ctx.isServer());

    SSLEngine engine = ctx.newEngine(Unpooled.EMPTY_BUFFER.alloc());
    assertFalse(engine.getUseClientMode());

    String[] protocols = engine.getEnabledProtocols();
    assertTrue(Arrays.stream(protocols).allMatch(p -> p.equals("TLSv1.3") || p.equals("TLSv1.2")),
        "Only TLSv1.3 and TLSv1.2 allowed, got: " + Arrays.toString(protocols));

    String[] ciphers = engine.getEnabledCipherSuites();
    assertTrue(ciphers.length >= 3, "Should have multiple strong ciphers");
  }

  @Test
  @DisplayName("client auth is REQUIRED when configured")
  void clientAuth_isEnforced() {
    SslConfig require = new SslConfig(keystorePath.toString(), "testpass", "testpass", true);
    SslConfig none = new SslConfig(keystorePath.toString(), "testpass", "testpass", false);

    SSLEngine e1 = require.getSslContext().newEngine(Unpooled.EMPTY_BUFFER.alloc());
    SSLEngine e2 = none.getSslContext().newEngine(Unpooled.EMPTY_BUFFER.alloc());

    assertTrue(e1.getNeedClientAuth());
    assertFalse(e2.getNeedClientAuth());
  }

  @Test
  @DisplayName("getSslContext() is lazy and idempotent")
  void getSslContext_isIdempotent() {
    SslConfig ssl = new SslConfig(keystorePath.toString(), "testpass", "testpass", false);

    SslContext c1 = ssl.getSslContext();
    SslContext c2 = ssl.getSslContext();

    assertSame(c1, c2);
  }

  @Test
  @DisplayName("wrong keystore password throws wrapped exception")
  void wrongPassword_failsGracefully() {
    SslConfig ssl = new SslConfig(keystorePath.toString(), "wrong", "wrong", false);

    RuntimeException ex = assertThrows(RuntimeException.class, ssl::getSslContext);
    assertEquals("Failed to initialize SSL context", ex.getMessage());
  }
}