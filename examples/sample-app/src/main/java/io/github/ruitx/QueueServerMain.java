package io.github.ruitx;

import org.nexus.queue.embedded.EmbeddedQueueBroker;
import org.nexus.queue.remote.QueueBrokerServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueueServerMain {

  private static final Logger LOGGER = LoggerFactory.getLogger(QueueServerMain.class);

  public static void main(String[] args) throws Exception {
    String host = System.getenv().getOrDefault("QUEUE_HOST", "0.0.0.0");
    int port = Integer.parseInt(System.getenv().getOrDefault("QUEUE_PORT", "9000"));

    LOGGER.info("Starting Queue Broker Server on {}:{}", host, port);

    EmbeddedQueueBroker broker = new EmbeddedQueueBroker();
    QueueBrokerServer server = new QueueBrokerServer(broker, host, port);

    // Add shutdown hook for graceful shutdown
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      LOGGER.info("Shutting down...");
      server.stop();
    }));

    // Blocks until shutdown
    server.startAndAwait();
  }
}