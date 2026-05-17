# Factotum AI

A lightweight, event-driven AI orchestration system built on Java 21 + Quarkus. It consumes messages from a PostgreSQL-backed message queue (PGMQ), leverages Google ADK for Java to drive intelligent decision-making, and delegates parallel tasks to transient, stateless sub-agents equipped with callable skill tools.

---

## Architecture

```
┌─────────────┐     ┌──────────────────┐     ┌──────────────────┐
│  CLI / REST │────▶│  PGMQ Queue      │────▶│  Queue Poller    │
│  Ingestion  │     │  (PostgreSQL)    │     │  Engine          │
└─────────────┘     └──────────────────┘     └───────┬──────────┘
                                                     │
                                              ┌──────▼──────┐
                                              │  The Brain  │
                                              │(ADKLlmAgent)│
                                              └──────┬──────┘
                                                     │
                            ┌────────────────────────┼─────────────────────┐
                            │                        │                     │
                   ┌────────▼────────┐    ┌──────────▼─────────┐  ┌────────▼────────┐
                   │ TaskExecutor #1 │    │ TaskExecutor #2    │  │ DurableWorkflow │
                   │ (HttpSkillTools)│    │ (HttpSkillTools)   │  │ Engine          │
                   └─────────────────┘    └────────────────────┘  └─────────────────┘
```

### Core Components

| Component | Package | Role |
|---|---|---|
| **Queue Poller** | `com.factotum.queue` | Virtual-thread loop that polls PGMQ via JDBC, deserializes messages, and hands them to the Brain. |
| **The Brain** | `com.factotum.brain` | Google ADK `LlmAgent` that analyzes incoming events and issues structured decisions (`discard`, `spawn-task`, `create-plan`, `update-plan`). |
| **Skill Loader** | `com.factotum.brain` | Discovers and parses SKILL.md files from the classpath, composes them into the Brain's system prompt at runtime. Supports both exploded directories (dev) and JAR packaging. |
| **Task Executors** | `com.factotum.executor` | Stateless sub-agents created on-demand, bound with skill tools for transient task execution. |
| **Skill Tools** | `com.factotum.skills` | Java methods annotated with `@Schema` that ADK auto-extracts as callable function definitions for LLM agents. |
| **Workflow Engine** | `com.factotum.workflow` | Durable sequential/parallel agent orchestration backed by PostgreSQL checkpoint tracking. |
| **REST API** | `com.factotum.api` | JAX-RS endpoints for message ingestion and health checks, served via Quarkus RESTEasy Reactive. |
| **CLI** | `com.factotum.cli` | PicoCLI-based command-line tool, compilable to a GraalVM native binary (~20ms startup). |

### Runtime Skills System

The Brain's behavior is driven by SKILL.md files loaded at runtime from `src/main/resources/skills/`, following the [agentskills.io](https://agentskills.io) specification. Each skill uses YAML frontmatter (name, description, license, metadata) and a Markdown body with guidelines for the LLM.

| Skill | Description |
|---|---|
| **brain-orchestrator** | Core decision framework — analyzes events and issues `spawn-task`, `create-plan`, `update-plan`, or `discard` actions. Handles malformed JSON gracefully. |
| **plan-manager** | Plan lifecycle rules — creation, updates, status transitions, and cleanup. |
| **code-analyzer** | Code analysis guidelines — output format, severity levels, and structured reporting. |
| **self-healing-loop** | Execution cycle embedded in task instructions: plan → execute → validate → fix (up to 2 retries) → report. |
| **output-contract** | Defines expected output format per task so LLM executors return parseable, consistent results. |
| **error-handling-matrix** | 4-tier error classification — transient (retry), LLM-recoverable (self-correct), user-fixable (clarify), fatal (fail fast). |
| **parallel-execution** | Guidelines for marking independent tasks with `"parallel": true` so the executor fans them out concurrently via `CompletableFuture`. |

New skills can be added by creating a new directory under `src/main/resources/skills/` with a `SKILL.md` file — no code changes required.

### Technology Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21 LTS + Virtual Threads (Project Loom) |
| Framework | Quarkus 3.x (RESTEasy Reactive, Agroal, Hibernate Validator) |
| AI Agents | Google ADK for Java (`adk-core`) |
| Message Queue | PGMQ extension inside PostgreSQL |
| Database | PostgreSQL 16/17 |
| CLI | Picocli + GraalVM Native Image |
| Validation | Jackson records + Jakarta Validation annotations |

---

## Prerequisites

- **Java 21** (Eclipse Temurin or similar)
- **Maven 3.9+**
- **Docker & Docker Compose** (for PostgreSQL with PGMQ)
- **Local LLM endpoint** — LM Studio, Ollama, or any OpenAI-compatible server on port `1234`

---

## Configuration

Configuration is managed through [application.properties](factotum-core/src/main/resources/application.properties). Key properties:

```properties
# Database connection (PGMQ-backed PostgreSQL)
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/factotum
quarkus.datasource.username=factotum
quarkus.datasource.password=factotum_secret

# LLM Brain model — any OpenAI-compatible endpoint
factotum.llm.brain-model=openai/qwen-2.5-32b
openai.api.base=http://localhost:1234/v1
openai.api.key=local-dev-dummy

# API authentication token
factotum.api-key=dev-token-123
```

For Docker Compose deployments, override values via environment variables or a `.env` file:

```bash
POSTGRES_PASSWORD=my_secure_password
FACTOTUM_API_KEY=your-production-token
```

---

## Building

