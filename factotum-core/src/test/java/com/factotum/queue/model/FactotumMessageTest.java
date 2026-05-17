package com.factotum.queue.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FactotumMessageTest {

    @Test
    void testFactotumMessageCreation() {
        UUID id = UUID.randomUUID();
        MessageHeader header = new MessageHeader(
            id, null, Instant.now(), "sender", "factotum_inbound",
            null, "application/json", "1.0", 5, null, null
        );
        Map<String, Object> body = Map.of("action", "analyze", "target", "src/main/java");

        FactotumMessage msg = new FactotumMessage(header, body);

        assertEquals(id, msg.header().messageId());
        assertEquals("sender", msg.header().sender());
        assertEquals("factotum_inbound", msg.header().destination());
        assertEquals("analyze", msg.body().get("action"));
        assertEquals("src/main/java", msg.body().get("target"));
    }

    @Test
    void testFactotumMessageWithAllHeaderFields() {
        UUID id = UUID.randomUUID();
        MessageHeader header = new MessageHeader(
            id, "corr-123", Instant.now(), "cli", "factotum_inbound",
            "reply.queue", "application/json", "1.0", 8, 60,
            Map.of("env", "test")
        );

        FactotumMessage msg = new FactotumMessage(header, Map.of());

        assertEquals(id, msg.header().messageId());
        assertEquals("corr-123", msg.header().correlationId());
        assertEquals("reply.queue", msg.header().replyTo());
        assertEquals(8, msg.header().priority());
        assertEquals(60, msg.header().ttlSeconds());
        assertEquals("test", msg.header().context().get("env"));
    }

    @Test
    void testFactotumMessageDefaultPriority() {
        MessageHeader header = new MessageHeader(
            UUID.randomUUID(), null, Instant.now(), "sender", "factotum_inbound",
            null, "application/json", "1.0", 5, null, null
        );

        assertEquals(5, header.priority());
    }
}
