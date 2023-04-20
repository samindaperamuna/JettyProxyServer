package org.fifthgen.proxytest;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.dynamic.HttpClientTransportDynamic;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.proxy.AsyncProxyServlet;
import org.eclipse.jetty.util.HttpCookieStore;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class ProxyServletWithTLSParams extends AsyncProxyServlet {


    private String trustStorePath;
    private String trustStorePassword;
    private static final Logger LOG = LoggerFactory.getLogger(ProxyServletWithTLSParams.class);

    public String getTrustStorePath() {
        return trustStorePath;
    }

    public void setTrustStorePath(String trustStorePath) {
        this.trustStorePath = trustStorePath;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    @Override
    public void init() throws ServletException {
        super.init();
    }

    @Override
    protected HttpClient createHttpClient() {
        // Create an instance of SslContextFactory with custom TLS parameters.
        var sslContextFactory = new SslContextFactory.Client();
        sslContextFactory.setExcludeProtocols("TLSv1.2");
        sslContextFactory.setExcludeProtocols("TLSv1.1");
        sslContextFactory.setIncludeProtocols("TLSv1.3");

        // Disable certificate validation at the TLS level.
        // sslContextFactory.setTrustAll(true);
        //sslContextFactory.setEndpointIdentificationAlgorithm(null);
        //sslContextFactory.setValidateCerts(false);
        //sslContextFactory.setValidatePeerCerts(false);

        // Secure context and JAVA keystore configuration
//        if (trustStorePath != null && !trustStorePath.isEmpty() && !trustStorePath.isBlank()) {
//            sslContextFactory.setTrustStorePath(trustStorePath);
//
//            // If a password is provided, set it else ignore.
//            // Passing an empty string will make the password invalid.
//            if (trustStorePassword != null && !trustStorePassword.isEmpty() && !trustStorePassword.isBlank()) {
//                sslContextFactory.setTrustStorePassword(trustStorePassword);
//            }
//        }

        ClientConnector clientConnector = new ClientConnector();
        clientConnector.setSslContextFactory(sslContextFactory);

        // Create an instance of HttpClient with the clientConnector.
        HttpClient httpClient = new HttpClient(new HttpClientTransportDynamic(clientConnector));
        httpClient.setFollowRedirects(false);
        httpClient.setCookieStore(new HttpCookieStore.Empty());

        try {
            httpClient.start();
            httpClient.getContentDecoderFactories().clear();
        } catch (Exception e) {
            LOG.error("Failed to start server: " + e.getLocalizedMessage());
            LOG.error("Cause: " + e.getCause());
        }

        return httpClient;
    }

    @Override
    protected void sendProxyRequest(HttpServletRequest clientRequest, HttpServletResponse proxyResponse, Request proxyRequest) {
        try {
            URI rewrittenURI = rewriteForSSL(clientRequest);

            proxyRequest.scheme(rewrittenURI.getScheme());
            proxyRequest.path(rewrittenURI.toString());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        super.sendProxyRequest(clientRequest, proxyResponse, proxyRequest);
    }


    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        super.service(request, response);
    }

    private URI rewriteForSSL(HttpServletRequest request) throws URISyntaxException {
        String host = request.getServerName().toLowerCase();
        int port = request.getServerPort();
        String file = request.getRequestURI();
        String queryString = request.getQueryString();

        LOG.debug("Host: " + host);
        LOG.debug("Port: " + port);
        LOG.debug("Request URL: " + file);

        return new URI("https", null, host, port, file, queryString, null);
    }
}
