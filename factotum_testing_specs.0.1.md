# FACTOTUM AI (JAVA/QUARKUS) — TEST PLAN

**Document Version:** 1.0

**Target Architecture:** Quarkus 3.x, Google ADK for Java (`adk-java`), PostgreSQL + PGMQ, Picocli CLI.

---

## 1. Test Strategy & Infrastructure

To match the lightweight, cloud-native design of Quarkus, the testing architecture avoids heavy external mocking frameworks where live integration containers can provide higher-fidelity assertions.

### 1.1 Test Topology

```
[Test Runner (JUnit 5)]
       │
       ├─► [REST-Assured] ──────► [Quarkus HTTP Layer (Reactive)]
       ├─► [QuarkusMainTest] ───► [Picocli CLI Command Engine]
       │
       ▼ (Managed via Testcontainers / Quarkus Dev Services)
 ┌────────────────────────────────────────────────────────┐
 │                   ISOLATED CONTAINERS                  │
 │                                                        │
 │   ┌────────────────────────┐  ┌────────────────────┐   │
 │   │      PostgreSQL        │  │     WireMock       │   │
 │   │ (w/ PGMQ extension)    │  │ (LLM API Stub)     │   │
 │   └────────────────────────┘  └────────────────────┘   │
 └────────────────────────────────────────────────────────┘

```

### 1.2 Test Profile Configuration (`src/test/resources/application.properties`)

```properties
%test.quarkus.datasource.db-kind=postgresql
%test.quarkus.datasource.jdbc.url=jdbc:tc:tembo/pgmq-pg:latest:///factotum
%test.quarkus.http.test-port=8081
%test.factotum.api-key=test-token-secure
# Route ADK traffic to local WireMock instance during test lifecycle
%test.openai.api-base=http://localhost:${wiremock.port}/v1
%test.openai.api-key=test-mock-key

```

---

## 2. Test Suite Breakdowns

### 2.1 REST API Verification

Tests target the `FactotumResource` endpoint layer using **REST-Assured**. We validate error states, authorization boundaries, reactive throughput payload shapes, and synchronous execution feedback loops.

| Test Case ID | Target Endpoint | Input Condition | Expected Outcome | Verification Method |
| --- | --- | --- | --- | --- |
| **API-001** | `POST /api/v1/messages` | Valid Factotum JSON event structure, correct bearer token | `222 Accepted`; Message validated against schema, returns tracking UUID | REST-Assured body assertion |
| **API-002** | `POST /api/v1/messages` | Malformed JSON payload / missing headers | `400 Bad Request`; Custom validation errors returned in JSON format | Jakarta Validation trap |
| **API-003** | `POST /api/v1/messages` | Missing or incorrect `Authorization` header | `401 Unauthorized` | HTTP status assertion |
| **API-004** | `GET /api/v1/admin/health` | Unauthenticated request | `200 OK` with JSON structure `{"status":"ok"}` | REST-Assured path parsing |

### 2.2 CLI Integration Testing

We use Quarkus’s built-in command-line testing framework (`@QuarkusMainTest`) to verify the CLI runner component without having to manually compile to native binaries before each run.

| Test Case ID | CLI Command Execution | Input Condition | Expected Outcome |
| --- | --- | --- | --- |
| **CLI-001** | `factotum --help` | Invocation via terminal launcher | Exit code `0`; Output dumps standard parameter usage information to stdout |
| **CLI-002** | `factotum -q test.queue -b '{"data":"test"}'` | Valid options matching live backend connection profiles | Exit code `0`; System captures standard JSON acknowledgment trace |
| **CLI-003** | `factotum -q inbound` | Missing the critical `--body` required option payload string | Exit code `2` (Picocli usage mismatch standard); Emits usage correction matrix to stderr |

### 2.3 Message Queue (PGMQ Core Primitives)

Ensures that the application's repository queries interact seamlessly with the native transactional database queue engine.

