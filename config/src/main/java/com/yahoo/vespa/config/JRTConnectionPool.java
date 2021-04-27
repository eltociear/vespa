// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A pool of JRT connections to a config source (either a config server or a config proxy).
 * The current connection is chosen randomly when calling {#link {@link #switchConnection()}}
 * (it will continue to use the same connection if there is only one source).
 * The current connection is available with {@link #getCurrent()}.
 * When calling {@link #setError(Connection, int)}, {@link #switchConnection()} will always be called.
 *
 * @author Gunnar Gauslaa Bergem
 * @author hmusum
 */
public class JRTConnectionPool implements ConnectionPool {

    private static final Logger log = Logger.getLogger(JRTConnectionPool.class.getName());

    private final Supervisor supervisor;
    private final Map<String, JRTConnection> connections = new LinkedHashMap<>();

    // The config sources used by this connection pool.
    private ConfigSourceSet sourceSet = null;

    // The current connection used by this connection pool.
    private volatile JRTConnection currentConnection;

    public JRTConnectionPool(ConfigSourceSet sourceSet) {
        supervisor = new Supervisor(new Transport("config-jrtpool-" + sourceSet.hashCode())).useSmallBuffers();
        addSources(sourceSet);
    }

    public JRTConnectionPool(List<String> addresses) {
        this(new ConfigSourceSet(addresses));
    }

    public void addSources(ConfigSourceSet sourceSet) {
        this.sourceSet = sourceSet;
        synchronized (connections) {
            for (String address : sourceSet.getSources()) {
                connections.put(address, new JRTConnection(address, supervisor));
            }
        }
        currentConnection = initialize();
    }

    /**
     * Returns the current JRTConnection instance
     *
     * @return a JRTConnection
     */
    public synchronized JRTConnection getCurrent() {
        return currentConnection;
    }

    @Override
    public synchronized JRTConnection switchConnection() {
        List<JRTConnection> sources = getSources();
        if (sources.size() <= 1) return currentConnection;

        List<JRTConnection> sourceCandidates = sources.stream()
                                                    .filter(JRTConnection::isHealthy)
                                                    .collect(Collectors.toList());
        JRTConnection newConnection;
        if (sourceCandidates.size() == 0) {
            sourceCandidates = getSources();
            sourceCandidates.remove(currentConnection);
        }
        newConnection = pickNewConnectionRandomly(sourceCandidates);
        log.log(Level.INFO, () -> "Switching from " + currentConnection + " to " + newConnection);
        return currentConnection = newConnection;
    }

    public synchronized JRTConnection initialize() {
        return pickNewConnectionRandomly(getSources());
    }

    private JRTConnection pickNewConnectionRandomly(List<JRTConnection> sources) {
        return sources.get(ThreadLocalRandom.current().nextInt(0, sources.size()));
    }

    List<JRTConnection> getSources() {
        List<JRTConnection> ret;
        synchronized (connections) {
            ret = new ArrayList<>(connections.values());
        }
        return ret;
    }

    ConfigSourceSet getSourceSet() {
        return sourceSet;
    }

    @Override
    public void setError(Connection connection, int errorCode) {
        connection.setError(errorCode);
        switchConnection();
    }

    public JRTConnectionPool updateSources(List<String> addresses) {
        ConfigSourceSet newSources = new ConfigSourceSet(addresses);
        return updateSources(newSources);
    }

    public JRTConnectionPool updateSources(ConfigSourceSet sourceSet) {
        synchronized (connections) {
            for (JRTConnection conn : connections.values()) {
                conn.getTarget().close();
            }
            connections.clear();
            addSources(sourceSet);
        }
        return this;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        synchronized (connections) {
            for (JRTConnection conn : connections.values()) {
                sb.append(conn.toString());
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public void close() {
        supervisor.transport().shutdown().join();
    }

    @Override
    public int getSize() {
        synchronized (connections) {
            return connections.size();
        }
    }

    @Override
    public Supervisor getSupervisor() {
        return supervisor;
    }

}
