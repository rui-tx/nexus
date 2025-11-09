package org.nexus.config;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for SSL/TLS settings used by the server. Supports loading configuration from
 * environment variables and provides secure defaults for modern TLS configurations.
 */
public final class SslConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(SslConfig.class);

  // Modern, secure cipher suites - prioritizing forward secrecy (ECDHE)
  private static final List<String> SECURE_CIPHERS = List.of(
      // TLS 1.3 ciphers (preferred - provides forward secrecy by default)
      "TLS_AES_256_GCM_SHA384",
      "TLS_AES_128_GCM_SHA256",
      "TLS_CHACHA20_POLY1305_SHA256",

      // TLS 1.2 ciphers with forward secrecy (ECDHE)
      "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
      "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
      "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
      "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
      "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256"
  );

  // Only modern TLS versions
  private static final String[] SECURE_PROTOCOLS = {"TLSv1.3", "TLSv1.2"};

  private final String keystorePath;
  private final String keystorePassword;
  private final String keyPassword;
  private final boolean requireClientAuth;
  private SslContext sslContext;

  /**
   * Creates a new SSL configuration with the specified keystore and password. Client authentication
   * is disabled by default.
   *
   * @param keystorePath     Path to the keystore file
   * @param keystorePassword Password for the keystore
   * @param keyPassword      Password for the key (if different from keystore password)
   */
  public SslConfig(String keystorePath, String keystorePassword, String keyPassword) {
    this(keystorePath, keystorePassword, keyPassword, false);
  }

  /**
   * Creates a new SSL configuration with the specified parameters.
   *
   * @param keystorePath      Path to the keystore file (required)
   * @param keystorePassword  Password for the keystore (required)
   * @param keyPassword       Password for the key (if null or blank, uses keystore password)
   * @param requireClientAuth Whether to require client certificate authentication
   * @throws IllegalArgumentException if keystore path or password is invalid
   */
  public SslConfig(
      String keystorePath,
      String keystorePassword,
      String keyPassword,
      boolean requireClientAuth
  ) {
    if (keystorePath == null || keystorePath.isBlank()) {
      throw new IllegalArgumentException("Keystore path cannot be null or empty");
    }
    if (keystorePassword == null || keystorePassword.isBlank()) {
      throw new IllegalArgumentException("Keystore password cannot be null or empty");
    }

    this.keystorePath = keystorePath;
    this.keystorePassword = keystorePassword;
    this.keyPassword = (keyPassword != null && !keyPassword.isBlank())
        ? keyPassword
        : keystorePassword;
    this.requireClientAuth = requireClientAuth;
  }

  /**
   * Creates an SslConfig instance by reading configuration from environment variables.
   * Required environment variables:
   * - SSL_KEYSTORE_PATH: Path to the keystore file
   * - SSL_KEYSTORE_PASSWORD: Password for the keystore
   *
   * Optional environment variables:
   * - SSL_KEY_PASSWORD: Password for the key (defaults to keystore password)
   * - SSL_REQUIRE_CLIENT_AUTH: Whether to require client certificate authentication (default: false)
   *
   * @return A new SslConfig instance
   * @throws IllegalStateException if required environment variables are not set
   */
  /**
   * Creates an SslConfig instance by reading configuration from the application config. Required
   * configuration: - SSL_KEYSTORE_PATH: Path to the keystore file - SSL_KEYSTORE_PASSWORD: Password
   * for the keystore
   * <p>
   * Optional configuration: - SSL_KEY_PASSWORD: Password for the key (defaults to keystore
   * password) - SSL_REQUIRE_CLIENT_AUTH: Whether to require client certificate authentication
   * (default: false)
   *
   * @return A new SslConfig instance
   * @throws IllegalStateException if required configuration is not set
   */
  public static SslConfig fromConfig() {
    AppConfig config = AppConfig.getInstance();
    String keystorePath = config.get("SSL_KEYSTORE_PATH");
    String keystorePassword = config.get("SSL_KEYSTORE_PASSWORD");
    String keyPassword = config.get("SSL_KEY_PASSWORD");
    boolean requireClientAuth = config.getBoolean("SSL_REQUIRE_CLIENT_AUTH", false);

    if (keystorePath == null || keystorePath.isBlank()) {
      throw new IllegalStateException("SSL enabled but SSL_KEYSTORE_PATH is not set");
    }
    if (keystorePassword == null || keystorePassword.isBlank()) {
      throw new IllegalStateException("SSL enabled but SSL_KEYSTORE_PASSWORD is not set");
    }

    LOGGER.info("Loading SSL configuration");
    return new SslConfig(keystorePath, keystorePassword, keyPassword, requireClientAuth);
  }

  /**
   * Gets the SSL context, initializing it if necessary.
   *
   * @return The initialized SslContext
   * @throws RuntimeException if there's an error initializing the SSL context
   */
  public SslContext getSslContext() {
    if (sslContext == null) {
      try {
        sslContext = createSslContext();
      } catch (Exception e) {
        throw new RuntimeException("Failed to initialize SSL context", e);
      }
    }
    return sslContext;
  }

  /**
   * Creates and initializes a new SslContext with the current configuration.
   *
   * @return A new SslContext instance
   * @throws GeneralSecurityException if there's an error initializing the SSL context
   * @throws IOException              if there's an error reading the keystore
   */
  private SslContext createSslContext() throws GeneralSecurityException, IOException {
    LOGGER.info("Initializing SSL context from keystore: {}", keystorePath);

    // Load keystore
    KeyStore keyStore = loadKeyStore();

    // Initialize KeyManagerFactory
    KeyManagerFactory kmf = createKeyManagerFactory(keyStore);

    // Initialize TrustManagerFactory
    TrustManagerFactory tmf = createTrustManagerFactory(keyStore);

    // Build SslContext with hardened settings
    SslContextBuilder builder = SslContextBuilder
        .forServer(kmf)
        .trustManager(tmf)
        .sslProvider(SslProvider.JDK)  // JDK provider - no external deps
        .protocols(SECURE_PROTOCOLS)   // TLS 1.2 and 1.3 only
        .ciphers(SECURE_CIPHERS);      // Strong ciphers only

    if (requireClientAuth) {
      builder.clientAuth(ClientAuth.REQUIRE);
      LOGGER.info("Client certificate authentication REQUIRED");
    }

    SslContext context = builder.build();

    LOGGER.info("SSL context initialized successfully");
    LOGGER.debug("  Protocols: {}", Arrays.toString(SECURE_PROTOCOLS));
    LOGGER.debug("  Cipher suites: {} configured", SECURE_CIPHERS.size());
    LOGGER.debug("  Client auth: {}", requireClientAuth ? "REQUIRED" : "OPTIONAL");

    return context;
  }

  private KeyStore loadKeyStore() throws KeyStoreException, IOException, GeneralSecurityException {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (InputStream keystoreStream = new FileInputStream(keystorePath)) {
      keyStore.load(keystoreStream, keystorePassword.toCharArray());
    }
    return keyStore;
  }

  private KeyManagerFactory createKeyManagerFactory(KeyStore keyStore)
      throws GeneralSecurityException {
    KeyManagerFactory kmf = KeyManagerFactory.getInstance(
        KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(keyStore, keyPassword.toCharArray());
    return kmf;
  }

  private TrustManagerFactory createTrustManagerFactory(KeyStore keyStore)
      throws GeneralSecurityException {
    TrustManagerFactory tmf = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(keyStore);
    return tmf;
  }
}