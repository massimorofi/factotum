---
name: code-analyzer
description: Analyzes source code, identifies issues, suggests improvements, and generates reports. Use when the event involves code review, static analysis, bug detection, or code quality assessment.
license: MIT
metadata:
  version: "1.0"
---

# Code Analyzer Skill

## Purpose

Analyze source code files and directories to identify issues, suggest improvements, and generate actionable reports.

## When to Use

- Incoming events reference specific file paths or directories for analysis
- The action involves code review, quality assessment, or bug detection
- A task requires understanding the structure of existing code before making changes

## Analysis Guidelines

1. **Scope** — Identify which files/directories are in scope from the event parameters
2. **Severity** — Classify findings as: critical, warning, info
3. **Actionability** — Each finding should include a concrete recommendation

## Output Format for Task Instructions

When spawning tasks based on code analysis, structure instructions like:

```
Analyze {target_path} for the following concerns:
- Code quality issues (naming, complexity, duplication)
- Potential bugs or edge cases
- Security vulnerabilities (injection, exposure)
- Performance bottlenecks

Return findings as a structured report with severity levels.
```
