/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.messaging.remote.internal;

import org.gradle.api.Action;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.ConnectEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

public class HandshakeIncomingConnector implements IncomingConnector<Object> {
    private final IncomingConnector<Object> connector;
    private final Executor executor;
    private final Object lock = new Object();
    private Address localAddress;
    private long nextId;
    private final Map<Address, Action<ConnectEvent<Connection<Object>>>> pendingActions = new HashMap<Address, Action<ConnectEvent<Connection<Object>>>>();

    public HandshakeIncomingConnector(IncomingConnector<Object> connector, Executor executor) {
        this.connector = connector;
        this.executor = executor;
    }

    public Address accept(Action<ConnectEvent<Connection<Object>>> action) {
        synchronized (lock) {
            if (localAddress == null) {
                localAddress = connector.accept(handShakeAction());
            }

            Address localAddress = new CompositeAddress(this.localAddress, nextId++);
            pendingActions.put(localAddress, action);
            return localAddress;
        }
    }

    private Action<ConnectEvent<Connection<Object>>> handShakeAction() {
        return new Action<ConnectEvent<Connection<Object>>>() {
            public void execute(final ConnectEvent<Connection<Object>> connectEvent) {
                executor.execute(new Runnable() {
                    public void run() {
                        handshake(connectEvent);
                    }
                });
            }
        };
    }

    private void handshake(ConnectEvent<Connection<Object>> connectEvent) {
        Connection<Object> connection = connectEvent.getConnection();
        ConnectRequest request = (ConnectRequest) connection.receive();
        Address localAddress = request.getDestinationAddress();
        Action<ConnectEvent<Connection<Object>>> channelConnection;
        synchronized (lock) {
            channelConnection = pendingActions.remove(localAddress);
        }
        if (channelConnection == null) {
            throw new IllegalStateException(String.format(
                    "Request to connect received for unknown address '%s'.", localAddress));
        }
        channelConnection.execute(new ConnectEvent<Connection<Object>>(connection, localAddress, connectEvent.getRemoteAddress()));
    }
}
