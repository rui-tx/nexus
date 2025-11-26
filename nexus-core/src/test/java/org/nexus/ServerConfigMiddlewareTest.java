package org.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.nexus.config.ServerConfig;
import org.nexus.interfaces.Middleware;
import org.nexus.interfaces.MiddlewareChain;

class ServerConfigMiddlewareTest {

  @Test
  void testSingleMiddlewareVarargs() {
    CountingMiddleware middleware = new CountingMiddleware();

    ServerConfig config = ServerConfig.builder()
        .port(8080)
        .middlewares(middleware)
        .build();

    List<Middleware> middlewares = config.getMiddlewares();
    assertNotNull(middlewares);
    assertEquals(1, middlewares.size());
    assertSame(middleware, middlewares.getFirst());
  }

  @Test
  void testMultipleMiddlewaresVarargs() {
    CountingMiddleware m1 = new CountingMiddleware();
    CountingMiddleware m2 = new CountingMiddleware();
    CountingMiddleware m3 = new CountingMiddleware();

    ServerConfig config = ServerConfig.builder()
        .port(8080)
        .middlewares(m1, m2, m3)
        .build();

    List<Middleware> middlewares = config.getMiddlewares();
    assertEquals(3, middlewares.size());
    assertSame(m1, middlewares.get(0));
    assertSame(m2, middlewares.get(1));
    assertSame(m3, middlewares.get(2));
  }

  @Test
  void testMiddlewaresList() {
    CountingMiddleware m1 = new CountingMiddleware();
    CountingMiddleware m2 = new CountingMiddleware();

    List<Middleware> inputList = List.of(m1, m2);

    ServerConfig config = ServerConfig.builder()
        .port(8080)
        .middlewares(inputList)
        .build();

    List<Middleware> middlewares = config.getMiddlewares();
    assertEquals(2, middlewares.size());
    assertSame(m1, middlewares.get(0));
    assertSame(m2, middlewares.get(1));
  }

  @Test
  void testMiddlewaresOrderIsPreserved() {
    List<String> executionOrder = new ArrayList<>();

    Middleware m1 = new OrderTrackingMiddleware("first", executionOrder);
    Middleware m2 = new OrderTrackingMiddleware("second", executionOrder);
    Middleware m3 = new OrderTrackingMiddleware("third", executionOrder);

    ServerConfig config = ServerConfig.builder()
        .port(8080)
        .middlewares(m1, m2, m3)
        .build();

    List<Middleware> middlewares = config.getMiddlewares();
    assertEquals(3, middlewares.size());

    // Verify the order is preserved
    assertSame(m1, middlewares.get(0));
    assertSame(m2, middlewares.get(1));
    assertSame(m3, middlewares.get(2));
  }

  @Test
  void testChainedMiddlewareBuilderCalls() {
    CountingMiddleware m1 = new CountingMiddleware();
    CountingMiddleware m2 = new CountingMiddleware();
    CountingMiddleware m3 = new CountingMiddleware();
    CountingMiddleware m4 = new CountingMiddleware();

    ServerConfig config = ServerConfig.builder()
        .port(8080)
        .middlewares(m1, m2)
        .middlewares(m3)
        .middlewares(List.of(m4))
        .build();

    List<Middleware> middlewares = config.getMiddlewares();
    assertEquals(4, middlewares.size());
    assertSame(m1, middlewares.get(0));
    assertSame(m2, middlewares.get(1));
    assertSame(m3, middlewares.get(2));
    assertSame(m4, middlewares.get(3));
  }

  @Test
  void testEmptyMiddlewaresVarargs() {
    ServerConfig config = ServerConfig.builder()
        .port(8080)
        .middlewares() // Empty varargs
        .build();

    List<Middleware> middlewares = config.getMiddlewares();
    assertNotNull(middlewares);
    assertTrue(middlewares.isEmpty());
  }

  @Test
  void testEmptyMiddlewaresList() {
    ServerConfig config = ServerConfig.builder()
        .port(8080)
        .middlewares(List.of())
        .build();

    List<Middleware> middlewares = config.getMiddlewares();
    assertNotNull(middlewares);
    assertTrue(middlewares.isEmpty());
  }

  @Test
  void testNullMiddlewareInVarargsThrowsException() {
    ServerConfig.Builder builder = ServerConfig.builder().port(8080);
    CountingMiddleware validMiddleware = new CountingMiddleware();

    assertThrows(NullPointerException.class, () ->
        builder.middlewares(validMiddleware, null));
  }

