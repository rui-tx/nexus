package org.nexus.services;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import org.nexus.embedded.EmbeddedQueueBroker;
import org.nexus.interfaces.MessageProducer;
import org.nexus.interfaces.QueueBroker;

@Singleton
public class QueueTestsService {

  private final EmbeddedQueueBroker broker;
  private final MessageProducer<byte[]> producer;

  @Inject
  public QueueTestsService(QueueBroker broker1, QueueBroker broker) {
    this.broker = (EmbeddedQueueBroker) broker1;
    this.producer = broker.createProducer();
  }

  public CompletableFuture<String> createTestPkg(String pkg) {

    String pkgId = saveToDatabase(pkg);

    byte[] payload = serialize(pkg);
    return producer.send("pkg.created", pkgId, payload)
        .thenApply(_ -> broker.getStats().toString());
  }

  private String saveToDatabase(String pkg) {
    return "ORD-" + System.currentTimeMillis();
  }

  private byte[] serialize(String pkg) {
    return pkg.getBytes();
  }
}