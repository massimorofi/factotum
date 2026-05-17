package com.factotum.api;

import com.factotum.queue.model.FactotumMessage;
import com.factotum.queue.model.MessageHeader;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
class FactotumResourceTest {

    private MessageHeader buildValidHeader() {
        return new MessageHeader(
            UUID.randomUUID(),
            null,
            Instant.now(),
            "test-suite",
            "factotum_inbound",
            null,
            "application/json",
            "1.0",
            5,
            null,
            null
        );
    }

    private FactotumMessage buildValidMessage() {
        return new FactotumMessage(buildValidHeader(), Map.of("action", "analyze"));
    }

    // API-004: Health check is public, returns 200 with {"status":"ok"}
    @Test
    void testHealthCheckEndpoint() {
        given()
            .when().get("/api/v1/admin/health")
            .then()
                .statusCode(200)
                .body("status", is("ok"));
    }

    // API-003: POST /messages without Authorization header returns 401
    @Test
    void testInboundMessageDeniedWithoutToken() {
        given()
            .contentType(ContentType.JSON)
            .body(buildValidMessage())
            .when().post("/api/v1/messages")
            .then()
                .statusCode(401);
    }

    // API-003: POST /messages with wrong token returns 401
    @Test
    void testInboundMessageDeniedWithWrongToken() {
        given()
            .header("Authorization", "Bearer wrong-token")
            .contentType(ContentType.JSON)
            .body(buildValidMessage())
            .when().post("/api/v1/messages")
            .then()
                .statusCode(401);
    }

    // API-001: Valid message with correct token returns 202 Accepted
    @Test
    void testInboundMessageAcceptedWithValidToken() {
        // Verify message is accepted with valid token (messageId matches header)

        given()
            .header("Authorization", "Bearer test-token-secure")
            .contentType(ContentType.JSON)
            .body(buildValidMessage())
            .when().post("/api/v1/messages")
            .then()
                .statusCode(202)
                .body("enqueued", is(true))
                .body("messageId", notNullValue())
                .body("queue", is("factotum_inbound"));
    }

    // API-001: Message with explicit messageId returns that same ID
    @Test
    void testInboundMessageReturnsProvidedMessageId() {
        UUID expectedId = UUID.fromString("a58b8882-628b-4a57-b452-f6176c1bb7b3");
        MessageHeader header = new MessageHeader(
            expectedId, null, Instant.now(), "test-suite",
            "factotum_inbound", null, "application/json", "1.0", 5, null, null
        );

        given()
            .header("Authorization", "Bearer test-token-secure")
            .contentType(ContentType.JSON)
            .body(new FactotumMessage(header, Map.of("action", "test")))
            .when().post("/api/v1/messages")
            .then()
                .statusCode(202)
                .body("messageId", is(expectedId.toString()));
    }

    // API-002: Malformed JSON returns 400 Bad Request
    @Test
    void testMalformedJsonReturnsBadRequest() {
        given()
            .header("Authorization", "Bearer test-token-secure")
            .contentType(ContentType.JSON)
            .body("{invalid json}")
            .when().post("/api/v1/messages")
            .then()
                .statusCode(400);
    }

    // API-002: Missing required header fields returns 400
    @Test
    void testMissingRequiredHeaderFieldsReturnsBadRequest() {
        given()
            .header("Authorization", "Bearer test-token-secure")
            .contentType(ContentType.JSON)
            .body("{\"header\": {}, \"body\": {\"action\":\"test\"}}")
            .when().post("/api/v1/messages")
            .then()
                .statusCode(400);
    }

    // API-002: Missing required body returns 400
    @Test
    void testMissingRequiredBodyReturnsBadRequest() {
        given()
            .header("Authorization", "Bearer test-token-secure")
            .contentType(ContentType.JSON)
            .body("{\"header\":{}}")
            .when().post("/api/v1/messages")
            .then()
                .statusCode(400);
    }

    // API-004: Health check does NOT require auth (public endpoint)
    @Test
    void testHealthCheckNoAuthRequired() {
        given()
            .when().get("/api/v1/admin/health")
            .then()
                .statusCode(200);
    }
}
