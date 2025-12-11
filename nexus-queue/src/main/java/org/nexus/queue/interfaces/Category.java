package org.nexus.queue.interfaces;

import org.nexus.queue.domain.CategoryConfig;
import org.nexus.queue.domain.CategoryStats;

public interface Category {

  String name();

  CategoryConfig config();

  int queueCount();

  CategoryStats stats();
}
