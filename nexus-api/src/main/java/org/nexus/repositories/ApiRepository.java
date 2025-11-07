package org.nexus.repositories;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.nexus.NexusDatabase;
import org.nexus.NexusExecutor;
import org.nexus.annotations.Repository;
import org.nexus.config.DatabaseConfig;
import org.nexus.dbConnector.DatabaseConnectorFactory;
import org.nexus.interfaces.DatabaseConnector;
import org.nexus.model.Test;

@Repository
public class ApiRepository {

  private final NexusDatabase db;

  public ApiRepository() {
    DatabaseConfig dbConfig = DatabaseConnectorFactory.getConfig("nexus-db-postgresql");
    DatabaseConnector connector = DatabaseConnectorFactory.create(dbConfig);
    this.db = new NexusDatabase(connector);
  }

  public CompletableFuture<List<Test>> getData(String name) {
    return CompletableFuture.supplyAsync(() -> {
      db.insert(
          "INSERT INTO test (name) VALUES (?)",
          rs -> rs.getInt(1),
          name
      );

      return db.query(
          """
              SELECT
                t.id, t.name
              FROM
                test t
              WHERE t.name = ?;""",
          rs -> new Test(rs.getLong("id"), rs.getString("name")),
          name
      );
    }, NexusExecutor.INSTANCE.get());
  }

  public CompletableFuture<List<Test>> getDataSelect(String name) {
    return CompletableFuture.supplyAsync(() -> {
      return db.query(
          "SELECT t.id, t.name FROM test t WHERE t.name = ? LIMIT 1000",
          rs -> new Test(rs.getLong("id"), rs.getString("name")),
          name
      );
    }, NexusExecutor.INSTANCE.get());
  }
}
