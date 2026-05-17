---
name: error-handling-matrix
description: 4-tier error handling strategy for task execution decisions. Use when the Brain needs to decide how to respond to a failed or failing task — retry, self-correct, request clarification, or fail fast. Ensures consistent error response patterns across all task types.
license: MIT
metadata:
  version: "1.0"
---

# Error Handling Matrix Skill

## Purpose

When the Brain evaluates task outcomes (either from a failed execution or a task that reports an error), it should classify the failure into one of four tiers and respond accordingly. This prevents unnecessary retries on fatal errors and avoids failing fast on recoverable ones.

## The 4 Tiers

### Tier 1 — Transient Errors
**Examples:** Network timeout, rate limit exceeded, service temporarily unavailable
**Response:** Retry with backoff (up to 2 attempts). Include `retry: true` in task parameters for the executor.

### Tier 2 — LLM-Recoverable Errors
**Examples:** Tool call failed with wrong arguments, missing optional parameter, incorrect URL format
**Response:** Re-invoke the same task with corrected instructions. The Brain should analyze the error and fix the parameters before retrying.

### Tier 3 — User-Fixable Errors (Clarification Needed)
**Examples:** Ambiguous goal, missing required information, conflicting requirements
**Response:** Return a `discard` decision with reasoning that explains what information is needed. Do NOT retry — the sender must provide clarification.

### Tier 4 — Fatal / Unexpected Errors
**Examples:** Out of memory, invalid configuration, unhandled exception in executor
**Response:** Fail fast. Log the error and return `false` from processing. No retry — this indicates a systemic issue.

## Brain Decision Rule

When evaluating task results:
1. If result contains an error message → classify into Tier 1-4
2. Apply the corresponding response strategy
3. For Tiers 1-2, re-spawn the task with adjusted parameters
4. For Tier 3, discard and communicate back to sender
5. For Tier 4, log and stop processing this message
