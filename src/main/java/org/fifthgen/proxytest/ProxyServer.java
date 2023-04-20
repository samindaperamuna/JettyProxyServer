package org.fifthgen.proxytest;

import org.eclipse.jetty.proxy.ConnectHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.server.handler.HandlerList;

public class ProxyServer {

    public static void main(String[] args) throws Exception {
        // Create a new Jetty server instance
        Server server = new Server(8080);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        ProxyServletWithTLSParams customProxyServlet = new ProxyServletWithTLSParams();
        ServletHolder proxyServletHolder = new ServletHolder(customProxyServlet);
        context.addServlet(proxyServletHolder, "/*");

        // Add ConnectHandler to support HTTPS
        ConnectHandler proxy = new ConnectHandler();
        proxy.setServer(server);

        HandlerList handlers = new HandlerList();
        handlers.addHandler(proxy);
        handlers.addHandler(context);

        server.setHandler(handlers);
        server.start();

        // Wait for the Jetty server instance to finish execution
        server.join();
    }
}
