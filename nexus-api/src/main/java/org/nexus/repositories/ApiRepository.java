package org.nexus.repositories;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.nexus.NexusDatabase;
import org.nexus.NexusExecutor;
import org.nexus.annotations.Repository;
import org.nexus.dbConnector.PostgresConnector;
import org.nexus.interfaces.DatabaseConnector;
import org.nexus.model.Test;

@Repository
public class ApiRepository {

  //  DatabaseConnector connector = new SqliteConnector("db.db", false, 10);
  DatabaseConnector connector = new PostgresConnector(
      "localhost",
      5432,
      "nexus-db",
      "username",
      "password",
      25);
  NexusDatabase db = new NexusDatabase(connector);

  public ApiRepository() {
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
}
