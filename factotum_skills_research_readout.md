
# Research Summary: Planning & Execution Skills for Factotum Brain
What I Researched
Browsed 4 major agent skills repositories (openai/skills, langchain-ai/langchain-skills, vercel-labs/agent-skills) and found 8 planning-relevant skills across them. Here are the patterns distilled into what matters for Factotum:

## Core Patterns Found
1. Self-Healing Loop (from OpenAI migrate-to-codex)

Plan → dry-run → execute → fix errors → validate → repeat until clean

Fit: Directly applicable to BrainService.executeTasks(). When a task executor fails, instead of just logging the error, the Brain could re-invoke with an error-aware instruction. This doesn't change the workflow — it adds a retry layer inside the existing for-loop.

2. Incremental Assembly + Validation (from OpenAI figma-generate-design)

Build one piece at a time, validate before proceeding. Errors are naturally scoped to current piece.

Fit: Relevant for create-plan / update-plan actions. When the Brain creates a multi-step plan, it should generate steps sequentially and validate each step's feasibility before committing the full plan. Could be a runtime skill injected when action is plan-related.

3. Stateless Delegation (from LangChain deep-agents-orchestration)

Subagents are ephemeral — they do not remember previous calls. The Brain must provide complete instructions in a single call.

Fit: Already implicit in Factotum's TaskExecutorFactory which creates a fresh LlmAgent per task with the full instruction. This is a constraint to document, not change.

4. 4-Tier Error Handling (from LangChain langgraph-fundamentals)

Tier Type	Strategy
1	Transient (network, rate limits)	Retry with RetryPolicy
2	LLM-recoverable (tool failures)	Return error as context so LLM self-corrects
3	User-fixable (missing info)	Interrupt / request clarification
4	Unexpected	Bubble up, fail fast
Fit: Currently Factotum only does tier 4 (catch + log). This is the biggest gap. Could be encoded as a runtime skill that the Brain loads when deciding how to handle task failures.

5. Parallel Fan-Out with Aggregation (from LangChain langgraph-fundamentals)

Spawn parallel workers via Send, aggregate results via reducer.

Fit: Currently tasks execute sequentially in a for-loop. For independent tasks from spawn-task, the Brain could signal which tasks are parallelizable. Doesn't change workflow — just adds a parallel flag to TaskSpecification.

6. TodoList as Execution Tracker (from LangChain deep-agents-orchestration)

Create full plan upfront with status tracking: pending → in_progress → completed.

Fit: Useful for create-plan / update-plan paths which are currently stubs. Each step gets a status that the Brain updates as tasks complete. Could be stored in the DB (already has plan_steps table).

7. Output Contracts Upfront (from OpenAI migrate-to-codex)

Define exact output format before starting work. Ensures consistent deliverables.

Fit: The Brain already returns structured JSON (BrainDecision). Task executors could similarly be given an expected output contract in their instruction, making responses more parseable and reliable.

## Which Patterns to Inject as Runtime Skills (No Workflow Change)
These can be added as SKILL.md files that get loaded into the Brain's context at startup — they guide the LLM's reasoning without changing code:

Skill	When Loaded	Purpose
self-healing-loop	Always available	Tells Brain to include error-recovery instructions when spawning tasks
parallel-execution	When Brain sees independent tasks	Instructs Brain to mark tasks as parallel: true in TaskSpecification
output-contract	Always available	Tells Brain to specify expected output format in task instructions
error-handling-matrix	Always available	Gives Brain the 4-tier strategy so it decides retry vs fail vs clarify
These would be injected into BrainAgentProvider alongside the existing skills, just like brain-orchestrator, code-analyzer, and plan-manager. They don't change any code path — they only influence what the LLM Brain decides.

Which Patterns to Implement as Code (Minor Additions)
Pattern	Where	Change
TodoList tracking	BrainService.executeTasks()	Update a DB status per task before/after execution
Parallel fan-out	BrainService.executeTasks()	If tasks have parallel: true, use CompletableFuture instead of sequential loop
Both are additive — the existing sequential path remains the default.

What NOT to Change
The Brain → decision → execute flow stays identical
No middleware, no interrupt/HITL (not needed for current scope)
No subagent hierarchy (TaskExecutor is flat per task)
Skill discovery mechanism stays classpath-based via SkillLoader