package org.nexus.messaging;

import org.nexus.domain.QueueCapacityConfig;
import org.nexus.embedded.EmbeddedQueueBroker;

public final class MessagingClients {

  private MessagingClients() {
  }

  public static MessagingClient create(MessagingConfig config) {
    return switch (config.mode()) {
      case EMBEDDED -> {
        EmbeddedQueueBroker broker;
        Integer maxMessages = config.embeddedMaxMessagesPerQueue();
        if (maxMessages != null) {
          QueueCapacityConfig base = QueueCapacityConfig.defaultConfig();
          QueueCapacityConfig capacityConfig = new QueueCapacityConfig(
              maxMessages,
              base.maxSizeBytes()
          );
          broker = new EmbeddedQueueBroker(capacityConfig);
        } else {
          broker = new EmbeddedQueueBroker();
        }
        yield new EmbeddedMessagingClient(broker);
      }
      case REMOTE -> new RemoteMessagingClient(config.host(), config.port());
    };
  }
}
