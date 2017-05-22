/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc - initial API and implementation
 *******************************************************************************/
package org.eclipse.kapua.gateway.client.spi;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import org.eclipse.kapua.gateway.client.Application;
import org.eclipse.kapua.gateway.client.Client;
import org.eclipse.kapua.gateway.client.Module;
import org.eclipse.kapua.gateway.client.ModuleContext;
import org.eclipse.kapua.gateway.client.Topic;
import org.eclipse.kapua.gateway.client.Transport;
import org.eclipse.kapua.gateway.client.utils.TransportAsync;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractClient implements Client {

    private static final Logger logger = LoggerFactory.getLogger(AbstractClient.class);

    public static abstract class Builder<T extends Builder<T>> implements Client.Builder {

        protected abstract T builder();

        private final Set<Module> modules = new HashSet<>();

        public T module(final Module module) {
            Objects.requireNonNull(module);

            this.modules.add(module);
            return builder();
        }

        public Set<Module> modules() {
            return this.modules;
        }
    }

    protected final ScheduledExecutorService executor;
    private final Set<Module> modules;

    private final TransportAsync transport;

    private final Map<String, AbstractApplication> applications = new HashMap<>();

    public AbstractClient(final ScheduledExecutorService executor, final Set<Module> modules) {
        this.executor = executor;
        this.modules = new HashSet<>(modules);

        this.transport = new TransportAsync(executor);

        fireModuleEvent(module -> module.initialize(new ModuleContext() {

            @Override
            public Client getClient() {
                return AbstractClient.this;
            }
        }));
    }

    @Override
    public Transport transport() {
        return this.transport;
    }

    private void fireModuleEvent(final Consumer<Module> consumer) {
        for (final Module module : this.modules) {
            try {
                consumer.accept(module);
            } catch (final Exception e) {
                logger.info("Failed to process module event", e);
            }
        }
    }

    protected void notifyAddApplication(final String applicationId) {
        fireModuleEvent(module -> module.applicationAdded(applicationId));
    }

    protected void notifyRemoveApplication(final String applicationId) {
        fireModuleEvent(module -> module.applicationRemoved(applicationId));
    }

    protected void notifyConnected() {
        fireModuleEvent(Module::connected);
        this.transport.handleConnected();
    }

    protected void notifyDisconnected() {
        fireModuleEvent(Module::disconnected);
        this.transport.handleDisconnected();
    }

    protected void handleConnected() {
        logger.debug("Connected");

        notifyConnected();
        synchronized (this) {
            this.applications.values().stream().forEach(app -> app.handleConnected());
        }
    }

    protected void handleDisconnected() {
        logger.debug("Disconnected");

        notifyDisconnected();
        synchronized (this) {
            this.applications.values().stream().forEach(app -> app.handleDisconnected());
        }
    }

    @Override
    public Application.Builder buildApplication(final String applicationId) {
        return new Application.Builder() {

            @Override
            public Application build() {
                return internalBuildApplication(this, applicationId);
            }
        };
    }

    protected AbstractApplication internalBuildApplication(final Application.Builder builder, final String applicationId) {
        final AbstractApplication result = internalCreateApplication(builder, applicationId);

        synchronized (this) {
            this.applications.put(applicationId, result);
            notifyAddApplication(applicationId);
        }

        return result;
    }

    protected abstract AbstractApplication internalCreateApplication(final Application.Builder builder, final String applicationId);

    protected abstract void internalUnsubscribe(String applicationId, Collection<Topic> topics) throws Exception;

    protected synchronized void internalCloseApplication(final String applicationId, Set<Topic> topics, final AbstractApplication application) {
        if (this.applications.remove(applicationId, application)) {
            try {
                internalUnsubscribe(applicationId, topics);
            } catch (Exception e) {
                logger.warn("Failed to unsubscribe on application close", e);
            }
            handleApplicationClosed(applicationId, application);
        }
    }

    protected void handleApplicationClosed(final String applicationId, final AbstractApplication application) {
        notifyRemoveApplication(applicationId);
    }

}
