---
name: self-healing-loop
description: Self-healing execution loop for task processing. Use when spawning tasks that may fail — instructs the Brain to include error-recovery steps in task instructions so executors can fix their own mistakes without human intervention.
license: MIT
metadata:
  version: "1.0"
---

# Self-Healing Loop Skill

## Purpose

When the Brain spawns tasks, it should embed a self-healing loop pattern into each task instruction. This ensures that if an executor encounters an error during execution, it can recover autonomously rather than failing outright.

## Pattern

Every spawned task should follow this cycle:

1. **Plan** — Understand the goal and outline steps
2. **Execute** — Perform the primary action
3. **Validate** — Check results against expected outcome
4. **Fix** — If validation fails, analyze the error and retry with corrected approach
5. **Report** — Return structured final result

The loop repeats until either validation passes or a maximum of 2 retries is reached.

## Brain Instruction Template

When spawning tasks via `spawn-task`, include this in the task instruction:

```
Follow a self-healing loop: plan your approach, execute, validate results, and fix any issues up to 2 times before reporting failure. If you encounter an error, analyze what went wrong, adjust your approach, and retry with corrected parameters.
```

## When Not to Use

- Simple read-only operations (e.g., `http_get`) that have no side effects — these don't need healing
- Tasks where failure is the correct outcome (e.g., "check if this endpoint returns 404")
