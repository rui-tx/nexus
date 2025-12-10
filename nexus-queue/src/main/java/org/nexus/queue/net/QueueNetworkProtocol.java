package org.nexus.queue.net;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class QueueNetworkProtocol {

  private static final int FRAME_HEADER_SIZE = 1 + 1 + 4; // apiKey + apiVersion + correlationId

  private QueueNetworkProtocol() {
  }

  public static ByteBuffer encodeProduceRequest(ProduceRequest request) {
    byte[] categoryBytes = stringToBytes(request.category());
    byte[] keyBytes = stringToBytesNullable(request.key());

    int headersSize = Short.BYTES; // header count
    for (Map.Entry<String, String> entry : request.headers().entrySet()) {
      byte[] k = stringToBytes(entry.getKey());
      byte[] v = stringToBytes(entry.getValue());
      headersSize += Short.BYTES + k.length + Short.BYTES + v.length;
    }

    int bodySize = 0;
    bodySize += Short.BYTES + categoryBytes.length; // category
    bodySize += Short.BYTES + keyBytes.length; // key
    bodySize += headersSize; // headers map
    bodySize += Integer.BYTES; // flags
    bodySize += Integer.BYTES; // messageCount

    for (byte[] payload : request.payloads()) {
      bodySize += Integer.BYTES + payload.length;
    }

    int frameLength = FRAME_HEADER_SIZE + bodySize;

    ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + frameLength);
    buffer.putInt(frameLength);

    QueueFrameHeader header = request.header();
    buffer.put(header.apiKey().id());
    buffer.put(header.apiVersion());
    buffer.putInt(header.correlationId());

    putString(buffer, categoryBytes);
    putString(buffer, keyBytes);

    buffer.putShort((short) request.headers().size());
    for (Map.Entry<String, String> entry : request.headers().entrySet()) {
      putString(buffer, stringToBytes(entry.getKey()));
      putString(buffer, stringToBytes(entry.getValue()));
    }

    buffer.putInt(request.flags());

    buffer.putInt(request.messageCount());
    for (byte[] payload : request.payloads()) {
      buffer.putInt(payload.length);
      buffer.put(payload);
    }

    buffer.flip();
    return buffer;
  }

  public static ProduceRequest decodeProduceRequest(ByteBuffer buffer) {
    int frameLength = buffer.getInt();
    if (frameLength < FRAME_HEADER_SIZE) {
      throw new IllegalArgumentException("Invalid frame length: " + frameLength);
    }

    byte apiKeyId = buffer.get();
    QueueApiKey apiKey = QueueApiKey.fromId(apiKeyId);
    if (apiKey != QueueApiKey.PRODUCE_REQUEST) {
      throw new IllegalArgumentException("Unexpected api key for ProduceRequest: " + apiKey);
    }

    byte apiVersion = buffer.get();
    int correlationId = buffer.getInt();
    QueueFrameHeader header = new QueueFrameHeader(apiKey, apiVersion, correlationId);

    String category = readString(buffer);
    String key = readNullableString(buffer);

    int headerCount = Short.toUnsignedInt(buffer.getShort());
    Map<String, String> headers = new HashMap<>(headerCount);
    for (int i = 0; i < headerCount; i++) {
      String k = readString(buffer);
      String v = readString(buffer);
      headers.put(k, v);
    }

    int flags = buffer.getInt();

    int messageCount = buffer.getInt();
    if (messageCount <= 0) {
      throw new IllegalArgumentException("messageCount must be positive, was " + messageCount);
    }

    List<byte[]> payloads = new ArrayList<>(messageCount);
    for (int i = 0; i < messageCount; i++) {
      int length = buffer.getInt();
      if (length < 0) {
        throw new IllegalArgumentException("Negative payload length: " + length);
      }
      byte[] data = new byte[length];
      buffer.get(data);
      payloads.add(data);
    }

    return new ProduceRequest(header, category, key, headers, flags, payloads);
  }

  public static ByteBuffer encodeProduceResponse(ProduceResponse response) {
    int bodySize = 0;
    bodySize += Integer.BYTES; // messageCount

    for (ProduceResponse.Result result : response.results()) {
      bodySize += Long.BYTES * 2; // UUID
      bodySize += Integer.BYTES; // queueId
      bodySize += Long.BYTES; // offset
      bodySize += Integer.BYTES; // errorCode
    }

    int frameLength = FRAME_HEADER_SIZE + bodySize;

    ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + frameLength);
    buffer.putInt(frameLength);

    QueueFrameHeader header = response.header();
    buffer.put(header.apiKey().id());
    buffer.put(header.apiVersion());
    buffer.putInt(header.correlationId());

    buffer.putInt(response.messageCount());
    for (ProduceResponse.Result result : response.results()) {
      long most = result.messageId().getMostSignificantBits();
      long least = result.messageId().getLeastSignificantBits();
      buffer.putLong(most);
      buffer.putLong(least);
      buffer.putInt(result.queueId());
      buffer.putLong(result.offset());
      buffer.putInt(result.errorCode());
    }

    buffer.flip();
    return buffer;
  }

  public static ProduceResponse decodeProduceResponse(ByteBuffer buffer) {
    int frameLength = buffer.getInt();
    if (frameLength < FRAME_HEADER_SIZE) {
      throw new IllegalArgumentException("Invalid frame length: " + frameLength);
    }

    byte apiKeyId = buffer.get();
    QueueApiKey apiKey = QueueApiKey.fromId(apiKeyId);
    if (apiKey != QueueApiKey.PRODUCE_RESPONSE) {
      throw new IllegalArgumentException(
          "Unexpected api key for ProduceResponse: " + apiKey);
    }

    byte apiVersion = buffer.get();
    int correlationId = buffer.getInt();
    QueueFrameHeader header = new QueueFrameHeader(apiKey, apiVersion, correlationId);

    int messageCount = buffer.getInt();
    if (messageCount < 0) {
      throw new IllegalArgumentException("Negative messageCount: " + messageCount);
    }

    List<ProduceResponse.Result> results = new ArrayList<>(messageCount);
    for (int i = 0; i < messageCount; i++) {
      long most = buffer.getLong();
      long least = buffer.getLong();
      int queueId = buffer.getInt();
      long offset = buffer.getLong();
      int errorCode = buffer.getInt();
      results.add(new ProduceResponse.Result(
          new java.util.UUID(most, least),
          queueId,
          offset,
          errorCode
      ));
    }

    return new ProduceResponse(header, results);
  }

  public static ByteBuffer encodeFetchRequest(FetchRequest request) {
    byte[] categoryBytes = stringToBytes(request.category());
    byte[] groupBytes = stringToBytes(request.groupId());
    byte[] clientBytes = stringToBytes(request.clientId());

    int bodySize = 0;
    bodySize += Short.BYTES + categoryBytes.length; // category
    bodySize += Short.BYTES + groupBytes.length; // groupId
    bodySize += Short.BYTES + clientBytes.length; // clientId
    bodySize += Integer.BYTES; // queueId
    bodySize += Long.BYTES; // fromOffset
    bodySize += Integer.BYTES; // maxRecords
    bodySize += Integer.BYTES; // maxWaitMs

    int frameLength = FRAME_HEADER_SIZE + bodySize;

    ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + frameLength);
    buffer.putInt(frameLength);

    QueueFrameHeader header = request.header();
    buffer.put(header.apiKey().id());
    buffer.put(header.apiVersion());
    buffer.putInt(header.correlationId());

    putString(buffer, categoryBytes);
    putString(buffer, groupBytes);
    putString(buffer, clientBytes);
    buffer.putInt(request.queueId());
    buffer.putLong(request.fromOffset());
    buffer.putInt(request.maxRecords());
    buffer.putInt(request.maxWaitMs());

    buffer.flip();
    return buffer;
  }

  public static FetchRequest decodeFetchRequest(ByteBuffer buffer) {
    int frameLength = buffer.getInt();
    if (frameLength < FRAME_HEADER_SIZE) {
      throw new IllegalArgumentException("Invalid frame length: " + frameLength);
    }

    byte apiKeyId = buffer.get();
    QueueApiKey apiKey = QueueApiKey.fromId(apiKeyId);
    if (apiKey != QueueApiKey.FETCH_REQUEST) {
      throw new IllegalArgumentException("Unexpected api key for FetchRequest: " + apiKey);
    }

    byte apiVersion = buffer.get();
    int correlationId = buffer.getInt();
    QueueFrameHeader header = new QueueFrameHeader(apiKey, apiVersion, correlationId);

    String category = readString(buffer);
    String groupId = readString(buffer);
    String clientId = readString(buffer);
    int queueId = buffer.getInt();
    long fromOffset = buffer.getLong();
    int maxRecords = buffer.getInt();
    int maxWaitMs = buffer.getInt();

    return new FetchRequest(header, category, groupId, clientId, queueId, fromOffset,
        maxRecords, maxWaitMs);
  }

  public static ByteBuffer encodeFetchResponse(FetchResponse response) {
    int bodySize = 0;
    bodySize += Integer.BYTES; // messageCount

    for (FetchResponse.Message message : response.messages()) {
      bodySize += Long.BYTES * 2; // UUID
      bodySize += Integer.BYTES; // queueId
      bodySize += Long.BYTES; // offset
      bodySize += Long.BYTES; // timestamp

      byte[] keyBytes = stringToBytesNullable(message.key());
      bodySize += Short.BYTES + keyBytes.length; // key

      Map<String, String> headers = message.headers();
      bodySize += Short.BYTES; // header count
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        byte[] k = stringToBytes(entry.getKey());
        byte[] v = stringToBytes(entry.getValue());
        bodySize += Short.BYTES + k.length + Short.BYTES + v.length;
      }

      byte[] payload = message.payload();
      bodySize += Integer.BYTES + payload.length; // payload
    }

    int frameLength = FRAME_HEADER_SIZE + bodySize;

    ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + frameLength);
    buffer.putInt(frameLength);

    QueueFrameHeader header = response.header();
    buffer.put(header.apiKey().id());
    buffer.put(header.apiVersion());
    buffer.putInt(header.correlationId());

    buffer.putInt(response.messageCount());
    for (FetchResponse.Message message : response.messages()) {
      UUID messageId = message.messageId();
      buffer.putLong(messageId.getMostSignificantBits());
      buffer.putLong(messageId.getLeastSignificantBits());
      buffer.putInt(message.queueId());
      buffer.putLong(message.offset());
      buffer.putLong(message.timestamp());

      putString(buffer, stringToBytesNullable(message.key()));

      Map<String, String> headers = message.headers();
      buffer.putShort((short) headers.size());
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        putString(buffer, stringToBytes(entry.getKey()));
        putString(buffer, stringToBytes(entry.getValue()));
      }

      byte[] payload = message.payload();
      buffer.putInt(payload.length);
      buffer.put(payload);
    }

    buffer.flip();
    return buffer;
  }

  public static FetchResponse decodeFetchResponse(ByteBuffer buffer) {
    int frameLength = buffer.getInt();
    if (frameLength < FRAME_HEADER_SIZE) {
      throw new IllegalArgumentException("Invalid frame length: " + frameLength);
    }

    byte apiKeyId = buffer.get();
    QueueApiKey apiKey = QueueApiKey.fromId(apiKeyId);
    if (apiKey != QueueApiKey.FETCH_RESPONSE) {
      throw new IllegalArgumentException("Unexpected api key for FetchResponse: " + apiKey);
    }

    byte apiVersion = buffer.get();
    int correlationId = buffer.getInt();
    QueueFrameHeader header = new QueueFrameHeader(apiKey, apiVersion, correlationId);

    int messageCount = buffer.getInt();
    if (messageCount < 0) {
      throw new IllegalArgumentException("Negative messageCount: " + messageCount);
    }

    List<FetchResponse.Message> messages = new ArrayList<>(messageCount);
    for (int i = 0; i < messageCount; i++) {
      long most = buffer.getLong();
      long least = buffer.getLong();
      int queueId = buffer.getInt();
      long offset = buffer.getLong();
      long timestamp = buffer.getLong();
      String key = readNullableString(buffer);

      int headerCount = Short.toUnsignedInt(buffer.getShort());
      Map<String, String> headers = new HashMap<>(headerCount);
      for (int h = 0; h < headerCount; h++) {
        String k = readString(buffer);
        String v = readString(buffer);
        headers.put(k, v);
      }

      int payloadLength = buffer.getInt();
      if (payloadLength < 0) {
        throw new IllegalArgumentException("Negative payload length: " + payloadLength);
      }
      byte[] payload = new byte[payloadLength];
      buffer.get(payload);

      messages.add(new FetchResponse.Message(new UUID(most, least), queueId, offset, timestamp,
          key, headers, payload));
    }

    return new FetchResponse(header, messages);
  }

  public static ByteBuffer encodeCommitOffsetRequest(CommitOffsetRequest request) {
    byte[] categoryBytes = stringToBytes(request.category());
    byte[] groupBytes = stringToBytes(request.groupId());

    int bodySize = 0;
    bodySize += Short.BYTES + categoryBytes.length; // category
    bodySize += Short.BYTES + groupBytes.length; // groupId
    bodySize += Integer.BYTES; // count

    int offsetsSize = request.offsets().size();
    for (int i = 0; i < offsetsSize; i++) {
      bodySize += Integer.BYTES; // queueId
      bodySize += Long.BYTES; // offset
    }

    int frameLength = FRAME_HEADER_SIZE + bodySize;

    ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + frameLength);
    buffer.putInt(frameLength);

    QueueFrameHeader header = request.header();
    buffer.put(header.apiKey().id());
    buffer.put(header.apiVersion());
    buffer.putInt(header.correlationId());

    putString(buffer, categoryBytes);
    putString(buffer, groupBytes);

    buffer.putInt(request.count());
    for (Map.Entry<Integer, Long> entry : request.offsets().entrySet()) {
      buffer.putInt(entry.getKey());
      buffer.putLong(entry.getValue());
    }

    buffer.flip();
    return buffer;
  }

  public static CommitOffsetRequest decodeCommitOffsetRequest(ByteBuffer buffer) {
    int frameLength = buffer.getInt();
    if (frameLength < FRAME_HEADER_SIZE) {
      throw new IllegalArgumentException("Invalid frame length: " + frameLength);
    }

    byte apiKeyId = buffer.get();
    QueueApiKey apiKey = QueueApiKey.fromId(apiKeyId);
    if (apiKey != QueueApiKey.COMMIT_OFFSET_REQUEST) {
      throw new IllegalArgumentException(
          "Unexpected api key for CommitOffsetRequest: " + apiKey);
    }

    byte apiVersion = buffer.get();
    int correlationId = buffer.getInt();
    QueueFrameHeader header = new QueueFrameHeader(apiKey, apiVersion, correlationId);

    String category = readString(buffer);
    String groupId = readString(buffer);

    int count = buffer.getInt();
    if (count < 0) {
      throw new IllegalArgumentException("Negative count: " + count);
    }

    Map<Integer, Long> offsets = new HashMap<>(count);
    for (int i = 0; i < count; i++) {
      int queueId = buffer.getInt();
      long offset = buffer.getLong();
      offsets.put(queueId, offset);
    }

    return new CommitOffsetRequest(header, category, groupId, offsets);
  }

  public static ByteBuffer encodeCommitOffsetResponse(CommitOffsetResponse response) {
    int bodySize = 0;
    bodySize += Integer.BYTES; // errorCode

    int frameLength = FRAME_HEADER_SIZE + bodySize;

    ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + frameLength);
    buffer.putInt(frameLength);

    QueueFrameHeader header = response.header();
    buffer.put(header.apiKey().id());
    buffer.put(header.apiVersion());
    buffer.putInt(header.correlationId());

    buffer.putInt(response.errorCode());

    buffer.flip();
    return buffer;
  }

  public static CommitOffsetResponse decodeCommitOffsetResponse(ByteBuffer buffer) {
    int frameLength = buffer.getInt();
    if (frameLength < FRAME_HEADER_SIZE) {
      throw new IllegalArgumentException("Invalid frame length: " + frameLength);
    }

    byte apiKeyId = buffer.get();
    QueueApiKey apiKey = QueueApiKey.fromId(apiKeyId);
    if (apiKey != QueueApiKey.COMMIT_OFFSET_RESPONSE) {
      throw new IllegalArgumentException(
          "Unexpected api key for CommitOffsetResponse: " + apiKey);
    }

    byte apiVersion = buffer.get();
    int correlationId = buffer.getInt();
    QueueFrameHeader header = new QueueFrameHeader(apiKey, apiVersion, correlationId);

    int errorCode = buffer.getInt();

    return new CommitOffsetResponse(header, errorCode);
  }

  public static ByteBuffer encodeSubscribeRequest(SubscribeRequest request) {
    byte[] categoryBytes = stringToBytes(request.category());
    byte[] groupBytes = stringToBytes(request.groupId());
    byte[] clientBytes = stringToBytes(request.clientId());

    int bodySize = 0;
    bodySize += Short.BYTES + categoryBytes.length; // category
    bodySize += Short.BYTES + groupBytes.length; // groupId
    bodySize += Short.BYTES + clientBytes.length; // clientId

    int frameLength = FRAME_HEADER_SIZE + bodySize;

    ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + frameLength);
    buffer.putInt(frameLength);

    QueueFrameHeader header = request.header();
    buffer.put(header.apiKey().id());
    buffer.put(header.apiVersion());
    buffer.putInt(header.correlationId());

    putString(buffer, categoryBytes);
    putString(buffer, groupBytes);
    putString(buffer, clientBytes);

    buffer.flip();
    return buffer;
  }

  public static SubscribeRequest decodeSubscribeRequest(ByteBuffer buffer) {
    int frameLength = buffer.getInt();
    if (frameLength < FRAME_HEADER_SIZE) {
      throw new IllegalArgumentException("Invalid frame length: " + frameLength);
    }

    byte apiKeyId = buffer.get();
    QueueApiKey apiKey = QueueApiKey.fromId(apiKeyId);
    if (apiKey != QueueApiKey.SUBSCRIBE_REQUEST) {
      throw new IllegalArgumentException("Unexpected api key for SubscribeRequest: " + apiKey);
    }

    byte apiVersion = buffer.get();
    int correlationId = buffer.getInt();
    QueueFrameHeader header = new QueueFrameHeader(apiKey, apiVersion, correlationId);

    String category = readString(buffer);
    String groupId = readString(buffer);
    String clientId = readString(buffer);

    return new SubscribeRequest(header, category, groupId, clientId);
  }

  public static ByteBuffer encodeSubscribeResponse(SubscribeResponse response) {
    int bodySize = 0;
    bodySize += Integer.BYTES; // errorCode
    bodySize += Integer.BYTES; // assignedQueueCount
    bodySize += Integer.BYTES * response.assignedQueueCount();

    int frameLength = FRAME_HEADER_SIZE + bodySize;

    ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + frameLength);
    buffer.putInt(frameLength);

    QueueFrameHeader header = response.header();
    buffer.put(header.apiKey().id());
    buffer.put(header.apiVersion());
    buffer.putInt(header.correlationId());

    buffer.putInt(response.errorCode());
    buffer.putInt(response.assignedQueueCount());
    for (int queueId : response.assignedQueues()) {
      buffer.putInt(queueId);
    }

    buffer.flip();
    return buffer;
  }

  public static SubscribeResponse decodeSubscribeResponse(ByteBuffer buffer) {
    int frameLength = buffer.getInt();
    if (frameLength < FRAME_HEADER_SIZE) {
      throw new IllegalArgumentException("Invalid frame length: " + frameLength);
    }

    byte apiKeyId = buffer.get();
    QueueApiKey apiKey = QueueApiKey.fromId(apiKeyId);
    if (apiKey != QueueApiKey.SUBSCRIBE_RESPONSE) {
      throw new IllegalArgumentException(
          "Unexpected api key for SubscribeResponse: " + apiKey);
    }

    byte apiVersion = buffer.get();
    int correlationId = buffer.getInt();
    QueueFrameHeader header = new QueueFrameHeader(apiKey, apiVersion, correlationId);

    int errorCode = buffer.getInt();
    int assignedQueueCount = buffer.getInt();
    if (assignedQueueCount < 0) {
      throw new IllegalArgumentException("Negative assignedQueueCount: " + assignedQueueCount);
    }

    List<Integer> queues = new ArrayList<>(assignedQueueCount);
    for (int i = 0; i < assignedQueueCount; i++) {
      queues.add(buffer.getInt());
    }

    return new SubscribeResponse(header, errorCode, queues);
  }

  public static ByteBuffer encodeHeartbeatRequest(HeartbeatRequest request) {
    byte[] categoryBytes = stringToBytes(request.category());
    byte[] groupBytes = stringToBytes(request.groupId());
    byte[] clientBytes = stringToBytes(request.clientId());

    int bodySize = 0;
    bodySize += Short.BYTES + categoryBytes.length; // category
    bodySize += Short.BYTES + groupBytes.length; // groupId
    bodySize += Short.BYTES + clientBytes.length; // clientId

    int frameLength = FRAME_HEADER_SIZE + bodySize;

    ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + frameLength);
    buffer.putInt(frameLength);

    QueueFrameHeader header = request.header();
    buffer.put(header.apiKey().id());
    buffer.put(header.apiVersion());
    buffer.putInt(header.correlationId());

    putString(buffer, categoryBytes);
    putString(buffer, groupBytes);
    putString(buffer, clientBytes);

    buffer.flip();
    return buffer;
  }

  public static HeartbeatRequest decodeHeartbeatRequest(ByteBuffer buffer) {
    int frameLength = buffer.getInt();
    if (frameLength < FRAME_HEADER_SIZE) {
      throw new IllegalArgumentException("Invalid frame length: " + frameLength);
    }

    byte apiKeyId = buffer.get();
    QueueApiKey apiKey = QueueApiKey.fromId(apiKeyId);
    if (apiKey != QueueApiKey.HEARTBEAT_REQUEST) {
      throw new IllegalArgumentException("Unexpected api key for HeartbeatRequest: " + apiKey);
    }

    byte apiVersion = buffer.get();
    int correlationId = buffer.getInt();
    QueueFrameHeader header = new QueueFrameHeader(apiKey, apiVersion, correlationId);

    String category = readString(buffer);
    String groupId = readString(buffer);
    String clientId = readString(buffer);

    return new HeartbeatRequest(header, category, groupId, clientId);
  }

  public static ByteBuffer encodeHeartbeatResponse(HeartbeatResponse response) {
    int assignedCount = response.assignedQueues() != null
        ? response.assignedQueues().size()
        : 0;

    int bodySize = 0;
    bodySize += Integer.BYTES; // errorCode
    bodySize += Integer.BYTES; // assignedQueueCount
    bodySize += Integer.BYTES * assignedCount; // queueIds

    int frameLength = FRAME_HEADER_SIZE + bodySize;

    ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + frameLength);
    buffer.putInt(frameLength);

    QueueFrameHeader header = response.header();
    buffer.put(header.apiKey().id());
    buffer.put(header.apiVersion());
    buffer.putInt(header.correlationId());

    buffer.putInt(response.errorCode());
    buffer.putInt(assignedCount);
    for (int i = 0; i < assignedCount; i++) {
      buffer.putInt(response.assignedQueues().get(i));
    }

    buffer.flip();
    return buffer;
  }

  public static HeartbeatResponse decodeHeartbeatResponse(ByteBuffer buffer) {
    int frameLength = buffer.getInt();
    if (frameLength < FRAME_HEADER_SIZE) {
      throw new IllegalArgumentException("Invalid frame length: " + frameLength);
    }

    byte apiKeyId = buffer.get();
    QueueApiKey apiKey = QueueApiKey.fromId(apiKeyId);
    if (apiKey != QueueApiKey.HEARTBEAT_RESPONSE) {
      throw new IllegalArgumentException(
          "Unexpected api key for HeartbeatResponse: " + apiKey);
    }

    byte apiVersion = buffer.get();
    int correlationId = buffer.getInt();
    QueueFrameHeader header = new QueueFrameHeader(apiKey, apiVersion, correlationId);

    int errorCode = buffer.getInt();

    int assignedQueueCount = buffer.getInt();
    if (assignedQueueCount < 0) {
      throw new IllegalArgumentException("Negative assignedQueueCount: " + assignedQueueCount);
    }

    java.util.List<Integer> queues = new java.util.ArrayList<>(assignedQueueCount);
    for (int i = 0; i < assignedQueueCount; i++) {
      queues.add(buffer.getInt());
    }

    return new HeartbeatResponse(header, errorCode, queues);
  }

  private static byte[] stringToBytes(String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > Short.MAX_VALUE) {
      throw new IllegalArgumentException("String too long: " + bytes.length);
    }
    return bytes;
  }

  private static byte[] stringToBytesNullable(String value) {
    if (value == null) {
      return new byte[0];
    }
    return stringToBytes(value);
  }

  private static void putString(ByteBuffer buffer, byte[] bytes) {
    buffer.putShort((short) bytes.length);
    if (bytes.length > 0) {
      buffer.put(bytes);
    }
  }

  private static String readString(ByteBuffer buffer) {
    int length = Short.toUnsignedInt(buffer.getShort());
    if (length == 0) {
      return "";
    }
    byte[] bytes = new byte[length];
    buffer.get(bytes);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static String readNullableString(ByteBuffer buffer) {
    int length = Short.toUnsignedInt(buffer.getShort());
    if (length == 0) {
      return null;
    }
    byte[] bytes = new byte[length];
    buffer.get(bytes);
    return new String(bytes, StandardCharsets.UTF_8);
  }
}
