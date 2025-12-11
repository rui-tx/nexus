package org.nexus.core.services;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import org.nexus.database.NexusDatabase;
import org.nexus.queue.domain.Message;
import org.nexus.queue.interfaces.MessageConsumer;
import org.nexus.queue.interfaces.QueueBroker;

@Singleton
public class QueueProcessor {

  private final MessageConsumer<byte[]> consumer;
  private final NexusDatabase db1;

  @Inject
  public QueueProcessor(QueueBroker broker, NexusDatabase db1) {
    this.consumer = broker.createConsumer("pkg-processor");
    this.db1 = db1;
  }

  @PostConstruct
  public void start() {
    consumer.subscribe("pkg.created", this::process);
  }

  private CompletableFuture<Void> process(Message<byte[]> message) {
    return CompletableFuture.runAsync(() -> {
      String id = message.key();
      String test = deserialize(message.payload());

      // Process
      db1.update("INSERT INTO logs (log) VALUES (?)", id);
      //System.out.println("Inserted log: " + id);
    });
  }

  private String deserialize(byte[] payload) {
    return null;
  }
}