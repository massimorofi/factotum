package com.factotum.brain;

import java.util.List;

/**
 * The structured decision returned by the Brain agent after analyzing an incoming message.
 */
public record BrainDecision(
    /** Action to take: "discard", "spawn-task", "update-plan", or "create-plan". */
    String action,
    /** Human-readable reasoning behind this decision. */
    String reasoning,
    /** List of tasks to execute when action is "spawn-task". */
    List<TaskSpecification> tasks
) {}
