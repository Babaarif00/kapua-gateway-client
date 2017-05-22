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
package org.eclipse.kapua.gateway.client.mqtt;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.eclipse.kapua.gateway.client.ErrorHandler;
import org.eclipse.kapua.gateway.client.MessageHandler;
import org.eclipse.kapua.gateway.client.Payload;
import org.eclipse.kapua.gateway.client.Topic;
import org.eclipse.kapua.gateway.client.spi.AbstractApplication;
import org.eclipse.kapua.gateway.client.spi.AbstractData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MqttApplication extends AbstractApplication {

    private static final Logger logger = LoggerFactory.getLogger(MqttApplication.class);

    private MqttClient client;

    public MqttApplication(final MqttClient client, final String applicationId, final Executor executor) {
        super(client, applicationId, executor);
        this.client = client;
    }

    @Override
    public AbstractData data(final Topic topic) {
        return new AbstractData(this, topic);
    }

    protected void publish(Topic topic, Payload payload) throws Exception {
        logger.debug("Publishing values - {} -> {}", topic, payload.getValues());

        final ByteBuffer buffer = client.getCodec().encode(payload, null);
        buffer.flip();

        client.publish(applicationId, topic, buffer);
    }

    @Override
    protected CompletionStage<?> internalSubscribe(Topic topic, MessageHandler handler, ErrorHandler<? extends Throwable> errorHandler) throws Exception {
        return client.subscribe(applicationId, topic, (messageTopic, payload) -> {
            logger.debug("Received message for: {}", topic);
            try {
                MqttApplication.this.handleMessage(handler, payload);
            } catch (final Exception e) {
                try {
                    errorHandler.handleError(e, null);
                } catch (final Exception e1) {
                    throw e1;
                } catch (final Throwable e1) {
                    throw new Exception(e1);
                }
            }
        });
    }

    protected void handleMessage(final MessageHandler handler, final ByteBuffer buffer) throws Exception {
        final Payload payload = client.getCodec().decode(buffer);
        logger.debug("Received: {}", payload);
        handler.handleMessage(payload);
    }
}