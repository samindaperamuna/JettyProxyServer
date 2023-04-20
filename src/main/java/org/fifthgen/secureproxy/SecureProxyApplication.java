package org.fifthgen.secureproxy;

public class SecureProxyApplication {

    private static final int HTTP_PORT = 8080;
    private static final int HTTPS_PORT = 8443;

    public static void main(String[] args) {
        var server = new ProxyServer(HTTP_PORT, HTTPS_PORT);
        server.start();
    }
}
