package org.nexus.queue.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nexus.domain.BrokerStats;
import org.nexus.domain.CategoryStats;
import org.nexus.domain.ProducerConfig;
import org.nexus.domain.PublishResult;
import org.nexus.embedded.EmbeddedQueueBroker;
import org.nexus.interfaces.MessageProducer;

class RemoteQueueBrokerProduceTest {

  private EmbeddedQueueBroker broker;
  private QueueBrokerServer server;

  @BeforeEach
  void setUp() throws Exception {
    broker = new EmbeddedQueueBroker();
    server = new QueueBrokerServer(broker, "127.0.0.1", 0);
    server.start();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (server != null) {
      server.stop();
    }
    if (broker != null) {
      broker.shutdown().get(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void remoteProducer_publishSingleMessage() {
    BrokerStats before = broker.getStats();

    RemoteQueueBroker remoteBroker = new RemoteQueueBroker("127.0.0.1", server.getPort());

    MessageProducer<byte[]> producer = remoteBroker.createProducer(ProducerConfig.defaults());

    String category = "remote-produce-category";
    byte[] payload = "remote-hello".getBytes(StandardCharsets.UTF_8);

    PublishResult result = producer.send(category, payload).join();

    assertEquals(category, result.topic());
    assertTrue(result.partition() >= 0);
    assertTrue(result.offset() >= 0);
    assertNotNull(result.messageId());

    BrokerStats after = broker.getStats();

    assertEquals(before.totalMessages() + 1, after.totalMessages());

    CategoryStats beforeCategory = before.topicStats().get(category);
    long beforeCategoryMessages = beforeCategory != null ? beforeCategory.messageCount() : 0L;

    CategoryStats afterCategory = after.topicStats().get(category);
    assertNotNull(afterCategory);
    assertEquals(beforeCategoryMessages + 1, afterCategory.messageCount());
  }
}
