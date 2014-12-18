package org.openhim.mediator.engine;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Encapsulates the mediator configuration.
 * At a minimum the name, server host & port and the routing table needs to be set.
 */
public class MediatorConfig {
    private String name;
    private String serverHost;
    private Integer serverPort;
    private Integer rootTimeout;
    private String coreHost;
    private Integer coreAPIPort = 8080;
    private String coreAPIScheme = "https";
    private String coreAPIUsername;
    private String coreAPIPassword;
    private RoutingTable routingTable;
    private StartupActorsConfig startupActors;
    private RegistrationConfig registrationConfig;
    private Properties properties;


    public MediatorConfig() {
    }

    public MediatorConfig(String name, String serverHost, Integer serverPort) {
        this.name = name;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }

    public MediatorConfig(String name, String serverHost, Integer serverPort, RoutingTable routingTable) {
        this(name, serverHost, serverPort);
        this.routingTable = routingTable;
    }


    /**
     * @see #setName(String)
     */
    public String getName() {
        return name;
    }

    /**
     * A name for the mediator. The root actor path will be set using this name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @see #setServerHost(String)
     */
    public String getServerHost() {
        return serverHost;
    }

    /**
     * The hostname that the mediator server should listen on.
     */
    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    /**
     * @see #setServerPort(Integer)
     */
    public Integer getServerPort() {
        return serverPort;
    }

    /**
     * The port that the mediator server should listen on.
     */
    public void setServerPort(Integer serverPort) {
        this.serverPort = serverPort;
    }

    /**
     * @see #setRootTimeout(Integer)
     */
    public Integer getRootTimeout() {
        return rootTimeout;
    }

    /**
     * How long the root actor should wait before timing out a request (in milliseconds).
     */
    public void setRootTimeout(Integer rootTimeout) {
        this.rootTimeout = rootTimeout;
    }

    public String getCoreHost() {
        return coreHost;
    }

    public void setCoreHost(String coreHost) {
        this.coreHost = coreHost;
    }

    public Integer getCoreAPIPort() {
        return coreAPIPort;
    }

    public void setCoreAPIPort(Integer coreAPIPort) {
        this.coreAPIPort = coreAPIPort;
    }

    public String getCoreAPIScheme() {
        return coreAPIScheme;
    }

    public void setCoreAPIScheme(String coreAPIScheme) {
        this.coreAPIScheme = coreAPIScheme;
    }

    public String getCoreAPIUsername() {
        return coreAPIUsername;
    }

    public void setCoreAPIUsername(String coreAPIUsername) {
        this.coreAPIUsername = coreAPIUsername;
    }

    public String getCoreAPIPassword() {
        return coreAPIPassword;
    }

    public void setCoreAPIPassword(String coreAPIPassword) {
        this.coreAPIPassword = coreAPIPassword;
    }

    public RoutingTable getRoutingTable() {
        return routingTable;
    }

    public void setRoutingTable(RoutingTable routingTable) {
        this.routingTable = routingTable;
    }

    public StartupActorsConfig getStartupActors() {
        return startupActors;
    }

    public void setStartupActors(StartupActorsConfig startupActors) {
        this.startupActors = startupActors;
    }

    public RegistrationConfig getRegistrationConfig() {
        return registrationConfig;
    }

    public void setRegistrationConfig(RegistrationConfig registrationConfig) {
        this.registrationConfig = registrationConfig;
    }

    /**
     * @see #setProperties(java.util.Properties)
     */
    public Properties getProperties() {
        return properties;
    }

    /**
     * Optional mediator specific field for loading custom config from a properties file
     */
    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    /**
     * @see #setProperties(java.util.Properties)
     */
    public void setProperties(String resourceName) throws IOException {
        InputStream propsStream = getClass().getClassLoader().getResourceAsStream(resourceName);
        try {
            Properties props = new Properties();
            props.load(propsStream);
            setProperties(props);
        } finally {
            IOUtils.closeQuietly(propsStream);
        }
    }
}
