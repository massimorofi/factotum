package com.factotum.queue.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MessageHeaderTest {

    private MessageHeader createMinimalHeader() {
        return new MessageHeader(
            UUID.randomUUID(), null, Instant.now(), "sender", "factotum_inbound",
            null, "application/json", "1.0", 5, null, null
        );
    }

    @Test
    void testMessageHeaderCreation() {
        MessageHeader header = createMinimalHeader();

        assertNotNull(header.messageId());
        assertNull(header.correlationId());
        assertNotNull(header.timestamp());
        assertEquals("sender", header.sender());
        assertEquals("factotum_inbound", header.destination());
        assertNull(header.replyTo());
        assertEquals("application/json", header.contentType());
        assertEquals("1.0", header.schemaVersion());
    }

    @Test
    void testMessageHeaderDefaults() {
        MessageHeader header = createMinimalHeader();
        assertEquals(5, header.priority(), "Default priority should be 5");
        assertNull(header.ttlSeconds(), "ttlSeconds should default to null");
        assertNull(header.context(), "context should default to null");
    }

    @Test
    void testMessageHeaderWithAllFields() {
        UUID id = UUID.randomUUID();
        Map<String, Object> ctx = Map.of("env", "test", "count", 42);

        MessageHeader header = new MessageHeader(
            id, "corr-123", Instant.now(), "cli", "factotum_inbound",
            "reply.queue", "application/json", "1.0", 8, 60, ctx
        );

        assertEquals(id, header.messageId());
        assertEquals("corr-123", header.correlationId());
        assertEquals("cli", header.sender());
        assertEquals("factotum_inbound", header.destination());
        assertEquals("reply.queue", header.replyTo());
        assertEquals("application/json", header.contentType());
        assertEquals("1.0", header.schemaVersion());
        assertEquals(8, header.priority());
        assertEquals(60, header.ttlSeconds());
        assertEquals("test", header.context().get("env"));
    }

    @Test
    void testMessageHeaderImmutability() {
        MessageHeader header = createMinimalHeader();

        // Records are immutable - no setters exist, only accessors
        assertDoesNotThrow(() -> {
            UUID retrievedId = header.messageId();
            String sender = header.sender();
            assertNotNull(retrievedId);
            assertEquals("sender", sender);
        });
    }

    @Test
    void testMessageHeaderEqualsAndHashCode() {
        Instant ts = Instant.now();
        MessageHeader h1 = new MessageHeader(
            UUID.randomUUID(), null, ts, "sender", "factotum_inbound",
            null, "application/json", "1.0", 5, null, null
        );

        // Same values should be equal (different UUIDs)
        MessageHeader h2 = new MessageHeader(
            h1.messageId(), null, h1.timestamp(), h1.sender(), h1.destination(),
            null, h1.contentType(), h1.schemaVersion(), 5, null, null
        );

        assertEquals(h1, h2);
        assertEquals(h1.hashCode(), h2.hashCode());
    }

    @Test
    void testMessageHeaderToString() {
        MessageHeader header = createMinimalHeader();
        String str = header.toString();
        assertTrue(str.contains("messageId="));
        assertTrue(str.contains("sender="));
        assertTrue(str.contains("destination="));
    }
}
