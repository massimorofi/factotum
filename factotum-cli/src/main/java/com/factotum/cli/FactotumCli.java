package com.factotum.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Command-line interface for the Factotum AI pipeline.
 * Subcommands: send, health, status, list, tasks, log, ask.
 */
@Command(name = "factotum", mixinStandardHelpOptions = true, version = "1.0-JAVA",
         description = "Factotum AI Command Line Interface",
         subcommands = {
             FactotumCli.Send.class,
             FactotumCli.Health.class,
             FactotumCli.Status.class,
             FactotumCli.ListCmd.class,
             FactotumCli.Tasks.class,
             FactotumCli.Log.class,
             FactotumCli.Ask.class
         })
public class FactotumCli implements Runnable {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    public static void main(String[] args) {
        int exitCode = new CommandLine(new FactotumCli()).execute(args);
        System.exit(exitCode);
    }

    private static String getBaseUrl() {
        String url = System.getenv("FACTOTUM_API_URL");
        if (url == null) {
            url = System.getProperty("factotum.api.url");
        }
        return url != null ? url : "http://localhost:8080";
    }

    private static String getApiKey() {
        String key = System.getenv("FACTOTUM_API_KEY");
        if (key == null) {
            key = System.getProperty("factotum.api.key");
        }
        return key != null ? key : "dev-token-123";
    }

