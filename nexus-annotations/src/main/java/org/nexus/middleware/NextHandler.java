package org.nexus.middleware;

import org.nexus.annotations.RequestContext;
import org.nexus.Response;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Represents the next handler in the middleware chain.
 */
@FunctionalInterface
public interface NextHandler {
    /**
     * Proceeds to the next middleware or route handler.
     * @return A CompletableFuture that completes with the response
     */
    CompletableFuture<Response<?>> next();
    
    /**
     * Gets the request context.
     */
    default RequestContext getContext() {
        throw new UnsupportedOperationException("Context not available in this handler");
    }
    
    /**
     * Creates a simple next handler with a supplier function.
     */
    static NextHandler of(Supplier<CompletableFuture<Response<?>>> supplier) {
        return new NextHandler() {
            @Override
            public CompletableFuture<Response<?>> next() {
                return supplier.get();
            }
        };
    }
    
    /**
     * Creates a simple next handler with a next function.
     */
    static NextHandler of(NextHandlerFunction next) {
        return new NextHandler() {
            @Override
            public CompletableFuture<Response<?>> next() {
                return next.next();
            }
        };
    }
    
    /**
     * Functional interface for the next handler function.
     */
    @FunctionalInterface
    interface NextHandlerFunction {
        CompletableFuture<Response<?>> next();
    }
}
