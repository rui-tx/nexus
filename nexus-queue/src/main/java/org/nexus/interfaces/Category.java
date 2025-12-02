package org.nexus.interfaces;

import org.nexus.domain.CategoryConfig;
import org.nexus.domain.CategoryStats;

public interface Category {

  String name();

  CategoryConfig config();

  int queueCount();

  CategoryStats stats();
}