  @Test
  void testNullMiddlewaresListThrowsException() {
    ServerConfig.Builder builder = ServerConfig.builder().port(8080);

    assertThrows(NullPointerException.class, () ->
        builder.middlewares((List<Middleware>) null));
  }

  @Test
  void testMiddlewareListIsImmutableAfterBuild() {
    CountingMiddleware m1 = new CountingMiddleware();

    ServerConfig config = ServerConfig.builder()
        .port(8080)
        .middlewares(m1)
        .build();

    List<Middleware> middlewares = config.getMiddlewares();
    CountingMiddleware newMiddleware = new CountingMiddleware();

    // Try to modify the returned list - should throw exception
    assertThrows(UnsupportedOperationException.class, () ->
        middlewares.add(newMiddleware));
  }

  @Test
  void testSingleMiddlewareMethod() {
    CountingMiddleware middleware = new CountingMiddleware();

    // Assuming there's a single middleware method like in TestNexusApplication
    ServerConfig config = ServerConfig.builder()
        .port(8080)
        .middleware(middleware)
        .build();

    List<Middleware> middlewares = config.getMiddlewares();
    assertEquals(1, middlewares.size());
    assertSame(middleware, middlewares.getFirst());
  }

  @Test
  void testMixingSingleAndMultipleMiddlewareMethods() {
    CountingMiddleware m1 = new CountingMiddleware();
    CountingMiddleware m2 = new CountingMiddleware();
    CountingMiddleware m3 = new CountingMiddleware();

    ServerConfig config = ServerConfig.builder()
        .port(8080)
        .middleware(m1)
        .middlewares(m2, m3)
        .build();

    List<Middleware> middlewares = config.getMiddlewares();
    assertEquals(3, middlewares.size());
    assertSame(m1, middlewares.get(0));
    assertSame(m2, middlewares.get(1));
    assertSame(m3, middlewares.get(2));
  }

  @Test
  void testBuilderReturnsThis() {
    ServerConfig.Builder builder = ServerConfig.builder();
    CountingMiddleware m1 = new CountingMiddleware();

    ServerConfig.Builder returned = builder.middlewares(m1);

    assertSame(builder, returned, "Builder should return 'this' for method chaining");
  }

  @Test
  void testBuilderReturnsThisForList() {
    ServerConfig.Builder builder = ServerConfig.builder();

    ServerConfig.Builder returned = builder.middlewares(List.of(new CountingMiddleware()));

    assertSame(builder, returned, "Builder should return 'this' for method chaining");
  }

  @Test
  void testMutableListDoesNotAffectConfig() {
    CountingMiddleware m1 = new CountingMiddleware();
    List<Middleware> mutableList = new ArrayList<>();
    mutableList.add(m1);

    ServerConfig config = ServerConfig.builder()
        .port(8080)
        .middlewares(mutableList)
        .build();

    // Modify the original list
    mutableList.add(new CountingMiddleware());

    // Config should not be affected
    List<Middleware> middlewares = config.getMiddlewares();
    assertEquals(1, middlewares.size());
    assertSame(m1, middlewares.getFirst());
  }

  @Test
  void testDifferentMiddlewareImplementations() {
    Middleware lambda = (ctx, next) -> next.next(ctx);
    Middleware counting = new CountingMiddleware();

    ServerConfig config = ServerConfig.builder()
        .port(8080)
        .middlewares(lambda, counting)
        .build();

    List<Middleware> middlewares = config.getMiddlewares();
    assertEquals(2, middlewares.size());
    assertSame(lambda, middlewares.get(0));
    assertSame(counting, middlewares.get(1));
  }

  /**
   * Simple test middleware that tracks execution order
   */
  record OrderTrackingMiddleware(String name, List<String> executionOrder) implements Middleware {

    @Override
    public void handle(RequestContext ctx, MiddlewareChain next) throws Exception {
      executionOrder.add(name);
      next.next(ctx);
    }
  }

  /**
   * Middleware that counts how many times it's called
   */
  static class CountingMiddleware implements Middleware {

    private final AtomicInteger callCount = new AtomicInteger(0);

    @Override
    public void handle(RequestContext ctx, MiddlewareChain next) throws Exception {
      callCount.incrementAndGet();
      next.next(ctx);
    }
  }
}