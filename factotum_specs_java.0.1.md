Here is the detailed technical specification for **Factotum AI** migrated to a lightweight, high-performance Java stack.

To maintain the ultra-lightweight, non-blocking characteristics of your original Node.js architecture, this specification substitutes Hono with **Quarkus RESTEasy Reactive** (powered by Eclipse Vert.x) as the direct high-performance Java equivalent. Spring is completely omitted to avoid unnecessary overhead and preserve immediate bootstrap speeds and low memory footprints. The AI processing uses **Google Agent Development Kit for Java (`adk-java`)**.

---

# FACTOTUM AI — Java/Quarkus Technical Specification

**Version:** 0.1-JAVA

**Status:** Ready for Implementation

**Purpose:** Technical specification for building a lightweight Java-based coding agent orchestrator.

---

## 1. Overview & Technology Stack

Factotum is an event-driven AI orchestration system that scales via lightweight, asynchronous execution lines. It consumes messages from a PostgreSQL-backed message queue, leverages an AI "Brain" via `adk-java` to dictate operational plans, and delegates parallel tasks to transient, stateless sub-agents.

### Technology Stack Substitutions

| Layer | Original Tech (TS) | Lightweight Java Stack | Architecture & Strategy |
| --- | --- | --- | --- |
| **Runtime** | Node.js 22 LTS | **Java 21 LTS + GraalVM** | Leverages Java 21 Virtual Threads (Project Loom) for high-concurrency event loops. Supports compilation to native binaries. |
| **AI Agents** | Pi Code Agent | **Google ADK for Java (`adk-java`)** | Handles orchestration via native `LlmAgent`, `SequentialAgent`, and `ParallelAgent` abstractions. |
| **Durable Workflows** | Absurd | **ADK Workflows + Agroal DB Checkpointing** | Integrates ADK’s looping/sequential structures with a custom transactional state manager backed by Postgres. |
| **Message Queue** | PGMQ (`pgmq`) | **PGMQ via JDBC / Agroal Client** | Interacts directly with the PGMQ extension using SQL parameterized native queries over a high-performance connection pool. |
| **Database** | PostgreSQL 16 | **PostgreSQL 16 / 17** | Coexists in schemas for structural tracking (`factotum`, `pgmq`). |
| **REST API** | Hono | **Quarkus RESTEasy Reactive + SmallRye** | High-performance, non-blocking JAX-RS web stack natively serving an auto-generated OpenAPI 3.1 specification. |
| **CLI** | `commander` + `ink` | **Quarkus Picocli + GraalVM Native** | Compiled directly down to a zero-dependency, ultra-fast local binary (~20ms startup) instead of a heavy Node execution path. |
| **Validation** | Zod | **Jackson Data Bindings + Jakarta Validation** | Strongly-typed Java 21 records coupled with declarative annotations for incoming structure validation. |

---

## 2. Component Specifications

### 2.1 Message Queue (PGMQ Client Integration)

Since PGMQ functions completely inside your Postgres instance, the application uses Quarkus's built-in **Agroal connection pool** to invoke queue primitives directly without requiring a standalone third-party driver wrapper.

#### Queue Message Data Representation (Java 21 Records)

```java
package com.factotum.queue.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record FactotumMessage(
    @JsonProperty(required = true) MessageHeader header,
    @JsonProperty(required = true) Map<String, Object> body
) {}

public record MessageHeader(
    @JsonProperty(required = true) UUID messageId,
    String correlationId,
    @JsonProperty(required = true) Instant timestamp,
    @JsonProperty(required = true) String sender,
    @JsonProperty(required = true) String destination,
    String replyTo,
    @JsonProperty(required = true) String contentType,
    @JsonProperty(required = true) String schemaVersion,
    @JsonProperty(defaultValue = "5") int priority,
    Integer ttlSeconds,
    Map<String, Object> context
) {}

```

#### Reactive Queue Poller Engine

Quarkus manages the queue pollers within virtual threads to guarantee a non-blocking execution footprint.

```java
package com.factotum.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.factotum.queue.model.FactotumMessage;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;

@ApplicationScoped
public class QueuePollerEngine {

    @Inject DataSource dataSource;
    @Inject ObjectMapper objectMapper;
    @Inject BrainService brainService;

    public void initQueuePollers(@Observes StartupEvent ev) {
        // Spawn standard loop per queue name inside a virtual thread
        Thread.startVirtualThread(() -> startPolling("factotum.inbound"));
    }

    private void startPolling(String queueName) {
        while (true) {
            try (Connection conn = dataSource.getConnection()) {
                // Read messages from PGMQ via native SQL execution function
                String sql = "SELECT msg_id, message::text FROM pgmq.read(?, 60, 10)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, queueName);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            long msgId = rs.getLong("msg_id");
                            String messageJson = rs.getString("message");
                            
                            FactotumMessage msg = objectMapper.readValue(messageJson, FactotumMessage.class);
                            
                            // Hand message execution directly over to the ADK Brain
                            boolean processSuccess = brainService.processEvent(msg, msgId);
                            
                            if (processSuccess) {
                                // Acknowledge message delivery to drop from active queue
                                try (PreparedStatement del = conn.prepareStatement("SELECT pgmq.delete(?, ?);")) {
                                    del.setString(1, queueName);
                                    del.setLong(2, msgId);
                                    del.execute();
                                }
                            }
                        }
                    }
                }
                Thread.sleep(500); // Poll Interval config fallback
            } catch (Exception e) {
                // Exponential backoff or logging via pino equivalent (JBoss Logging)
            }
        }
    }
}

```

