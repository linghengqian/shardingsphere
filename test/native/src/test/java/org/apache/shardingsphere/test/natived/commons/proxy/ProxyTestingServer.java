/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.test.natived.commons.proxy;

import lombok.Getter;
import org.apache.curator.test.InstanceSpec;
import org.apache.shardingsphere.proxy.Bootstrap;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

/**
 * This class is designed to start ShardingSphere Proxy directly in the current process,
 * whether it is HotSpot VM or GraalVM Native Image,
 * so this class intentionally uses fewer than a few dozen JVM parameters.
 * It is necessary to avoid creating multiple ShardingSphere Proxy instances in parallel in Junit5 unit tests.
 * Currently, Junit5 unit tests are all executed serially.
 */
public final class ProxyTestingServer {
    
    @Getter
    private final int proxyPort;
    
    private Thread proxyBootstrapThread;
    
    /**
     * Call this method to start the Server side of ShardingSphere Proxy in a separate thread.
     *
     * @param configAbsolutePath The absolute path to the directory where {@code global.yaml} is located.
     */
    public ProxyTestingServer(final String configAbsolutePath) {
        proxyPort = InstanceSpec.getRandomPort();
        proxyBootstrapThread = new Thread(() -> {
            try {
                Thread.currentThread().setName("ShardingSphereProxy-Bootstrap-" + proxyPort);
                Bootstrap.main(new String[]{String.valueOf(proxyPort), configAbsolutePath, "0.0.0.0"});
            } catch (final IOException | SQLException ex) {
                // Log exceptions that occur during proxy startup within the thread
                System.err.println("Exception during ShardingSphere Proxy startup in ProxyTestingServer: " + ex.getMessage());
                ex.printStackTrace();
                // Depending on requirements, you might want to set a flag indicating startup failure
            } catch (final Throwable t) {
                // Catch any other unexpected Throwables
                System.err.println("Unexpected Throwable during ShardingSphere Proxy execution in ProxyTestingServer: " + t.getMessage());
                t.printStackTrace();
            }
        });
        // proxyBootstrapThread.setDaemon(true); // Consider if this should be a daemon thread
        proxyBootstrapThread.start();
        // It might be useful to add a short wait here or a mechanism to confirm the proxy has started listening,
        // e.g., by trying to connect or checking a status flag set by the thread.
        // For now, this starts the thread and proceeds.
    }
    
    /**
     * Attempt to close ShardingSphere Proxy by interrupting its thread.
     */
    public void close() {
        if (proxyBootstrapThread != null && proxyBootstrapThread.isAlive()) {
            System.out.println("Attempting to stop ProxyTestingServer thread for port: " + proxyPort);
            proxyBootstrapThread.interrupt(); // Signal Bootstrap.main to stop
            try {
                // Wait for the thread to die
                proxyBootstrapThread.join(TimeUnit.SECONDS.toMillis(30)); // Wait up to 30 seconds
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt(); // Restore interrupt status
                System.err.println("Interrupted while waiting for ProxyTestingServer thread to close on port: " + proxyPort);
            }
            
            if (proxyBootstrapThread.isAlive()) {
                System.err.println("ProxyTestingServer thread on port " + proxyPort + " did not terminate after interrupt and join. Potential resource leak.");
                // The thread is still running. This indicates Bootstrap.main may not handle interruption gracefully for shutdown.
            } else {
                System.out.println("ProxyTestingServer thread for port " + proxyPort + " has terminated.");
            }
        }
        proxyBootstrapThread = null;
    }
}
