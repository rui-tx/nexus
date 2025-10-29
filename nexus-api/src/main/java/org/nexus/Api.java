package org.nexus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.nexus.annotations.Mapping;
import org.nexus.enums.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Api {

  private static final Logger LOGGER = LoggerFactory.getLogger(Api.class);
  private static final String ENDPOINT = "/api/v1";

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

  @Mapping(type = HttpMethod.GET, endpoint = ENDPOINT + "/heartbeat")
  public CompletableFuture<Response<String>> pong() {
    CompletableFuture<Response<String>> future = new CompletableFuture<>();
    future.complete(new Response<>(200, "up"));
    return future;
  }

  @Mapping(type = HttpMethod.GET, endpoint = ENDPOINT + "/primes/:number")
  public CompletableFuture<Response<String>> primes(int number) {
    long startTime = System.currentTimeMillis();

    return calculatePrimesAsync(number, Runtime.getRuntime().availableProcessors())
        .thenApply(primes -> {
          long duration = System.currentTimeMillis() - startTime;
          return new Response<>(
              200,
              "Found %s primes in %dms".formatted(primes.size(), duration)
          );
        })
        .exceptionally(ex -> {
          LOGGER.error("Error calculating primes", ex);
          return new Response<>(500, "Error calculating primes: " + ex.getMessage());
        });
  }

  private CompletableFuture<List<Integer>> calculatePrimesAsync(int n, int threadCount) {
    List<CompletableFuture<List<Integer>>> futures = new ArrayList<>();
    int chunkSize = n / threadCount;

    for (int i = 0; i < threadCount; i++) {
      final int start = i * chunkSize + 1;
      final int end = (i == threadCount - 1) ? n : (i + 1) * chunkSize;

      CompletableFuture<List<Integer>> future = CompletableFuture.supplyAsync(() -> {
        List<Integer> primes = new ArrayList<>();
        for (int num = start; num <= end; num++) {
          if (isPrime(num)) {
            primes.add(num);
          }
        }
        return primes;
      }, NexusExecutor.INSTANCE.get());

      futures.add(future);
    }

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .thenApply(v -> {
          List<Integer> allPrimes = new ArrayList<>();
          futures.forEach(f -> allPrimes.addAll(f.join()));
          return allPrimes;
        });
  }
}