package io.github.ruitx;

import static io.github.ruitx.beans.DatabaseFactory.DEFAULT_DB;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.nexus.NexusDatabase;
import org.nexus.exceptions.DatabaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Repository {

  private static final Logger LOGGER = LoggerFactory.getLogger(Repository.class);

  private final NexusDatabase db1;

  @Inject
  public Repository(@Named(DEFAULT_DB) NexusDatabase db1) {
    this.db1 = db1;
  }

  public Integer getSample(String name) {
    try {
      return db1.query(
          "SELECT COUNT(*) FROM test t WHERE t.name = ?",
          rs -> rs.getInt(1),
          name
      ).getFirst();

    } catch (DatabaseException e) {
      LOGGER.error(e.getMessage(), e);
      return null;
    }

  }

  public Boolean postSample(String name) {
    return db1.insert(
        "INSERT INTO test (name) VALUES (?)",
        _ -> true,
        name
    );
  }
}
