package com.factotum.queue.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Top-level message envelope for the Factotum AI pipeline.
 * Contains a required header (routing/metadata) and an arbitrary body.
 */
public record FactotumMessage(
    /** Message metadata: ID, sender, destination queue, content type, etc. */
    @JsonProperty(required = true) MessageHeader header,
    /** Arbitrary key-value payload processed by the Brain agent. */
    @JsonProperty(required = true) Map<String, Object> body
) {}
