---
name: plan-manager
description: Creates, updates, and manages multi-step execution plans. Use when coordinating complex workflows that require sequential or parallel task execution across multiple agents.
license: MIT
metadata:
  version: "1.0"
---

# Plan Manager Skill

## Purpose

Manage the lifecycle of execution plans — creating new plans from goals, updating existing plans based on new information, and tracking step completion.

## When to Use

- An incoming event describes a multi-step goal that cannot be completed in a single task
- A plan needs to be created for coordinating multiple agents or tasks
- Existing plans need updates (new steps, removed steps, priority changes)

## Plan Structure

Plans consist of sequential `plan_steps`. Each step has:
- A unique identifier
- An associated agent/instruction
- A status: pending, running, completed, failed

## Decision Rules

1. **Single task sufficient?** → Use `spawn-task` instead of creating a plan
2. **Multiple independent tasks?** → Create a plan with parallel steps where possible
3. **Task depends on previous result?** → Order steps sequentially in the plan
4. **Event modifies existing plan?** → Use `update-plan` with clear change description

## Output Format

When action is `create-plan` or `update-plan`, include:
- A clear goal description in `reasoning`
- Ordered list of steps with agent assignments and instructions
