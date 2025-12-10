package org.nexus.messaging;

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
