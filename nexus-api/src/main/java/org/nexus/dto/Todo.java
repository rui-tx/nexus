package org.nexus.dto;

import java.util.ArrayList;
import java.util.List;

public record Todo(int userId, int id, String title, boolean completed) {

  public static List<Todo> emptyList() {
    return new ArrayList<>();
  }

}
