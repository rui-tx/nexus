package org.nexus.repositories;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import org.nexus.NexusDatabase;
import org.nexus.NexusExecutor;

@Singleton
public class ApiRepository {

  private final NexusDatabase db;

  @Inject
  public ApiRepository(NexusDatabase db) {
    this.db = db;
  }

  public CompletableFuture<Integer> getExample(String name) {
    return CompletableFuture.supplyAsync(() -> {
      db.insert(
          "INSERT INTO test (name) VALUES (?)",
          _ -> true,
          name
      );

      return db.query(
          "SELECT COUNT(*) FROM test t WHERE t.name = ?",
          rs -> rs.getInt(1),
          name
      ).getFirst();
    }, NexusExecutor.INSTANCE.get());
  }

  public record Test(long id, String name) {

  }
}
