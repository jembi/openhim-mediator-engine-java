/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Encapsulates the mediator configuration.
 * <br/><br/>
 * At a minimum the name, server host & port and the routing table needs to be set.
 */
public class MediatorConfig {
    public static class KeyStore {
        private final String filename;
        private final InputStream inputStream;
        private final String password;

        public KeyStore(String filename, String password) {
            this.filename = filename;
            this.inputStream = null;
            this.password = password;
        }

        public KeyStore(InputStream inputStream, String password) {
            this.filename = null;
            this.inputStream = inputStream;
            this.password = password;
        }

        public KeyStore(String filename) {
            this(filename, null);
        }

        public KeyStore(InputStream inputStream) {
            this(inputStream, null);
        }

        public String getFilename() {
            return filename;
        }

        public InputStream getInputStream() {
            return inputStream;
        }

        public String getPassword() {
            return password;
        }
    }

    public static class SSLContext {
        private final KeyStore keyStore;
        private final KeyStore[] trustStores;
        private final boolean trustAll;

        public SSLContext(KeyStore keyStore, KeyStore[] trustStores, boolean trustAll) {
            this.keyStore = keyStore;
            this.trustStores = trustStores;
            this.trustAll = trustAll;
        }

        public SSLContext(KeyStore keyStore, KeyStore[] trustStores) {
            this(keyStore, trustStores, false);
        }

        public SSLContext(KeyStore keyStore, KeyStore trustStore) {
            this(keyStore, new KeyStore[]{trustStore});
        }

        public SSLContext(KeyStore[] trustStores) {
            this(null, trustStores);
        }

        public SSLContext(KeyStore trustStore) {
            this(null, trustStore);
        }

        public SSLContext(KeyStore keyStore, boolean trustAll) {
            this(keyStore, new KeyStore[0], trustAll);
        }

        public SSLContext(boolean trustAll) {
            this(null, trustAll);
        }

        public KeyStore getKeyStore() {
            return keyStore;
        }

        public KeyStore[] getTrustStores() {
            return trustStores;
        }

        public boolean getTrustAll() {
            return trustAll;
        }
    }

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

    private SSLContext sslContext;

    private boolean heartbeatsEnabled = false;
    private int heartbeatPeriodSeconds = 10;

