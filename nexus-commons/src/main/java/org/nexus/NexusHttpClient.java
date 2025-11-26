package org.nexus;

import java.net.http.HttpClient;

public final class NexusHttpClient {

  private NexusHttpClient() {
  }

  public static HttpClient get() {
    return InstanceHolder.instance;
  }

  private static final class InstanceHolder {

    private static final HttpClient instance = HttpClient.newBuilder()
        .executor(NexusExecutor.get())
        .build();
  }
}
