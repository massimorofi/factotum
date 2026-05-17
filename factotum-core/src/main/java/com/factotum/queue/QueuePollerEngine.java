package com.factotum.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.factotum.brain.BrainService;
import com.factotum.queue.model.FactotumMessage;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.jboss.logging.Logger;

/**
 * Background poller that continuously reads messages from the PGMQ queue and processes them through the Brain.
 * Runs as a virtual thread on startup; can be disabled via configuration for testing.
 * Gracefully stops when the application shuts down to avoid NPE spam from destroyed CDI beans.
 * Uses exponential backoff (1s–30s) on connection failures to avoid flooding stderr.
 */
@ApplicationScoped
public class QueuePollerEngine {

    private static final Logger log = Logger.getLogger(QueuePollerEngine.class);

    @Inject DataSource dataSource;
    @Inject ObjectMapper objectMapper;
    @Inject BrainService brainService;

    @ConfigProperty(name = "factotum.queue.poller-enabled", defaultValue = "true") boolean pollerEnabled;

    private volatile boolean running = false;
    private Thread pollingThread;

    /** Called on application startup — launches the polling loop if enabled. */
    public void initQueuePollers(@Observes StartupEvent ev) {
        if (!pollerEnabled) {
            log.info("Queue poller is disabled");
            return;
        }
        running = true;
        pollingThread = Thread.startVirtualThread(() -> startPolling("factotum_inbound"));
    }

    /** Called on application shutdown — signals the poller to stop and interrupts it. */
    public void onStop(@Observes ShutdownEvent ev) {
        log.info("Queue poller shutting down");
        running = false;
        if (pollingThread != null && pollingThread.isAlive()) {
            pollingThread.interrupt();
        }
    }

    /** Continuously polls the PGMQ queue, processes each message via BrainService, and deletes it on success. */
    private void startPolling(String queueName) {
        long backoffMs = 1000;
        while (running) {
            try (Connection conn = dataSource.getConnection()) {
                String sql = "SELECT msg_id, message::text FROM pgmq.read(?, 60, 10)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, queueName);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next() && running) {
                            long msgId = rs.getLong("msg_id");
                            String messageJson = rs.getString("message");

                            FactotumMessage msg = objectMapper.readValue(messageJson, FactotumMessage.class);

                            boolean processSuccess = brainService.processEvent(msg, msgId);

                            if (processSuccess) {
                                try (PreparedStatement del = conn.prepareStatement("SELECT pgmq.delete(?, ?);")) {
                                    del.setString(1, queueName);
                                    del.setLong(2, msgId);
                                    del.execute();
                                }
                            }
                        }
                    }
                }
                // Reset backoff on success, then poll again after the normal interval
                backoffMs = 1000;
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (!running) return;

                // Exponential backoff on transient failures, capped at 30s
                log.warnf(e, "Error polling queue %s — retrying in %,dms", queueName, backoffMs);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                // Double the backoff, cap at 30 seconds
                backoffMs = Math.min(backoffMs * 2, 30_000);
            }
        }
        log.info("Queue poller loop exited");
    }
}
