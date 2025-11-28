package org.nexus.interfaces;

import org.nexus.domain.TopicConfig;
import org.nexus.domain.TopicStats;

/**
 * Represents a topic in the queue system
 */
public interface Topic {

  String name();

  TopicConfig config();

  int partitionCount();

  /**
   * Get statistics about this topic
   */
  TopicStats stats();
}
