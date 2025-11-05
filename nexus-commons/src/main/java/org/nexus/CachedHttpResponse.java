package org.nexus;

import io.netty.handler.codec.http.FullHttpResponse;

public class CachedHttpResponse extends Response<String> {

  private final FullHttpResponse cached;

  public CachedHttpResponse(FullHttpResponse cached) {
    super(0, null);
    this.cached = cached;
  }

  @Override
  public FullHttpResponse toHttpResponse() {
    return cached.retainedDuplicate(); // duplicate to avoid buffer reuse issues
  }
}
