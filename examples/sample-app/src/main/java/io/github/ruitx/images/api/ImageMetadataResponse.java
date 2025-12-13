package io.github.ruitx.images.api;

import java.util.Map;

public record ImageMetadataResponse(String id,
                                    String status,
                                    String mimeType,
                                    Integer originalWidth,
                                    Integer originalHeight,
                                    Map<String, String> links) {

}
