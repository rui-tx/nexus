package org.nexus.queue.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.nexus.queue.Protocol;
import org.nexus.queue.domain.BinaryMessage;

/**
 * Simple append-only log for binary messages, backed by a single file.
 *
 * <p>Each record in the file is stored as:
 * <pre>
 *   [recordLength:int][recordBytes...]
 * </pre>
 * where {@code recordBytes} is {@link Protocol#encode(BinaryMessage)}.
 */
public final class FileLog implements AutoCloseable {

  private final Path path;
  private final FileChannel channel;

  public FileLog(Path path) {
    this.path = path;
    try {
      Path parent = path.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      this.channel = FileChannel.open(
          path,
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE,
          StandardOpenOption.APPEND
      );
    } catch (IOException e) {
      throw new RuntimeException("Failed to open log file: " + path, e);
    }
  }

  /**
   * Factory for a per-category, per-queue log file.
   */
  public static FileLog forCategoryQueue(String category, int queueId) {
    Path path = QueueStorage.logPath(category, queueId);
    return new FileLog(path);
  }

  /**
   * Append a single message to the log.
   */
  public synchronized void append(BinaryMessage message) {
    byte[] data = Protocol.encode(message);
    ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + data.length);
    buffer.putInt(data.length);
    buffer.put(data);
    buffer.flip();

    try {
      while (buffer.hasRemaining()) {
        channel.write(buffer);
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to append to log: " + path, e);
    }
  }

  /**
   * Force pending writes to disk. The {@code metadata} flag is passed directly to
   * {@link FileChannel#force(boolean)}.
   */
  public synchronized void force(boolean metadata) {
    try {
      channel.force(metadata);
    } catch (IOException e) {
      throw new RuntimeException("Failed to force log to disk: " + path, e);
    }
  }

  /**
   * Replay all messages from the beginning of the log.
   */
  public List<BinaryMessage> replayFromStart() {
    if (!Files.exists(path)) {
      return List.of();
    }

    List<BinaryMessage> result = new ArrayList<>();

    try (FileChannel readChannel = FileChannel.open(path, StandardOpenOption.READ)) {
      ByteBuffer lengthBuffer = ByteBuffer.allocate(Integer.BYTES);

      while (true) {
        lengthBuffer.clear();
        int read = readChannel.read(lengthBuffer);
        if (read == -1) {
          break; // EOF
        }
        if (read < Integer.BYTES) {
          // Truncated length field; stop replaying
          break;
        }
        lengthBuffer.flip();
        int length = lengthBuffer.getInt();
        if (length <= 0) {
          // Corrupt or invalid length; stop replaying
          break;
        }

        ByteBuffer dataBuffer = ByteBuffer.allocate(length);
        int bytesRead = readChannel.read(dataBuffer);
        if (bytesRead < length) {
          // Truncated record at end of file; stop replaying
          break;
        }
        dataBuffer.flip();
        byte[] data = new byte[length];
        dataBuffer.get(data);

        BinaryMessage message = Protocol.decodeVersioned(data);
        result.add(message);
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to replay log: " + path, e);
    }

    return result;
  }

  @Override
  public void close() {
    try {
      channel.close();
    } catch (IOException e) {
      System.err.println("Failed to close log file: " + path + ": " + e.getMessage());
    }
  }
}
