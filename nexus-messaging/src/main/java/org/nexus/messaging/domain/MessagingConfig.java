package org.nexus.messaging.domain;

import org.nexus.commons.enums.MessagingMode;

public record MessagingConfig(
    MessagingMode mode,
    String host,
    int port,
    Integer embeddedMaxMessagesPerQueue
) {

  public MessagingConfig(MessagingMode mode, String host, int port) {
    this(mode, host, port, null);
  }
}
