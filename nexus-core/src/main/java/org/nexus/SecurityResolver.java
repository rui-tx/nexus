package org.nexus;

import java.util.ServiceLoader;

public final class SecurityResolver {

  private static volatile SecurityRulesProvider provider;

  private SecurityResolver() {}

  private static SecurityRulesProvider load() {
    SecurityRulesProvider p = provider;
    if (p != null) return p;
    synchronized (SecurityResolver.class) {
      if (provider != null) return provider;
      ServiceLoader<SecurityRulesProvider> loader = ServiceLoader.load(SecurityRulesProvider.class);
      for (SecurityRulesProvider rp : loader) {
        provider = rp;
        break;
      }
      return provider;
    }
  }

  public static SecurityRule getRule(String method, String path) {
    SecurityRulesProvider p = load();
    return (p == null) ? null : p.getRule(method, path);
  }

  public interface SecurityRulesProvider {
    SecurityRule getRule(String method, String path);
  }
}
