package org.nexus.domain;

import java.util.Map;

/**
 * Input provided by producers before queue assignment and offset allocation.
 */
public record MessageInput(
    String category,
    String key,
    Map<String, String> headers
) {

  public MessageInput {
    if (category == null || category.isEmpty()) {
      throw new IllegalArgumentException("category cannot be null or empty");
    }
    if (headers == null) {
      headers = Map.of();
    }
  }

  /**
   * Create a simple message input with just a category
   */
  public static MessageInput create(String category) {
    return new MessageInput(category, null, Map.of());
  }

  /**
   * Create a message input with category and key
   */
  public static MessageInput create(String category, String key) {
    return new MessageInput(category, key, Map.of());
  }

  /**
   * Create a message input with category, key, and headers
   */
  public static MessageInput create(String category, String key, Map<String, String> headers) {
    return new MessageInput(category, key, headers);
  }
}
