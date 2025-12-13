package io.github.ruitx.images.messaging;

import io.github.ruitx.images.ImageProcessor;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.nexus.commons.enums.MessagingMode;
import org.nexus.messaging.JsonMessageCodec;
import org.nexus.messaging.MessagingClients;
import org.nexus.messaging.domain.MessagingConfig;
import org.nexus.messaging.interfaces.MessageCodec;
import org.nexus.messaging.interfaces.MessagePublisher;
import org.nexus.messaging.interfaces.MessagingClient;
import org.nexus.messaging.interfaces.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ImageMessaging {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImageMessaging.class);

  private final MessagingClient client;
  private final MessagePublisher<ImageProcessRequested> publisher;
  private final Subscription subscription;

  @Inject
  public ImageMessaging(ImageProcessor processor) {
    String category = "image-processing";
    String groupId = "image-processing-workers";

    MessagingConfig config = new MessagingConfig(
        MessagingMode.EMBEDDED,
        "ignored-host",
        0,
        200_000
    );

    this.client = MessagingClients.create(config);
    MessageCodec<ImageProcessRequested> codec = new JsonMessageCodec<>(ImageProcessRequested.class);

    this.subscription = client.subscribe(
        category,
        groupId,
        codec,
        (payload, meta) -> {
          processor.process(payload);
        }
    );
    this.subscription.start();

    this.publisher = client.publisher(category, codec);
  }

  public void send(ImageProcessRequested payload) {
    publisher.send(payload.getImageId(), payload);
  }
}
