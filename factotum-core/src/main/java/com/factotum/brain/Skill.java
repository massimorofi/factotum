package com.factotum.brain;

import java.util.Map;

/**
 * Represents a parsed Agent Skill following the agentskills.io specification.
 * Each skill carries YAML frontmatter metadata and a Markdown body with instructions for LLM agents.
 */
public record Skill(
    /** Unique name of this skill (derived from its directory). */
    String name,
    /** Short human-readable description. */
    String description,
    /** Full instruction text that gets injected into the Brain's system prompt. */
    String instructions,
    /** Optional license identifier. */
    String license,
    /** Compatibility metadata (e.g., supported models). */
    String compatibility,
    /** Arbitrary key-value pairs from the frontmatter metadata block. */
    Map<String, String> metadata
) {}