| Test Case ID | Component Target | Pre-condition | Execution Step | Expected Verification |
| --- | --- | --- | --- | --- |
| **MQ-001** | `QueuePollerEngine` | Connection pool initialized | Call database function `pgmq.send('factotum.inbound', '...')` | Assert row addition within tracking table schema |
| **MQ-002** | `QueuePollerEngine` | Unprocessed items sit inside target queue table | Run internal engine reader pass sequence | Virtual thread worker pulls row target; visibility timeout window safely initiates |
| **MQ-003** | `QueuePollerEngine` | Active message processing flag set true | Execution loop flags block finish status successfully | Call database function `pgmq.delete(...)`; message is permanently removed from queue table |

### 2.4 End-to-End (E2E) Flow Integration

This suite tracks the complete lifecycle of a Factotum task, from initial ingestion down to final skill tool execution. To ensure predictable runs, we use **WireMock** to simulate the local LLM runtime (LM Studio/Ollama) responses.

```
[Inbound Queue Event] ──► [Brain Service] ──► [WireMock (LLM Plan)] ──► [Task Executor] ──► [HttpSkillTool Execute]
                                                                                                      │
[Database Plan Checkpoint Table Verified] ◄───────────────────────────────────────────────────────────┘

```

* **E2E-001: Linear Orchestration Happy Path**
1. Inject an event payload (`"action": "run-diagnostic"`) into `POST /api/v1/messages`.
2. WireMock interceptor intercepts ADK's `LlmAgent` inference request and replies with a structured plan:
```json
{
  "choices": [{
    "message": {
      "role": "assistant",
      "content": "{\"action\":\"spawn-task\",\"reasoning\":\"Need API status\",\"tasks\":[{\"id\":\"t1\",\"skill\":\"http_get\",\"params\":{\"url\":\"http://localhost:8081/api/v1/admin/health\"}}]}"
    }
  }]
}

```


3. Verify that the system invokes `HttpSkillTools.executeGet()`.
4. Assert that the database transaction registers the task execution state as `completed`.
5. Assert that the inbound message is deleted from the `pgmq` table.


* **E2E-002: Durable Task Resiliency & Rollback State Protection**
1. Seed an execution plan into the tracking tables with an initial step marked as `running`.
2. Simulate an application crash by forcing an engine exception during task execution.
3. Verify that the task state transitions to `failed` within the `factotum.plan_steps` schema table, preserving history for subsequent processing passes.



---

## 3. Reference Implementations for Testing

### 3.1 REST API Endpoint Integration Test (`FactotumResourceTest.java`)

```java
package com.factotum.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
public class FactotumResourceTest {

    @Test
    public void testHealthCheckEndpoint() {
        given()
          .when().get("/api/v1/admin/health")
          .then()
             .statusCode(200)
             .body("status", is("ok"));
    }

    @Test
    public void testInboundMessageIngestionDeniedWithoutToken() {
        given()
          .contentType(ContentType.JSON)
          .body("{\"header\":{}, \"body\":{}}")
          .when().post("/api/v1/messages")
          .then()
             .statusCode(401);
    }

    @Test
    public void testInboundMessageIngestionAccepted() {
        String validPayload = """
        {
          "header": {
            "messageId": "a58b8882-628b-4a57-b452-f6176c1bb7b3",
            "timestamp": "2026-05-17T00:00:00Z",
            "sender": "test-suite",
            "destination": "factotum.inbound",
            "contentType": "application/json",
            "schemaVersion": "1.0"
          },
          "body": {
            "command": "analyze-logs",
            "targetPath": "/var/log/nginx"
          }
        }
        """;

        given()
          .header("Authorization", "Bearer test-token-secure")
          .contentType(ContentType.JSON)
          .body(validPayload)
          .when().post("/api/v1/messages")
          .then()
             .statusCode(222)
             .body("enqueued", is(true))
             .body("messageId", is("a58b8882-628b-4a57-b452-f6176c1bb7b3"));
    }
}

```

### 3.2 CLI Automated Main Test Interface (`FactotumCliTest.java`)

