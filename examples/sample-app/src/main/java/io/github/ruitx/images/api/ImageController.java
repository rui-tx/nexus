package io.github.ruitx.images.api;

import io.github.ruitx.images.ImageService;
import io.github.ruitx.images.ImageService.ImageDownload;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import org.nexus.annotations.Mapping;
import org.nexus.annotations.QueryParam;
import org.nexus.annotations.RequestBody;
import org.nexus.commons.NexusExecutor;
import org.nexus.commons.Response;
import org.nexus.commons.enums.HttpMethod;

@Singleton
public class ImageController {

  public static final String API_VERSION = "v1";
  private static final String BASE_URL = "/api/" + API_VERSION;

  private final ImageService service;

  @Inject
  public ImageController(ImageService service) {
    this.service = service;
  }

  @Mapping(type = HttpMethod.GET, endpoint = BASE_URL)
  public CompletableFuture<Response<ApiVersion>> ping() {
    return CompletableFuture.supplyAsync(service::pong, NexusExecutor.get());
  }

  @Mapping(type = HttpMethod.POST, endpoint = BASE_URL + "/images")
  public CompletableFuture<Response<UploadResponse>> upload(@RequestBody ImageUploadRequest request) {
    return CompletableFuture.supplyAsync(() -> {
      String id = service.upload(request);
      return new Response<>(202, new UploadResponse(id, "PENDING"));
    }, NexusExecutor.get());
  }

  @Mapping(type = HttpMethod.GET, endpoint = BASE_URL + "/images/:id")
  public CompletableFuture<Response<ImageMetadataResponse>> getMetadata(String id) {
    return CompletableFuture.supplyAsync(() -> {
      ImageMetadataResponse metadata = service.getMetadata(id);
      if (metadata == null) {
        return new Response<>(404, null);
      }
      return new Response<>(200, metadata);
    }, NexusExecutor.get());
  }

  @Mapping(type = HttpMethod.GET, endpoint = BASE_URL + "/images/:id/download")
  public CompletableFuture<Response<ImageDownload>> download(String id, @QueryParam("size") String size) {
    return CompletableFuture.supplyAsync(() -> {
      ImageDownload download = service.getImage(id, size);
      if (download == null) {
        return new Response<>(404, null);
      }
      return new Response<>(200, download);
    }, NexusExecutor.get());
  }

  public record ApiVersion(String api, String version) {
  }

  public record UploadResponse(String id, String status) {

  }
}
