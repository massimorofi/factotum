package com.factotum.queue.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Metadata envelope for every message in the Factotum pipeline.
 * Contains routing info (sender, destination queue), identifiers, and optional context.
 */
public record MessageHeader(
    /** Unique message identifier (auto-generated if null). */
    @JsonProperty(required = true) UUID messageId,
    /** Optional correlation ID linking related messages. */
    String correlationId,
    /** When the message was created. */
    @JsonProperty(required = true) Instant timestamp,
    /** Originator of this message. */
    @JsonProperty(required = true) String sender,
    /** Target queue for routing (e.g., "factotum_inbound"). */
    @JsonProperty(required = true) String destination,
    /** Optional reply-to queue address. */
    String replyTo,
    /** MIME type of the message body. */
    @JsonProperty(required = true) String contentType,
    /** Schema version for forward compatibility. */
    @JsonProperty(required = true) String schemaVersion,
    /** Message priority (1-10, default 5). */
    @JsonProperty(defaultValue = "5") int priority,
    /** Optional time-to-live in seconds before the message expires. */
    Integer ttlSeconds,
    /** Arbitrary key-value context attached by the sender. */
    Map<String, Object> context
) {}
