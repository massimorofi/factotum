---
name: output-contract
description: Defines expected output format for tasks before execution begins. Use when spawning tasks to ensure consistent, parseable results from executors. Helps the Brain validate task outcomes and downstream consumers process results reliably.
license: MIT
metadata:
  version: "1.0"
---

# Output Contract Skill

## Purpose

Before a task executes, its expected output format should be defined. This is an "output contract" — a precise description of what the executor must return. Without it, LLM executors produce unstructured text that is hard to parse or validate downstream.

## Pattern

When spawning tasks via `spawn-task`, include an `outputContract` field in the task parameters:

```json
{
  "taskId": "...",
  "instruction": "...",
  "parameters": {
    "target": "src/main/java",
    "outputContract": "Return a JSON array of objects with keys: 'file', 'severity' (critical|warning|info), 'message', and 'suggestion'. One entry per finding."
  }
}
```

## Guidelines

1. **Be specific** — Name the exact format (JSON, markdown table, plain text) and required fields
2. **Define constraints** — Max items, allowed values for enums, required vs optional fields
3. **Include an example** — Show one valid output instance so the executor can match structure
4. **Keep it concise** — The contract should be 1-3 sentences max

## Brain Decision Rule

For every `spawn-task` action, always include `outputContract` in task parameters unless the task is a simple read operation (e.g., HTTP fetch) where the raw response is the natural output.
