---
name: parallel-execution
description: Guidelines for identifying and marking independent tasks for parallel execution. Use when the Brain spawns multiple tasks that can run concurrently without dependencies, reducing total execution time.
license: MIT
metadata:
  version: "1.0"
---

# Parallel Execution Skill

## Purpose

When spawning multiple tasks via `spawn-task`, the Brain should identify which tasks are independent (no data or state dependency on each other) and mark them for parallel execution. Dependent tasks must remain sequential.

## Decision Rule

For each task in a `spawn-task` decision:
1. **Independent** — Task reads from external sources or has no side effects → mark `"parallel": true`
2. **Dependent** — Task needs output from another task in the same batch → keep `"parallel": false` (sequential)

## Examples

### Parallelizable
- Analyzing different directories independently
- Fetching multiple unrelated API endpoints
- Running separate code quality checks on different modules

### NOT Parallelizable
- Step 2 depends on results from step 1
- A task that modifies shared state another task reads
- Sequential build steps (compile → test → package)

## Brain Output Format

When tasks are parallelizable, include the flag in each TaskSpecification:

```json
{
  "taskId": "...",
  "instruction": "...",
  "parameters": {
    "parallel": true,
    "target": "src/main/java"
  }
}
```

Tasks without `"parallel": true` are executed sequentially by default.
