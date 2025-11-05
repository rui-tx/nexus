package org.nexus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
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

  public static String normalise(String s) {
    if (s == null || s.isEmpty()) {
      return "/";
    }

    StringBuilder sb = new StringBuilder(s.length());
    boolean inSlash = false;
    boolean started = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '/') {
        if (!started) {
          continue; // Skip leading slashes
        }
        if (inSlash) {
          continue; // Collapse multiple slashes
        }
        inSlash = true;
        sb.append('/');
      } else {
        started = true;
        inSlash = false;
        sb.append(c);
      }
    }

    // Remove trailing slash if present and not the only character
    int len = sb.length();
    if (len > 1 && sb.charAt(len - 1) == '/') {
      sb.setLength(len - 1);
    }

    // Prepend '/' if the result is not empty
    if (!sb.isEmpty() && sb.charAt(0) != '/') {
      sb.insert(0, '/');
    } else if (sb.isEmpty()) {
      sb.append('/');
    }
    return sb.toString();
  }

  public record Result(boolean matches, Map<String, String> params) {

    public static final Result NO_MATCH = new Result(false, Map.of());

    public Result(boolean matches, Map<String, String> params) {
      this.matches = matches;
      this.params = Map.copyOf(params);
    }
  }

  public record CompiledPattern(Pattern pattern, List<String> paramNames) {

    /**
     * Factory to compile a template into a CompiledPattern (called once at startup).
     */
    public static CompiledPattern compile(String template) {
      template = normalise(template);

      String[] tmplSegs = template.split("/");
      StringBuilder regex = new StringBuilder("^");
      List<String> paramNames = new ArrayList<>();

      for (int i = 0; i < tmplSegs.length; i++) {
        String t = tmplSegs[i];
        if (t.isEmpty()) {
          regex.append("/?");
          continue;
        }
        if (t.startsWith(":")) {
          String name = t.substring(1);
          regex.append("(?<").append(name).append(">[^/]+)");
          paramNames.add(name);
        } else {
          regex.append(Pattern.quote(t));
        }
        if (i < tmplSegs.length - 1) {
          regex.append("/");
        } else {
          regex.append("$");
        }
      }

      Pattern p = Pattern.compile(regex.toString());
      return new CompiledPattern(p, paramNames);
    }

    /**
     * Attempts to match the path against this precompiled pattern.
     *
     * @return Result with matches and extracted params, or NO_MATCH.
     */
    public Result match(String path) {
      Matcher matcher = pattern.matcher(path);
      if (!matcher.matches()) {
        return Result.NO_MATCH;
      }

      Map<String, String> params = new HashMap<>();
      for (String name : paramNames) {
        params.put(name, matcher.group(name));
      }
      return new Result(true, params);
    }
  }
}
