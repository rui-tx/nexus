package org.nexus;

import java.util.ServiceLoader;
import org.nexus.domain.SecurityRule;

/**
 * Resolves the SecurityRulesProvider at runtime via ServiceLoader. No compile-time dependency on
 * the generated provider.
 */
public final class SecurityResolver {

  private SecurityResolver() {
  }

  public static SecurityRule getRule(String method, String path) {
    SecurityRulesProvider provider = ProviderHolder.INSTANCE;
    return provider != null ? provider.getRule(method, path) : null;
  }

  public interface SecurityRulesProvider {

    SecurityRule getRule(String method, String path);
  }

  private static final class ProviderHolder {

    static final SecurityRulesProvider INSTANCE = loadProvider();

    private static SecurityRulesProvider loadProvider() {
      return ServiceLoader.load(SecurityRulesProvider.class)
          .findFirst()
          .orElse(null);
    }
  }
}