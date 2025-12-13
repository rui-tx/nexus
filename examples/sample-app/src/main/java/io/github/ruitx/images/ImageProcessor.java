package io.github.ruitx.images;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.nio.GifWriter;
import com.sksamuel.scrimage.nio.JpegWriter;
import com.sksamuel.scrimage.nio.PngWriter;
import io.github.ruitx.images.messaging.ImageProcessRequested;
import io.github.ruitx.images.storage.BucketStorage;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ImageProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImageProcessor.class);

  private final ImageRepository repository;
  private final BucketStorage bucket;

  @Inject
  public ImageProcessor(ImageRepository repository, BucketStorage bucket) {
    this.repository = repository;
    this.bucket = bucket;
  }

  public void process(ImageProcessRequested msg) {
    LOGGER.info(String.format("Processing image %s", msg.getOriginalKey()));
    String id = msg.getImageId();
    repository.markProcessing(id);
    try {
      byte[] originalBytes = bucket.load(msg.getOriginalKey());
      ImmutableImage original = ImmutableImage.loader().fromBytes(originalBytes);

      int width = original.width;
      int height = original.height;

      // Get appropriate writer based on format
      var writer = getWriter(msg.getMimeType());

      byte[] originalCleanBytes = original.bytes(writer);
      byte[] smallBytes = resize(original, 256).bytes(writer);
      byte[] mediumBytes = resize(original, 512).bytes(writer);
      byte[] largeBytes = resize(original, 1024).bytes(writer);

      String baseKey = "images/" + id + "/";
      String originalCleanKey = baseKey + "original_clean";
      String smallKey = baseKey + "small";
      String mediumKey = baseKey + "medium";
      String largeKey = baseKey + "large";

      bucket.save(originalCleanKey, originalCleanBytes, msg.getMimeType());
      bucket.save(smallKey, smallBytes, msg.getMimeType());
      bucket.save(mediumKey, mediumBytes, msg.getMimeType());
      bucket.save(largeKey, largeBytes, msg.getMimeType());

      repository.updateProcessed(
          id,
          originalCleanKey,
          smallKey,
          mediumKey,
          largeKey,
          width,
          height
      );

      LOGGER.info(String.format("Processed image %s done", msg.getOriginalKey()));
    } catch (Exception e) {
      LOGGER.error("Failed to process image {}", id, e);
      repository.markFailed(id, e.getMessage());
    }
  }

  private ImmutableImage resize(ImmutableImage src, int maxWidth) {
    if (src.width <= maxWidth) {
      return src;
    }
    return src.scaleToWidth(maxWidth);
  }

  private com.sksamuel.scrimage.nio.ImageWriter getWriter(String mimeType) {
    if (mimeType == null) {
      return PngWriter.MaxCompression;
    }
    return switch (mimeType.toLowerCase()) {
      case "image/jpeg", "image/jpg" -> JpegWriter.Default;
      case "image/png" -> PngWriter.MaxCompression;
      case "image/gif" -> GifWriter.Default;
      default -> PngWriter.MaxCompression;
    };
  }
}