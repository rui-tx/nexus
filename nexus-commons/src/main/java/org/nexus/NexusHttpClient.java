package org.nexus;

import java.net.http.HttpClient;

public enum NexusHttpClient {
  INSTANCE;

  private final HttpClient httpClient = HttpClient.newBuilder()
      .executor(NexusExecutor.INSTANCE.get())
      .build();

  public HttpClient get() {
    return httpClient;
  }
  
}