    private Properties properties;
    private Map<String, Object> dynamicConfig = new HashMap<>();


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
     * Get a user path for an actor below the root context.
     * Useful for looking up startup actors or connectors, e.g.
     * <code>ActorSelection httpConnector = getContext().actorSelection(config.userPathFor("http-connector"));</code>
     */
    public String userPathFor(String actor) {
        return "/user/" + name + "/" + actor;
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

    /**
     * @see #setCoreHost(String)
     */
    public String getCoreHost() {
        return coreHost;
    }

    /**
     * The OpenHIM Core host.
     */
    public void setCoreHost(String coreHost) {
        this.coreHost = coreHost;
    }

    /**
     * @see #setCoreAPIPort(Integer)
     */
    public Integer getCoreAPIPort() {
        return coreAPIPort;
    }

    /**
     * The OpenHIM Core API port. Defaults to 8080 if not set.
     */
    public void setCoreAPIPort(Integer coreAPIPort) {
        this.coreAPIPort = coreAPIPort;
    }

    /**
     * @see #setCoreAPIScheme(String)
     */
    public String getCoreAPIScheme() {
        return coreAPIScheme;
    }

    /**
     * The OpenHIM Core API scheme. Defaults to 'https' if not set.
     */
    public void setCoreAPIScheme(String coreAPIScheme) {
        this.coreAPIScheme = coreAPIScheme;
    }

    /**
     * @see #setCoreAPIUsername(String)
     */
    public String getCoreAPIUsername() {
        return coreAPIUsername;
    }

    /**
     * The OpenHIM Core API user.
     *
     * @see #setCoreAPIPassword(String)
     */
    public void setCoreAPIUsername(String coreAPIUsername) {
        this.coreAPIUsername = coreAPIUsername;
    }

    /**
     * @see #getCoreAPIPassword()
     */
    public String getCoreAPIPassword() {
        return coreAPIPassword;
    }

    /**
     * Password for the OpenHIM Core API user.
     *
     * @see #setCoreAPIUsername(String)
     */
    public void setCoreAPIPassword(String coreAPIPassword) {
        this.coreAPIPassword = coreAPIPassword;
    }

    /**
     * @see org.openhim.mediator.engine.RoutingTable
     */
    public RoutingTable getRoutingTable() {
        return routingTable;
    }

    /**
     * @see org.openhim.mediator.engine.RoutingTable
     */
    public void setRoutingTable(RoutingTable routingTable) {
        this.routingTable = routingTable;
    }

    /**
     * @see org.openhim.mediator.engine.StartupActorsConfig
     */
    public StartupActorsConfig getStartupActors() {
        return startupActors;
    }

    /**
     * @see org.openhim.mediator.engine.StartupActorsConfig
     */
    public void setStartupActors(StartupActorsConfig startupActors) {
        this.startupActors = startupActors;
    }

    /**
     * @see org.openhim.mediator.engine.RegistrationConfig
     */
    public RegistrationConfig getRegistrationConfig() {
        return registrationConfig;
    }

    /**
     * The mediator registration config. If it contains default config and definitions,
     * then the dynamic config will be initialized with those values.
     *
     * @see org.openhim.mediator.engine.RegistrationConfig
     * @see #getDynamicConfig()
     */
    public void setRegistrationConfig(RegistrationConfig registrationConfig) {
        this.registrationConfig = registrationConfig;

        if (registrationConfig.getDefaultConfig()!=null) {
            for (String key : registrationConfig.getDefaultConfig().keySet()) {
                dynamicConfig.put(key, registrationConfig.getDefaultConfig().get(key));
            }
        }
    }

    /**
     * @see #setHeartbeatsEnabled(boolean)
     */
    public boolean getHeartsbeatEnabled() {
        return heartbeatsEnabled;
    }

    /**
     * If enabled, the mediator will periodically send a heartbeat message to core.
     * If core responds with any configuration updates, then the existing properties will be updated.
     *
     * By default the option is disabled.
     *
     * @see #setHeartbeatPeriodSeconds(int)
     * @see #setProperties(String)
     */
    public void setHeartbeatsEnabled(boolean heartbeatsEnabled) {
        this.heartbeatsEnabled = heartbeatsEnabled;
    }

    /**
     * @see #setHeartbeatsEnabled(boolean)
     */
    public int getHeartbeatPeriodSeconds() {
        return heartbeatPeriodSeconds;
    }

    /**
     * @see #setHeartbeatsEnabled(boolean)
     */
    public void setHeartbeatPeriodSeconds(int heartbeatPeriodSeconds) {
        this.heartbeatPeriodSeconds = heartbeatPeriodSeconds;
    }

    /**
     * @see #setProperties(java.util.Properties)
     */
    public Properties getProperties() {
        return properties;
    }

    /**
     * @see #setProperties(java.util.Properties)
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    /**
     * Optional mediator specific field for loading custom configuration from a properties file.
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

    /**
     * Settings for the mediator. On first load, it will be initialized to an empty map.
     *
     * If mediator registration config is supplied, then it will be initialized with any default config values.
     *
     * If heartbeats are enabled, then any config changes that core provides will be updated in the config map.
     *
     * @see #setRegistrationConfig(RegistrationConfig)
     */
    public Map<String, Object> getDynamicConfig() {
        return dynamicConfig;
    }

    /**
     * @see #setSSLContext(SSLContext)
     */
    public SSLContext getSSLContext() {
        return sslContext;
    }

    /**
     * Sets the SSL Context information for the mediator. If set, the {@link MediatorServer} will trigger a message
     * to the {@link org.openhim.mediator.engine.connectors.HTTPConnector} on startup in order to setup the context.
     *
     * @see #getSSLContext()
     */
    public void setSSLContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }
}
