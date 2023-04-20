package org.fifthgen.secureproxy;

import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.proxy.ConnectHandler;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.eclipse.jetty.servlet.ServletContextHandler.NO_SESSIONS;

public class ProxyServer {

    private final int httpPort;
    private final int httpsPort;

    private String keyStore;
    private String keyStorePassword;

    private static final String WELCOME_FILE = "index.html";
    private static final String STATIC_FILE_PATH = "static";
    private static final String CA_CERTS_PATH = "cacerts";
    private static final String KEY_STORE = "defaultStore.p12";
    private static final String KEY_STORE_PASS = "root123";
    private static final String TRUST_STORE = "trustStore.p12";
    private static final String TRUST_STORE_PASS = "root123";
    private static final String[] WELCOME_FILES = new String[]{WELCOME_FILE};

    private final Set<String> whiteListedHosts = new HashSet<>(Arrays.asList(
            "detectportal.firefox.com:80",
            "twitter.com:443",
            "skvazy.com:443",
            "snowmoscow.ru:443"
    ));
    private static final Logger LOG = LoggerFactory.getLogger(ProxyServer.class);

    ProxyServer(int httpPort, int httpsPort) {
        this.httpPort = httpPort;
        this.httpsPort = httpsPort;
    }

    public String getKeyStore() {
        return keyStore;
    }

    public void setKeyStore(String keyStore) {
        this.keyStore = keyStore;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public void start() {
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setName("server-thread-pool");

        try {
            Server server = new Server(threadPool);
            ServerConnector connector = getConnector(server);
            ServerConnector sslConnector = getSecureConnector(server, httpsPort);

            // Non-secure request port
            connector.setPort(httpPort);

            // Secure request port
            sslConnector.setPort(httpsPort);

            server.addConnector(connector);
            server.addConnector(sslConnector);

            // Configure server handlers
            HandlerCollection allHandlers = new HandlerCollection();
            server.setHandler(allHandlers);

            // Handlers CONNECT requests, essential for HTTP proxying
            ConnectHandler connectHandler = new ConnectHandler();
            connectHandler.getWhiteListHosts().addAll(whiteListedHosts);
            allHandlers.addHandler(connectHandler);

            // GZIP handler
            GzipHandler gzipHandler = new GzipHandler();
            allHandlers.addHandler(gzipHandler);

            // Handler for about context
            ServletContextHandler aboutContext = new ServletContextHandler(allHandlers, "/about/", NO_SESSIONS);
            aboutContext.setWelcomeFiles(WELCOME_FILES);
            aboutContext.setBaseResource(Resource.newResource(resolveContextPath(STATIC_FILE_PATH + "/" + WELCOME_FILE)));
            aboutContext.addServlet(DefaultServlet.class, "/about");

            // Handler for root context, must be a proxy handler servlet
            ServletContextHandler rootContext = new ServletContextHandler(allHandlers, "/", NO_SESSIONS);

            SecureProxyServlet proxyServlet = new SecureProxyServlet(whiteListedHosts);

            ServletHolder proxyServletHolder = new ServletHolder(proxyServlet);
            rootContext.addServlet(proxyServletHolder, "/");

            server.start();
            server.join();
        } catch (Exception e) {
            System.getLogger("init").log(System.Logger.Level.ERROR, e.getLocalizedMessage());
        }
    }

    /**
     * Build a server connector for handling HTTP requests. Every protocol version you need to handle
     * needs to be added separately for each connector.
     *
     * @param server Jetty server instance
     * @return ServerConnector instance
     */
    public ServerConnector getConnector(Server server) {
        HttpConfiguration httpConfig = new HttpConfiguration();

        // Http/1.1 connection factory
        HttpConnectionFactory http11 = new HttpConnectionFactory(httpConfig);

        // Http/2 connection factory
        HTTP2CServerConnectionFactory h2c = new HTTP2CServerConnectionFactory(httpConfig);

        return new ServerConnector(server, http11, h2c);
    }

    /**
     * Build a server connector for SSL. Every protocol version you need to handle
     * needs to be added separately for each connector.
     *
     * @param server Jetty server instance
     * @param port   HTTPS port to listen to
     * @return ServerConnector instance with an SSL context
     */
    public ServerConnector getSecureConnector(Server server, int port) throws URISyntaxException {
        // Custom HTTP config object with TLS support
        HttpConfiguration httpsConfig = new HttpConfiguration();
        httpsConfig.setSecureScheme("https");
        httpsConfig.setSecurePort(port);

        // Disable SNI host check to access localhost
        var requestCustomizer = new SecureRequestCustomizer();
        requestCustomizer.setSniHostCheck(false);

        httpsConfig.addCustomizer(requestCustomizer);

        // Http/1.1 connection factory
        HttpConnectionFactory http11 = new HttpConnectionFactory(httpsConfig);

        // Http/2 connection factory
        HTTP2CServerConnectionFactory h2c = new HTTP2CServerConnectionFactory(httpsConfig);

        // Create custom SslContextFactory and disable certificate validation
        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setNeedClientAuth(false);
        sslContextFactory.setValidateCerts(false);
        sslContextFactory.setValidatePeerCerts(false);

        // Secure context and JAVA keystore configuration
        if (keyStore != null && !keyStore.isEmpty() && !keyStore.isBlank()) {
            sslContextFactory.setKeyStorePath(keyStore);

            // If a password is provided, set it else ignore.
            // Passing an empty string will make the password invalid.
            if (keyStorePassword != null && !keyStorePassword.isEmpty() && !keyStorePassword.isBlank()) {
                sslContextFactory.setKeyStorePassword(keyStorePassword);
            }
        } else {
            sslContextFactory.setKeyStorePath(resolveContextPath(CA_CERTS_PATH + "/" + KEY_STORE) + KEY_STORE);
            sslContextFactory.setKeyStorePassword(KEY_STORE_PASS);
        }

        // Connection factory for TLS
        SslConnectionFactory tls = new SslConnectionFactory(sslContextFactory, http11.getProtocol());

        return new ServerConnector(server, tls, http11, h2c);
    }

    /**
     * Resolve path via URI resolution. Needs to provide a file within the directory
     * as resolving directories is not supported.
     *
     * @param path Path of a file in the directory
     * @return Resolved context path as a {@link URI}
     */
    private URI resolveContextPath(String path) throws URISyntaxException, RuntimeException {
        ClassLoader loader = ProxyServer.class.getClassLoader();
        URL url = loader.getResource(path);

        if (url == null) {
            throw new RuntimeException("Unable to find resource directory");
        }

        URI webRootUri = url.toURI().resolve("./").normalize();
        LOG.info("Webroot is " + webRootUri);

        return webRootUri;
    }
}