    /** Show available subcommands when no arguments are given. */
    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }

    // ── Subcommand: send ────────────────────────────────────────────────

    @Command(name = "send", mixinStandardHelpOptions = true, description = "Dispatch a message to the Factotum pipeline")
    public static class Send implements Runnable {

        @Option(names = {"-b", "--body"}, description = "JSON body to enqueue (key-value pairs)", required = true)
        String body;

        @Option(names = {"-q", "--queue"}, description = "Target queue (default: factotum_inbound)")
        String queue = "factotum_inbound";

        @Option(names = {"-s", "--sender"}, description = "Sender identifier (default: cli)")
        String sender = "cli";

        @Spec
        CommandSpec spec;

        @Override
        public void run() {
            if (!isValidJsonObject(body)) {
                throw new ParameterException(spec.commandLine(),
                    "--body must be a valid JSON object (e.g. {\"action\":\"analyze\"})");
            }

            String messageId = UUID.randomUUID().toString();
            String timestamp = Instant.now().toString();

            // Build the full message envelope matching FactotumMessage + MessageHeader records
            String payload = """
                {"header":{
                    "messageId":"%s",
                    "timestamp":"%s",
                    "sender":"%s",
                    "destination":"%s",
                    "contentType":"application/json",
                    "schemaVersion":"1.0"
                },"body":%s}
                """.formatted(messageId, timestamp, sender, queue, body);

            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/v1/messages"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

                HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(HTTP_TIMEOUT)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println(response.body());
            } catch (Exception e) {
                System.err.println("Dispatch error: " + e.getMessage());
            }
        }

        /** Minimal check that body starts with '{' and ends with '}' (ignoring whitespace). */
        private boolean isValidJsonObject(String json) {
            if (json == null || json.isBlank()) return false;
            String trimmed = json.trim();
            return trimmed.startsWith("{") && trimmed.endsWith("}");
        }
    }

    // ── Subcommand: health ──────────────────────────────────────────────

    @Command(name = "health", mixinStandardHelpOptions = true, description = "Check system health and queue depth")
    public static class Health implements Runnable {

        @Override
        public void run() {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/v1/admin/health"))
                    .GET()
                    .build();

                HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(HTTP_TIMEOUT)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("Health: " + response.body());
            } catch (Exception e) {
                System.err.println("Health check failed: " + e.getMessage());
            }
        }
    }

    // ── Subcommand: status ──────────────────────────────────────────────

    @Command(name = "status", mixinStandardHelpOptions = true, description = "Check plan or task status by ID")
    public static class Status implements Runnable {

        @Parameters(index = "0", description = "Plan ID or Task ID to look up", arity = "1")
        String id;

        @Override
        public void run() {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/v1/status/" + id))
                    .header("Authorization", "Bearer " + getApiKey())
                    .GET()
                    .build();

                HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(HTTP_TIMEOUT)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println(response.body());
            } catch (Exception e) {
                System.err.println("Status lookup failed: " + e.getMessage());
            }
        }
    }

    // ── Subcommand: list ────────────────────────────────────────────────

    @Command(name = "list", mixinStandardHelpOptions = true, description = "List active plans and tasks with full details")
    public static class ListCmd implements Runnable {

        @Option(names = {"-t", "--type"}, description = "Filter by type: plans, tasks, or all (default: all)")
        String type = "all";

        @Override
        public void run() {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/v1/list?type=" + type))
                    .header("Authorization", "Bearer " + getApiKey())
                    .GET()
                    .build();

                HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(HTTP_TIMEOUT)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println(response.body());
            } catch (Exception e) {
                System.err.println("List failed: " + e.getMessage());
            }
        }
    }

    // ── Subcommand: tasks ───────────────────────────────────────────────

    @Command(name = "tasks", mixinStandardHelpOptions = true, description = "List running tasks with description, plan info and status")
    public static class Tasks implements Runnable {

        @Option(names = {"-p", "--plan"}, description = "Filter by plan ID")
        String planId;

        @Override
        public void run() {
            try {
                URI uri = URI.create(getBaseUrl() + "/api/v1/tasks");
                if (planId != null) {
                    uri = URI.create(uri.toString() + "?plan=" + planId);
                }

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Authorization", "Bearer " + getApiKey())
                    .GET()
                    .build();

                HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(HTTP_TIMEOUT)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println(response.body());
            } catch (Exception e) {
                System.err.println("Tasks lookup failed: " + e.getMessage());
            }
        }
    }

    // ── Subcommand: log ─────────────────────────────────────────────────

    @Command(name = "log", mixinStandardHelpOptions = true, description = "Tail the main core logs until stopped (Ctrl-C)")
    public static class Log implements Runnable {

        @Option(names = {"-n", "--lines"}, description = "Number of initial lines to show (default: 20)")
        int tailLines = 20;

        @Override
        public void run() {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/v1/logs/tail?lines=" + tailLines))
                    .header("Authorization", "Bearer " + getApiKey())
                    .GET()
                    .build();

                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(HTTP_TIMEOUT)
                    .build();

                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

                System.out.println("Tailing logs (Ctrl-C to stop)...");
                for (String line : response.body().split("\n")) {
                    if (!line.isBlank()) {
                        System.out.println(line);
                    }
                }
            } catch (Exception e) {
                System.err.println("Log tail failed: " + e.getMessage());
            }
        }
    }

    // ── Subcommand: ask ─────────────────────────────────────────────────

    @Command(name = "ask", mixinStandardHelpOptions = true, description = "Send a question to the brain and wait for the answer")
    public static class Ask implements Runnable {

        @Parameters(index = "0", description = "Question/message to send to the Brain agent", arity = "0..*")
        String[] messageParts;

        @Option(names = {"-w", "--wait"}, description = "Max seconds to wait for answer (default: 60)")
        int maxWaitSeconds = 60;

        @Override
        public void run() {
            if (messageParts == null || messageParts.length == 0) {
                System.err.println("Usage: factotum ask <question>");
                return;
            }

            String question = String.join(" ", messageParts);

            // Step 1: Send the question as a message to the pipeline
            String messageId = UUID.randomUUID().toString();
            String timestamp = Instant.now().toString();

            String body = "{\"question\":\"" + escapeJson(question) + "\"}";
            String payload = """
                {"header":{
                    "messageId":"%s",
                    "timestamp":"%s",
                    "sender":"cli-ask",
                    "destination":"factotum_inbound",
                    "contentType":"application/json",
                    "schemaVersion":"1.0"
                },"body":%s}
                """.formatted(messageId, timestamp, body);

            try {
                HttpRequest sendRequest = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/v1/messages"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

                HttpClient.newBuilder()
                    .connectTimeout(HTTP_TIMEOUT)
                    .build()
                    .send(sendRequest, HttpResponse.BodyHandlers.ofString());

                System.out.println("Question sent. Waiting for answer...");

                // Step 2: Poll for the result using the message ID as a status key
                Instant deadline = Instant.now().plusSeconds(maxWaitSeconds);
                int pollIntervalMs = 2000;

                while (Instant.now().isBefore(deadline)) {
                    Thread.sleep(pollIntervalMs);

                    HttpRequest statusRequest = HttpRequest.newBuilder()
                        .uri(URI.create(getBaseUrl() + "/api/v1/status/" + messageId))
                        .header("Authorization", "Bearer " + getApiKey())
                        .GET()
                        .build();

                    HttpResponse<String> statusResponse = HttpClient.newBuilder()
                        .connectTimeout(HTTP_TIMEOUT)
    
                        .build()
                        .send(statusRequest, HttpResponse.BodyHandlers.ofString());

                    String result = statusResponse.body();
                    if (result.contains("\"completed\"") || result.contains("\"answer\"")) {
                        System.out.println("\nAnswer:");
                        System.out.println(result);
                        return;
                    }
                }

                System.err.println("Timed out after " + maxWaitSeconds + " seconds. No answer received.");
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while waiting for answer.");
            } catch (Exception e) {
                System.err.println("Ask failed: " + e.getMessage());
            }
        }

        /** Escape special characters in a JSON string value. */
        private String escapeJson(String s) {
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }
}
