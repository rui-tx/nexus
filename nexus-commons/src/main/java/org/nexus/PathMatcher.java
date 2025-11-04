package org.nexus;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Matches a request path against a route template that may contain colon-prefixed placeholders,
 * e.g. "/users/:id/profile".
 *
 * <p>Usage:
 * <pre>
 * PathMatcher.Result r = PathMatcher.match("/users/:id/profile", "/users/42/profile");
 * if (r.matches()) {
 *     Map<String,String> params = r.params();   // {id=42}
 * }
 * </pre>
 */
public final class PathMatcher {

  /**
   * One segment that is either a literal string or a ":name" placeholder
   */
  private static final Pattern SEGMENT = Pattern.compile("([^/:]+)|:([^/:]+)");

  private PathMatcher() {
  }   // utility class

  public static Result match(String template, String path) {
    // Normalize: ensure both start/end with '/' and remove double slashes
    template = normalise(template);
    path = normalise(path);

    String[] tmplSegs = template.split("/");
    String[] pathSegs = path.split("/");

    if (tmplSegs.length != pathSegs.length) {
      return Result.NO_MATCH;
    }

    StringBuilder regex = new StringBuilder("^");
    Map<String, String> params = new HashMap<>();

    for (int i = 0; i < tmplSegs.length; i++) {
      String t = tmplSegs[i];
      String p = pathSegs[i];

      if (t.isEmpty()) {                     // allow "//" -> "/"
        regex.append("/?");
        continue;
      }

      if (t.startsWith(":")) {
        // placeholder – capture any non-slash chars
        String name = t.substring(1);
        regex.append("(?<").append(name).append(">[^/]+)");
        params.put(name, p);
      } else {
        // literal segment – must match exactly
        regex.append(Pattern.quote(t));
      }
      regex.append(i < tmplSegs.length - 1 ? "/" : "$");
    }

    return Pattern.compile(regex.toString()).matcher(path).matches()
        ? new Result(true, params)
        : Result.NO_MATCH;
  }

  private static String normalise(String s) {
    return "/" + s.replaceAll("^/+|/+$", "").replaceAll("/+", "/");
  }

  public record Result(boolean matches, Map<String, String> params) {

    public static final Result NO_MATCH = new Result(false, Map.of());

    public Result(boolean matches, Map<String, String> params) {
      this.matches = matches;
      this.params = Map.copyOf(params);
    }
  }
}