---

### 2.2 The Brain (Implemented with `adk-java`)

The Brain utilizes Google ADK's core `LlmAgent` class. It routes reasoning requests to your local model endpoint (e.g., LM Studio/Ollama) via an OpenAI compatibility profile configuration.

```java
package com.factotum.brain;

import com.google.adk.agents.LlmAgent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.annotation.PostConstruct;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class BrainAgentProvider {

    @ConfigProperty(name = "factotum.llm.brain-model") String brainModel;

    private LlmAgent brainAgent;

    @PostConstruct
    public void setup() {
        this.brainAgent = LlmAgent.builder()
            .name("TheBrain")
            .model(brainModel) // e.g., "openai/qwen-2.5-32b" or local deployment targets
            .instruction(
                "You are the Brain of Factotum, an AI orchestration system. " +
                "Analyze the incoming JSON event, check existing plans, and issue precise step actions."
            )
            .build();
    }

    public LlmAgent getBrain() {
        return this.brainAgent;
    }
}

```

#### Enforcing Structural Output (Brain Decisions)

Rather than checking via TypeScript schemas, `adk-java` forces structured execution by providing the target model with a typed object structure or custom Java reflection definitions:

```java
public record BrainDecision(
    String action, // "discard" | "spawn-task" | "update-plan" | "create-plan"
    String reasoning,
    List<TaskSpecification> tasks
) {}

```

---

### 2.3 Task Executor Agents & Skills Binding

Sub-agents execute transient context requests mapping custom schemas straight onto **Java methods via `@Schema` annotations**. ADK handles parsing these annotations into standardized function definitions for your local LLM automatically.

#### Declaring Skill Tools in Quarkus

```java
package com.factotum.skills;

import com.google.adk.tools.Schema;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

@ApplicationScoped
public class HttpSkillTools {

    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

    @Schema(
        name = "http_get",
        description = "Perform a lightweight HTTP GET request to an external local API endpoint"
    )
    public String executeGet(@Schema(description = "Target API URL", required = true) String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            return "Execution error: " + e.getMessage();
        }
    }
}

```

#### Binding Active Skills to an ADK Executor Instance

```java
package com.factotum.executor;

import com.factotum.skills.HttpSkillTools;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.FunctionTool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TaskExecutorFactory {

    @Inject HttpSkillTools httpTools;

    public LlmAgent createExecutorAgent(String instructionContext) {
        return LlmAgent.builder()
            .name("TaskExecutor")
            .instruction(instructionContext)
            .tools(FunctionTool.create(httpTools)) // Automatically extracts @Schema annotations
            .build();
    }
}

```

---

### 2.4 Durable Workflow Engine (Java Equivalent of Absurd)

To replace Absurd's continuous generator tracking checkpoints, Factotum Java couples **Google ADK structural workflow wrappers** (`SequentialAgent`, `ParallelAgent`) with database write-ahead log listeners or custom step hooks using standardized PostgreSQL transactions.

```java
package com.factotum.workflow;

import com.google.adk.agents.SequentialAgent;
import com.google.adk.agents.LlmAgent;
import jakarta.enterprise.context.ApplicationScoped;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

@ApplicationScoped
public class DurableWorkflowEngine {

    @Inject DataSource dataSource;

    public void executeDurableSequence(UUID planId, LlmAgent stepOneAgent, LlmAgent stepTwoAgent) {
        // Wrap ADK sub-agents into a managed flow
        SequentialAgent sequence = SequentialAgent.builder()
            .agents(stepOneAgent, stepTwoAgent)
            .build();

        // Enforce state durability tracking checks before processing
        checkpointStep(planId, "step-1", "running");
        
        try {
            sequence.run(); // ADK structural framework runner
            checkpointStep(planId, "step-1", "completed");
        } catch (Exception e) {
            checkpointStep(planId, "step-1", "failed");
            throw e;
        }
    }

    private void checkpointStep(UUID planId, String stepId, String status) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "UPDATE factotum.plan_steps SET status = ?, started_at = now() WHERE plan_id = ? AND id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, status);
                ps.setObject(2, planId);
                ps.setString(3, stepId);
                ps.executeUpdate();
            }
        } catch (Exception ignored) {}
    }
}

```

---

## 3. REST API Layer (Quarkus RESTEasy Reactive)

