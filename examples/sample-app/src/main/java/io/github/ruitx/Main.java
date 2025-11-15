package io.github.ruitx;

import java.util.List;
import org.nexus.NexusBeanScope;
import org.nexus.config.ServerConfig;
import org.nexus.server.NexusServer;

public class Main {
  static void main() throws Exception {
    NexusBeanScope.init();

    ServerConfig cfg = ServerConfig.builder()
        .idleTimeoutSeconds(300)
        .maxContentLength(1_048_576)
        .build();

    NexusServer server = new NexusServer(cfg, List.of());
    server.startAndAwait();
  }
}