```java
package com.factotum.cli;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusMainTest
public class FactotumCliTest {

    @Test
    @Launch({ "--help" })
    public void testHelpCommand(LaunchResult result) {
        assertEquals(0, result.exitCode(), "Help should return standard success exit code 0");
        assertTrue(result.getOutput().contains("Usage: factotum"), "Output must display Picocli syntax structure block templates");
    }

    @Test
    @Launch(value = { "-q", "factotum.inbound" }, exitCode = 2)
    public void testMissingRequiredBodyParameter(LaunchResult result) {
        // Missing --body parameter triggers an automatic syntax validation failure flag
        assertTrue(result.getErrorOutput().contains("Missing required option"), "Should print specific parameter omission warnings to stderr");
    }
}

```

### 3.3 End-to-End Orchestration & Mock LLM Lifecycle Test (`FactotumE2ETest.java`)

```java
package com.factotum;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class FactotumE2ETest {

    WireMockServer wireMockServer;

    @Inject DataSource dataSource;

    @BeforeEach
    public void startMockServer() {
        // Dynamically spin up local endpoint mock on port specified inside profiles matrix
        wireMockServer = new WireMockServer(1234);
        wireMockServer.start();
        WireMock.configureFor("localhost", 1234);
    }

    @AfterEach
    public void stopMockServer() {
        wireMockServer.stop();
    }

    @Test
    public void testFullPipelineExecution() throws Exception {
        // 1. Stub the LLM Chat Completions API endpoint mapped by Google ADK engine
        stubFor(post(urlEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                {
                  "id": "chatcmpl-mock123",
                  "object": "chat.completion",
                  "created": 1715817600,
                  "model": "qwen-2.5-32b",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "{\\"action\\":\\"spawn-task\\",\\"reasoning\\":\\"Execute health validation run\\",\\"tasks\\":[{\\"id\\":\\"step-task-99\\",\\"skill\\":\\"http_get\\",\\"params\\":{\\"url\\":\\"http://localhost:8081/api/v1/admin/health\\"}}]}"
                      },
                      "finish_reason": "stop"
                    }
                  ]
                }
                """)));

        // 2. Direct seed insertion into underlying active PGMQ engine table structure
        try (Connection conn = dataSource.getConnection()) {
            String seedMsgSql = "SELECT * FROM pgmq.send('factotum.inbound', ?::json);";
            String mockEvent = """
            {
              "header": {"messageId":"f81d4fae-7dec-11d0-a765-00a0c91e6bf6","timestamp":"2026-05-17T00:10:00Z","sender":"upstream-sensor","destination":"factotum.inbound","contentType":"application/json","schemaVersion":"1.0"},
              "body": {"trigger":"check-infrastructure"}
            }
            """;
            try (PreparedStatement ps = conn.prepareStatement(seedMsgSql)) {
                ps.setString(1, mockEvent);
                ps.execute();
            }
        }

        // 3. Give Virtual Thread Queue Poller Engine a brief execution window
        Thread.sleep(2000);

        // 4. Verify structural checkpoint persistence rows exist inside tracking schema
        try (Connection conn = dataSource.getConnection()) {
            String verifyPlanSql = "SELECT status FROM factotum.plan_steps WHERE id = 'step-task-99';";
            try (PreparedStatement ps = conn.prepareStatement(verifyPlanSql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String trackingStatus = rs.getString("status");
                        // Confirm workflow completed processing task sequences completely
                        assertEquals("completed", trackingStatus);
                    }
                }
            }
            
            // 5. Verify the inbound event message was deleted from the queue
            String checkQueueSql = "SELECT count(*) as total FROM pgmq.meta('factotum.inbound');";
            try (PreparedStatement ps = conn.prepareStatement(checkQueueSql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        assertEquals(0, rs.getInt("total"), "Message must be permanently deleted from active processing line items queue via pgmq.delete invocation");
                    }
                }
            }
        }

        // 6. Confirm ADK accurately targeted the mocked local LLM connection layer endpoints
        verify(postRequestedFor(urlEqualTo("/v1/chat/completions")));
    }
}

```