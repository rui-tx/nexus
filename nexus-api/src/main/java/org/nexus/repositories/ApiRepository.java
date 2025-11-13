package org.nexus.repositories;

import jakarta.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import org.nexus.NexusDatabase;
import org.nexus.NexusExecutor;
import org.nexus.config.DatabaseConfig;
import org.nexus.dbconnector.DatabaseConnectorFactory;
import org.nexus.interfaces.DatabaseConnector;

@Singleton
public class ApiRepository {

  private final NexusDatabase db;

  public ApiRepository() {
    DatabaseConfig dbConfig = DatabaseConnectorFactory.getConfig("nexus-db-sqlite");
    DatabaseConnector connector = DatabaseConnectorFactory.create(dbConfig);
    this.db = new NexusDatabase(connector);
  }

  public CompletableFuture<Integer> getData(String name) {
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