```bash
./build.sh
```

This runs `mvn clean package` across both modules (`factotum-core` and `factotum-cli`). The Quarkus fast-jar distribution is produced in `factotum-core/target/`.

To build a native CLI binary (requires GraalVM):

```bash
mvn -pl factotum-cli native:compile
```

---

## Running Locally

### 1. Start all services

```bash
./start.sh
```

This script:
- Launches PostgreSQL with the PGMQ extension via Docker Compose
- Waits for the database to become healthy
- Starts the Quarkus core in dev mode (hot-reload enabled)
- Saves the core PID to `.factotum/core.pid`

Press `Ctrl+C` to gracefully stop both services.

### 2. Dispatch messages via CLI

```bash
# Send a message with inline JSON body
./run-cli.sh -b '{"action":"analyze","target":"src/main/java"}'

# Specify a target queue
./run-cli.sh -q factotum.inbound -b '{"action":"execute","command":"build"}'

# Show help
./run-cli.sh --help
```

### 3. Dispatch messages via REST API

```bash
curl -X POST http://localhost:8080/api/v1/messages \
  -H "Content-Type: application/json" \
  -d '{
    "header": {
      "messageId": "550e8400-e29b-41d4-a716-446655440000",
      "timestamp": "2026-05-17T10:30:00Z",
      "sender": "cli",
      "destination": "factotum.inbound",
      "contentType": "application/json",
      "schemaVersion": "1.0"
    },
    "body": {
      "action": "analyze",
      "target": "src/main/java"
    }
  }'

# Health check
curl http://localhost:8080/api/v1/admin/health
```

### 4. Stop all services

```bash
./stop.sh
```

---

## Docker Deployment

Build and run the entire stack with a single command:

```bash
docker compose up --build -d
```

This starts PostgreSQL (with PGMQ) and the Quarkus core container. The application is accessible at `http://localhost:8080`.

To stop:

```bash
docker compose down
```

---

## Project Structure

```
factotum-java/
├── pom.xml                          # Parent Maven POM (multi-module)
├── factotum-core/                   # Main Quarkus service application
│   ├── pom.xml                      # Quarkus + ADK + JDBC dependencies
│   └── src/main/java/com/factotum/
│       ├── api/FactotumResource.java        # REST API endpoints
│       ├── brain/                         # AI Brain agent & decision logic
│       │   ├── BrainAgentProvider.java    # LlmAgent configuration, composes skills into system prompt
│       │   ├── BrainDecision.java         # Structured output record (action + tasks + reasoning)
│       │   ├── TaskSpecification.java     # Task definition record (taskId, instruction, parameters)
│       │   ├── Skill.java                 # Parsed skill record (name, description, instructions)
│       │   ├── SkillLoader.java           # Classpath discovery of SKILL.md files (dev + JAR)
│       │   └── BrainService.java          # Event processing: decision → sequential/parallel execution
│       ├── executor/TaskExecutorFactory.java  # Transient sub-agent factory
│       ├── queue/                         # PGMQ message queue poller
│       │   ├── QueuePollerEngine.java     # Virtual-thread polling loop
│       │   └── model/                     # Message data records
│       │       ├── FactotumMessage.java
│       │       └── MessageHeader.java
│       ├── skills/HttpSkillTools.java         # @Schema annotated skill tools for executor agents
├── factotum-core/src/main/resources/skills/   # Runtime SKILL.md files loaded by SkillLoader
│   ├── brain-orchestrator/SKILL.md            # Core decision framework & action types
│   ├── code-analyzer/SKILL.md                 # Code analysis guidelines & output format
│   ├── error-handling-matrix/SKILL.md         # 4-tier error classification strategy
│   ├── output-contract/SKILL.md               # Expected output format per task
│   ├── parallel-execution/SKILL.md            # Independent task identification & parallel flag
│   ├── plan-manager/SKILL.md                  # Plan lifecycle management rules
│   └── self-healing-loop/SKILL.md             # Self-recovery execution cycle pattern
│       └── workflow/DurableWorkflowEngine.java  # SequentialAgent + DB checkpoints
├── factotum-cli/                    # PicoCLI command-line interface module
│   ├── pom.xml                      # Picocli + GraalVM native plugin
│   └── src/main/java/com/factotum/cli/FactotumCli.java
├── docker-compose.yaml              # Docker Compose orchestration
├── Dockerfile.jvm                   # Multi-stage JVM container build
├── build.sh                         # Build script (mvn clean package)
├── start.sh                         # Start DB + Core in dev mode
├── stop.sh                          # Stop Core + DB gracefully
└── run-cli.sh                       # Run the CLI with argument forwarding
```

---

## Extending Factotum

### Adding new skill tools

Create a new `@ApplicationScoped` class in `com.factotum.skills` and annotate methods with `@Schema`. The ADK framework auto-extracts them as callable functions for executor agents:

```java
@ApplicationScoped
public class FileSkillTools {

    @Schema(name = "read_file", description = "Read the contents of a file")
    public String readFile(@Schema(description = "File path", required = true) String path) {
        return Files.readString(Path.of(path));
    }
}
```

Inject it into `TaskExecutorFactory` and bind it via `FunctionTool.create()`.

### Adding new queues

Add a new virtual thread in `QueuePollerEngine.initQueuePollers()`:

```java
Thread.startVirtualThread(() -> startPolling("factotum.tasks"));
Thread.startVirtualThread(() -> startPolling("factotum.events"));
```

Each queue runs an independent polling loop with its own message consumption.
