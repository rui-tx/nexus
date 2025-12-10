package org.nexus.queue.net;

public enum QueueApiKey {

  PRODUCE_REQUEST(1),
  PRODUCE_RESPONSE(2),
  FETCH_REQUEST(3),
  FETCH_RESPONSE(4),
  COMMIT_OFFSET_REQUEST(5),
  COMMIT_OFFSET_RESPONSE(6),
  SUBSCRIBE_REQUEST(7),
  SUBSCRIBE_RESPONSE(8),
  HEARTBEAT_REQUEST(9),
  HEARTBEAT_RESPONSE(10);

  private final byte id;

  QueueApiKey(int id) {
    this.id = (byte) id;
  }

  public byte id() {
    return id;
  }

  public static QueueApiKey fromId(byte id) {
    for (QueueApiKey value : values()) {
      if (value.id == id) {
        return value;
      }
    }
    throw new IllegalArgumentException("Unknown api key: " + id);
  }
}
