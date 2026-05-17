package com.factotum.brain;

import java.util.Map;
import java.util.UUID;

/**
 * A single task unit produced by the Brain agent for execution.
 */
public record TaskSpecification(
    /** Unique identifier for this task. */
    UUID taskId,
    /** Natural-language instruction describing what the executor should do. */
    String instruction,
    /** Key-value parameters passed to the executor (e.g., URLs, file paths). */
    Map<String, Object> parameters
) {}
