package io.github.ruitx.images;

import static io.github.ruitx.images.api.ImageController.API_VERSION;

import io.github.ruitx.images.api.ImageController.ApiVersion;
import io.github.ruitx.images.api.ImageMetadataResponse;
import io.github.ruitx.images.api.ImageUploadRequest;
import io.github.ruitx.images.messaging.ImageMessaging;
import io.github.ruitx.images.messaging.ImageProcessRequested;
import io.github.ruitx.images.storage.BucketStorage;
import io.netty.handler.codec.http.FullHttpResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.nexus.commons.CachedHttpResponse;
import org.nexus.commons.NexusStaticResponseRegistry;
import org.nexus.commons.Response;

@Singleton
public class ImageService {
  static {
    NexusStaticResponseRegistry.register(
        "ping-pong",
        new ApiVersion("sample-app", API_VERSION), 200
    );
  }

  private final ImageRepository repository;
  private final BucketStorage bucket;
  private final ImageMessaging messaging;

  @Inject
  public ImageService(ImageRepository repository, BucketStorage bucket, ImageMessaging messaging) {
    this.repository = repository;
    this.bucket = bucket;
    this.messaging = messaging;
  }

  public Response<ApiVersion> pong() {
    FullHttpResponse preComputed = NexusStaticResponseRegistry.get("ping-pong");
    return new CachedHttpResponse<>(preComputed);
  }

  public String upload(ImageUploadRequest request) {
    byte[] bytes = Base64.getDecoder().decode(request.getImageBase64());
    String mimeType = request.getMimeType();

    String id = UUID.randomUUID().toString();
    String baseKey = "images/" + id + "/";
    String originalKey = baseKey + "original_raw";

    bucket.save(originalKey, bytes, mimeType);
    repository.insertNew(id, originalKey, mimeType);

    messaging.send(new ImageProcessRequested(id, originalKey, mimeType));

    return id;
  }

  public ImageMetadataResponse getMetadata(String id) {
    ImageRecord record = repository.findById(id);
    if (record == null) {
      return null;
    }

    Map<String, String> links = new HashMap<>();
    String base = "/api/" + API_VERSION + "/images/" + id + "/download?size=";
    links.put("original", base + "original");
    links.put("originalClean", base + "original_clean");
    links.put("small", base + "small");
    links.put("medium", base + "medium");
    links.put("large", base + "large");

    return new ImageMetadataResponse(
        record.getId(),
        record.getStatus().name(),
        record.getMimeType(),
        record.getOriginalWidth(),
        record.getOriginalHeight(),
        links
    );
  }

  public ImageDownload getImage(String id, String size) {
    ImageRecord record = repository.findById(id);
    if (record == null) {
      return null;
    }

    String key;
    if ("original".equals(size)) {
      key = record.getOriginalKey();
    } else if ("original_clean".equals(size)) {
      key = record.getOriginalCleanKey();
    } else if ("small".equals(size)) {
      key = record.getSmallKey();
    } else if ("medium".equals(size)) {
      key = record.getMediumKey();
    } else if ("large".equals(size)) {
      key = record.getLargeKey();
    } else {
      return null;
    }

    if (key == null) {
      return null;
    }

    byte[] bytes = bucket.load(key);
    String base64 = Base64.getEncoder().encodeToString(bytes);
    return new ImageDownload(id, size, record.getMimeType(), base64);
  }

  public record ImageDownload(String id, String size, String mimeType, String dataBase64) {

  }
}