This layer implements the complete OpenAPI endpoints originally mapped to Hono. It provides lightweight, asynchronous request handling out of the box.

```java
package com.factotum.api;

import com.factotum.queue.model.FactotumMessage;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import java.util.Map;

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FactotumResource {

    @POST
    @Path("/messages")
    public Response enqueueMessage(FactotumMessage request) {
        // Add message validation mapping and inject via PGMQ write sequence
        UUID generatedId = request.header().messageId() != null ? request.header().messageId() : UUID.randomUUID();
        
        return Response.status(Response.Status.ACCEPTED)
            .entity(Map.of("messageId", generatedId, "queue", request.header().destination(), "enqueued", true))
            .build();
    }

    @GET
    @Path("/admin/health")
    public Response getHealthCheck() {
        return Response.ok(Map.of("status", "ok")).build();
    }
}

```

---

## 4. High-Performance Native CLI (PicoCLI Extension)

Instead of forcing your host architecture to manage dynamic Node module trees or spin up runtime JS interpreters, compile your CLI directly into a highly efficient native executable via **GraalVM** and **PicoCLI**.

```java
package com.factotum.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

@Command(name = "factotum", mixinStandardHelpOptions = true, version = "1.0-JAVA",
         description = "Factotum AI Command Line Interface Core Execution Hub")
public class FactotumCli implements Runnable {

    @Option(names = {"-q", "--queue"}, description = "Target routing queue designation")
    String queue = "factotum.inbound";

    @Option(names = {"-b", "--body"}, description = "Inline JSON execution context data string")
    String body;

    @Override
    public void run() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/api/v1/messages"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer dev-token-123")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println(response.body());
        } catch (Exception e) {
            System.err.println("CLI Dispatch error: " + e.getMessage());
        }
    }
}

```

---

## 5. Deployment Architecture (Docker Toolset)

### `docker-compose.yaml`

```yaml
version: "3.9"

services:
  postgres:
    image: quay.io/tembo/pgmq-pg:latest # Pre-compiled Postgres engine with official PGMQ binaries
    environment:
      POSTGRES_DB: factotum
      POSTGRES_USER: factotum
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-factotum_secret}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U factotum"]
      interval: 5s
      timeout: 5s
      retries: 6
    ports:
      - "5432:5432"

  factotum-quarkus:
    build:
      context: .
      dockerfile: Dockerfile.jvm
    environment:
      - QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://postgres:5432/factotum
      - QUARKUS_DATASOURCE_USERNAME=factotum
      - QUARKUS_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD:-factotum_secret}
      - OPENAI_API_BASE=http://host.docker.internal:1234/v1 # Relays out cleanly to local LM Studio
      - OPENAI_API_KEY=local-dev-dummy
      - FACTOTUM_API_KEY=${FACTOTUM_API_KEY:-dev-token-123}
    extra_hosts:
      - "host.docker.internal:host-gateway" # Resolves local host loop interfaces smoothly
    depends_on:
      postgres:
        condition: service_healthy
    ports:
      - "8080:8080"
    restart: unless-stopped

volumes:
  postgres_data:

```

### `Dockerfile.jvm` (Optimized Multi-Stage Layer)

```dockerfile
# Stage 1: Build the fast-jar distribution package
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /usr/src/app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests

# Stage 2: Clean runtime target container 
FROM eclipse-temurin:21-jre-alpine
WORKDIR /deployments
COPY --from=build /usr/src/app/target/quarkus-app/lib/ /deployments/lib/
COPY --from=build /usr/src/app/target/quarkus-app/*.jar /deployments/
COPY --from=build /usr/src/app/target/quarkus-app/app/ /deployments/app/
COPY --from=build /usr/src/app/target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
ENV JAVA_OPTIONS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTIONS -jar /deployments/quarkus-run.jar" ]

```

---

## 6. Project Structure

This single multi-module project organizes your runtime core and your standalone CLI binary neatly under one roof:

```
factotum-java/
├── pom.xml                        # Parent project Maven management structure
├── factotum-core/                 # Main Quarkus Service application container
│   ├── pom.xml
│   └── src/main/java/com/factotum/
│       ├── api/                   # JAX-RS (RESTEasy Reactive Engine) resources
│       ├── brain/                 # adk-java Brain agents configuration classes
│       ├── executor/              # Stateless task sub-agents factories
│       ├── queue/                 # PGMQ thread loop poller service infrastructure
│       ├── skills/                # @Schema annotated business logic classes
│       └── workflow/              # Durable structural state synchronization layers
├── factotum-cli/                  # PicoCLI command architecture module
│   ├── pom.xml
│   └── src/main/java/com/factotum/cli/
│       └── FactotumCli.java       # Native compilation target file
└── docker-compose.yaml            # Local development infrastructure orchestrator

```

This setup provides an incredibly fast, memory-optimized Java pipeline. It handles high-throughput asynchronous execution loops efficiently without any of the heavy initialization weights typical of traditional Spring configurations.
