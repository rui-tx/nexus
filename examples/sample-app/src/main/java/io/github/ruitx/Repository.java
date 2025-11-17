package io.github.ruitx;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.nexus.NexusDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Repository {

  private static final Logger LOGGER = LoggerFactory.getLogger(Repository.class);

  private final NexusDatabase db;

  @Inject
  public Repository(NexusDatabase db) {
    this.db = db;
  }

  public Integer getSample(String name) {
    return db.query(
        "SELECT COUNT(*) FROM test t WHERE t.name = ?",
        rs -> rs.getInt(1),
        name
    ).getFirst();
  }

  public Boolean postSample(String name) {
    return db.insert(
        "INSERT INTO test (name) VALUES (?)",
        _ -> true,
        name
    );
  }
}


