package org.nexus.services;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import org.nexus.NexusDatabase;
import org.nexus.domain.Message;
import org.nexus.interfaces.MessageConsumer;
import org.nexus.interfaces.QueueBroker;

@Singleton
public class QueueProcessor {

  private final MessageConsumer<byte[]> consumer;
  private final NexusDatabase db1;

  @Inject
  public QueueProcessor(QueueBroker broker, NexusDatabase db1) {
    this.consumer = broker.createConsumer("pkg-processor-" + new Random().nextInt());
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

      // Process payment
      db1.update("INSERT INTO logs (log) VALUES (?)", id);
      System.out.println("Inserted log: " + id);
    });
  }

  private String deserialize(byte[] payload) {
    return null;
  }
}