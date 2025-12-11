package org.nexus.core.controllers;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.nexus.annotations.Mapping;
import org.nexus.commons.Response;
import org.nexus.commons.enums.HttpMethod;
import org.nexus.core.services.QueueTestsService;
import org.nexus.database.NexusDatabase;

@Singleton
public class QueueTestsController {

  private final QueueTestsService svc;
  private final NexusDatabase db1;

  @Inject
  public QueueTestsController(QueueTestsService svc, NexusDatabase db1) {
    this.svc = svc;
    this.db1 = db1;

    db1.update("""
        CREATE TABLE IF NOT EXISTS logs (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          log TEXT NULL
        )
        """);
  }

  @Mapping(type = HttpMethod.GET, endpoint = "/test")
  public CompletableFuture<Response<String>> createTest(
  ) {
    return svc.createTestPkg("test-pkg")
        .thenApply(result -> {
          //System.out.println("request done");
          return new Response<>(202, "PENDING");
        });
  }

  @Mapping(type = HttpMethod.GET, endpoint = "/result")
  public CompletableFuture<Response<List<String>>> resultTest() {
    return CompletableFuture.completedFuture(new Response<>(
        200, db1.query("SELECT COUNT(log) AS log_count FROM logs;",
        rs -> rs.getString("log_count"))));
  }
}
