package org.fifthgen.secureproxy;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.dynamic.HttpClientTransportDynamic;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.proxy.ProxyServlet;
import org.eclipse.jetty.util.HttpCookieStore;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

public class SecureProxyServlet extends ProxyServlet {

    private final Set<String> whiteListedHosts;
    private boolean sendAllViaHTTPS = false;
    private static final Logger LOG = LoggerFactory.getLogger(SecureProxyServlet.class);

    public SecureProxyServlet(Set<String> whitelistedDomains) {
        this.whiteListedHosts = whitelistedDomains;
    }

    public boolean isSendAllViaHTTPS() {
        return sendAllViaHTTPS;
    }

    public void setSendAllViaHTTPS(boolean sendAllViaHTTPS) {
        this.sendAllViaHTTPS = sendAllViaHTTPS;
    }

    @Override
    protected HttpClient createHttpClient() {
        // Create an instance of SslContextFactory with custom TLS parameters.
        var sslContextFactory = new SslContextFactory.Client();
        sslContextFactory.setExcludeProtocols("TLSv1.2");
        sslContextFactory.setExcludeProtocols("TLSv1.1");
        sslContextFactory.setIncludeProtocols("TLSv1.3");

        // Connector to house SSL context
        ClientConnector clientConnector = new ClientConnector();
        clientConnector.setSslContextFactory(sslContextFactory);

        // Create an instance of HttpClient with the secure client connector
        HttpClient httpClient = new HttpClient(new HttpClientTransportDynamic(clientConnector));
        // Redirects must be proxied and not followed
        httpClient.setFollowRedirects(false);
        // Must not store cookies for the cookies of different clients will mix
        httpClient.setCookieStore(new HttpCookieStore.Empty());

        try {
            httpClient.start();
            httpClient.getContentDecoderFactories().clear();
        } catch (Exception e) {
            LOG.error("Failed to start proxy http client: " + e.getLocalizedMessage());
            LOG.error("Cause: " + e.getCause());
        }

        return httpClient;
    }

    @Override
    protected void sendProxyRequest(HttpServletRequest clientRequest, HttpServletResponse proxyResponse, Request proxyRequest) {
        if(!whiteListedHosts.contains(proxyRequest.getHost())) {
            LOG.warn("Host " + proxyRequest.getHost() + " is not in the whitelisted hosts. Ignoring request!");
        }

        try {
            boolean rewrite = sendAllViaHTTPS || proxyRequest.getScheme().equals("https");

            if (rewrite) {
                URI rewrittenURI = rewriteForSSL(clientRequest);

                proxyRequest.scheme(rewrittenURI.getScheme());
                proxyRequest.host(rewrittenURI.getHost());
                proxyRequest.port(rewrittenURI.getPort());
                proxyRequest.path(rewrittenURI.getPath());
            }
        } catch (URISyntaxException e) {
            LOG.error("Failed to rewrite request URI: " + e.getLocalizedMessage());
            LOG.error("Cause: " + e.getCause());
        }

        super.sendProxyRequest(clientRequest, proxyResponse, proxyRequest);
    }


    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        super.service(request, response);
    }

    /**
     * Rewrite requests to use SSL
     *
     * @param request Servlet request to with the request information
     * @return Rewritten URI
     */
    private URI rewriteForSSL(HttpServletRequest request) throws URISyntaxException {
        String host = request.getServerName().toLowerCase();
        int port = request.getServerPort();
        String file = request.getRequestURI();
        String queryString = request.getQueryString();

        LOG.debug("Host: " + host);
        LOG.debug("Port: " + port);
        LOG.debug("Request URL: " + file);

        return new URI("https", null, host, 443, file, queryString, null);
    }
}
