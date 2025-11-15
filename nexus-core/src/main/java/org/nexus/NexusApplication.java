package org.nexus;

import org.nexus.config.ServerConfig;
import org.nexus.server.NexusServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for Nexus applications.
 * Extend this class to create a new Nexus application.
 */
public abstract class NexusApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusApplication.class);
    
    private NexusServer server;
    private final NexusConfig config = NexusConfig.getInstance();
    
    /**
     * Starts the Nexus application.
     * @param args Command line arguments
     */
    public final void start(String[] args) {
        try {
            // Initialize configuration
            LOGGER.info("Initializing application...");
            config.init(args);

            // Initialize dependency injection
            NexusBeanScope.init();

            // Create server configuration
            ServerConfig serverConfig = createServerConfig();
            
            // Allow customization
            configure(serverConfig);
            
            // Initialize database connections if needed
            initializeDatabase();
            
            // Create and start server
            server = createServer(serverConfig);
            
            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "shutdown-thread"));
            
            // Start the server
            server.start();
            
        } catch (Exception e) {
            LOGGER.error("Failed to start application", e);
            stop();
            throw new RuntimeException("Failed to start application", e);
        }
    }
    
    /**
     * Creates the server configuration.
     * Can be overridden to provide custom configuration.
     */
    protected ServerConfig createServerConfig() {
        return ServerConfig.from(config);
    }
    
    /**
     * Configures the server before starting.
     * Override this method to customize server configuration.
     * @param config The server configuration to modify
     */
    protected void configure(ServerConfig config) {
        // Default implementation does nothing
    }
    
    /**
     * Initializes database connections.
     * Override this method to customize database initialization.
     */
    protected void initializeDatabase() {
        // Database connections are initialized on-demand by NexusConfig
    }
    
    /**
     * Creates the Nexus server instance.
     * Can be overridden to provide a custom server implementation.
     */
    protected NexusServer createServer(ServerConfig config) {
        return new NexusServer(config);
    }
    
    /**
     * Stops the application and releases resources.
     */
    public void stop() {
        LOGGER.info("Shutting down application...");
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                LOGGER.error("Error during shutdown", e);
            }
        }
        // Additional cleanup can be added here
    }

    public final void run(String[] args) {
        start(args);
        try {
            server.startAndAwait();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
