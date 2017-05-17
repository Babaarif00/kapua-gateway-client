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
package org.eclipse.kapua.gateway.client;

import static org.eclipse.kapua.gateway.client.Credentials.userAndPassword;
import static org.eclipse.kapua.gateway.client.Errors.handle;
import static org.eclipse.kapua.gateway.client.Errors.ignore;
import static org.eclipse.kapua.gateway.client.Transport.waitForConnection;

import org.eclipse.kapua.gateway.client.profile.KuraMqttProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KuraExample {

    private static final Logger logger = LoggerFactory.getLogger(KuraExample.class);

    public static void main(final String[] args) throws Exception {

        try (final Client client = new KuraMqttProfile()
                .accountName("kapua-sys")
                .clientId("foo-bar-1")
                .brokerUrl("tcp://localhost:1883")
                .credentials(userAndPassword("kapua-broker", "kapua-password"))
                .build()) {

            try (final Application application = client.buildApplication("app1").build()) {

                // wait for connection

                waitForConnection(application.transport());

                // subscribe to a topic

                application.data(Topic.of("my", "topic")).subscribe(message -> {
                    System.out.format("Received: %s%n", message);
                });

                // example payload

                final Payload.Builder payload = new Payload.Builder();
                payload.put("foo", "bar");
                payload.put("a", 1);

                try {
                    // send, handling error ourself
                    application.data(Topic.of("my", "topic")).send(payload);
                } catch (final Exception e) {
                    logger.info("Failed to publish", e);
                }

                // send, with attached error handler

                application.data(Topic.of("my", "topic"))
                        .errors(handle((e, message) -> System.err.println("Failed to publish: " + e.getMessage())))
                        .send(payload);

                // ignoring error

                application.data(Topic.of("my", "topic")).errors(ignore()).send(payload);

                // cache sender instance

                final Sender<RuntimeException> sender = application.data(Topic.of("my", "topic")).errors(ignore());

                int i = 0;
                while (true) {
                    // send
                    sender.send(Payload.of("counter", i++));
                    Thread.sleep(1000);
                }

                // sleep to not run into Paho thread starvation
                // Thread.sleep(100_000);
            }

        }
    }
}
