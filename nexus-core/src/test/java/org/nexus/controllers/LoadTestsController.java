package org.nexus.controllers;

import jakarta.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import org.nexus.NexusExecutor;
import org.nexus.Response;
import org.nexus.annotations.Mapping;
import org.nexus.enums.HttpMethod;

@Singleton
public class LoadTestsController {

  private static boolean isPrime(final int num) {
    if (num <= 1) {
      return false;
    }
    if (num <= 3) {
      return true;
    }
    if (num % 2 == 0 || num % 3 == 0) {
      return false;
    }
    for (int i = 5; (long) i * i <= num; i += 6) {
      if (num % i == 0 || num % (i + 2) == 0) {
        return false;
      }
    }
    return true;
  }

  private static int countPrimes(int n) {
    int count = 0;
    for (int i = 2; i <= n; i++) {
      if (isPrime(i)) {
        count++;
      }
    }
    return count;
  }

  @Mapping(type = HttpMethod.GET, endpoint = "/primes/:number")
  public CompletableFuture<Response<String>> primes(int number) {
    return CompletableFuture.supplyAsync(() -> countPrimes(number), NexusExecutor.INSTANCE.get())
        .thenApply(count -> new Response<>(200, "Found %s primes".formatted(count)));
  }
}
