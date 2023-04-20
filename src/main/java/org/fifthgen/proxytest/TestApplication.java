package org.fifthgen.proxytest;

public class TestApplication {

    private static final int HTTP_PORT = 8080;
    private static final int HTTPS_PORT = 8443;

    public static void main(String[] args) {
        var server = new ProxyServerWithTLS(HTTP_PORT, HTTPS_PORT);
        // server.setKeyStore("/etc/pki/ca-trust/extracted/java/cacerts");
        server.start();
    }
}
