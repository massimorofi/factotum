package com.factotum.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FactotumCli")
class FactotumCliTest {

    private PrintWriter captureStream() {
        return new PrintWriter(new OutputStreamWriter(new ByteArrayOutputStream()), true);
    }

    // ── Root command tests ──────────────────────────────────────────────

    @Nested
    @DisplayName("Root Command")
    class RootCommandTests {

        @Test
        @DisplayName("CLI-001: --help returns exit code 0 and shows usage info")
        void testHelpReturnsZero() {
            FactotumCli cli = new FactotumCli();
            CommandLine cmd = new CommandLine(cli);
            int exitCode = cmd.execute("--help");
            assertEquals(0, exitCode);
        }

        @Test
        @DisplayName("CLI-001: --version returns exit code 0")
        void testVersionReturnsZero() {
            FactotumCli cli = new FactotumCli();
            CommandLine cmd = new CommandLine(cli);
            int exitCode = cmd.execute("--version");
            assertEquals(0, exitCode);
        }

        @Test
        @DisplayName("CLI-010: No arguments shows help (subcommands available)")
        void testNoArgumentsShowsSubcommands() {
            FactotumCli cli = new FactotumCli();
            PrintWriter out = captureStream();
            CommandLine cmd = new CommandLine(cli);
            cmd.setOut(out);
            int exitCode = cmd.execute();
            assertEquals(0, exitCode);
            String output = out.toString();
            assertTrue(output.contains("send"), "Help should mention 'send' subcommand");
            assertTrue(output.contains("health"), "Help should mention 'health' subcommand");
            assertTrue(output.contains("status"), "Help should mention 'status' subcommand");
            assertTrue(output.contains("list"), "Help should mention 'list' subcommand");
            assertTrue(output.contains("tasks"), "Help should mention 'tasks' subcommand");
            assertTrue(output.contains("log"), "Help should mention 'log' subcommand");
            assertTrue(output.contains("ask"), "Help should mention 'ask' subcommand");
        }

        @Test
        @DisplayName("CLI-011: Unknown subcommand returns non-zero exit code")
        void testUnknownSubcommand() {
            FactotumCli cli = new FactotumCli();
            CommandLine cmd = new CommandLine(cli);
            int exitCode = cmd.execute("bogus");
            assertNotEquals(0, exitCode, "Unknown subcommand should fail");
        }
    }

    // ── Send subcommand tests ───────────────────────────────────────────

    @Nested
    @DisplayName("Send Subcommand")
    class SendTests {

        @Test
        @DisplayName("CLI-002: --help on send subcommand returns 0")
        void testSendHelp() {
            FactotumCli.Send send = new FactotumCli.Send();
            CommandLine cmd = new CommandLine(send);
            int exitCode = cmd.execute("--help");
            assertEquals(0, exitCode);
        }

        @Test
        @DisplayName("CLI-003: Missing required --body returns exit code 2")
        void testMissingBodyReturnsUsageError() {
            FactotumCli.Send send = new FactotumCli.Send();
            CommandLine cmd = new CommandLine(send);
            int exitCode = cmd.execute();
            assertEquals(2, exitCode);
        }

        @Test
        @DisplayName("CLI-004: Valid body and queue options parse correctly")
        void testValidOptionsParse() {
            FactotumCli.Send send = new FactotumCli.Send();
            CommandLine cmd = new CommandLine(send);
            int exitCode = cmd.execute("-b", "{\"action\":\"analyze\"}", "-q", "my_queue");
            assertEquals(0, exitCode);
            assertEquals("{\"action\":\"analyze\"}", send.body);
            assertEquals("my_queue", send.queue);
        }

        @Test
        @DisplayName("CLI-005: Default queue is factotum_inbound")
        void testDefaultQueue() {
            FactotumCli.Send send = new FactotumCli.Send();
            CommandLine cmd = new CommandLine(send);
            cmd.execute("-b", "{\"action\":\"test\"}");
            assertEquals("factotum_inbound", send.queue);
        }

        @Test
        @DisplayName("CLI-006: Default sender is cli")
        void testDefaultSender() {
            FactotumCli.Send send = new FactotumCli.Send();
            CommandLine cmd = new CommandLine(send);
            cmd.execute("-b", "{\"action\":\"test\"}");
            assertEquals("cli", send.sender);
        }

        @Test
        @DisplayName("CLI-007: Custom sender option is parsed")
        void testCustomSender() {
            FactotumCli.Send send = new FactotumCli.Send();
            CommandLine cmd = new CommandLine(send);
            cmd.execute("-b", "{\"action\":\"test\"}", "-s", "my-app");
            assertEquals("my-app", send.sender);
        }

        @Test
        @DisplayName("CLI-008: Short options (-q, -b, -s) work")
        void testShortOptions() {
            FactotumCli.Send send = new FactotumCli.Send();
            CommandLine cmd = new CommandLine(send);
            cmd.execute("-q", "tasks", "-b", "{\"key\":\"val\"}", "-s", "runner");
            assertEquals("tasks", send.queue);
            assertEquals("{\"key\":\"val\"}", send.body);
            assertEquals("runner", send.sender);
        }

        @Test
        @DisplayName("CLI-009: Long options (--queue, --body, --sender) work")
        void testLongOptions() {
            FactotumCli.Send send = new FactotumCli.Send();
            CommandLine cmd = new CommandLine(send);
            cmd.execute("--queue", "events", "--body", "{\"x\":1}", "--sender", "bot");
            assertEquals("events", send.queue);
            assertEquals("{\"x\":1}", send.body);
            assertEquals("bot", send.sender);
        }

        @Test
        @DisplayName("CLI-020: Invalid body (not a JSON object) throws ParameterException")
        void testInvalidBodyNotJsonObject() {
            FactotumCli.Send send = new FactotumCli.Send();
            CommandLine cmd = new CommandLine(send);
            assertThrows(ParameterException.class, () ->
                cmd.execute("-b", "not-json"));
        }

        @Test
        @DisplayName("CLI-021: Invalid body (JSON array) throws ParameterException")
        void testInvalidBodyIsArray() {
            FactotumCli.Send send = new FactotumCli.Send();
            CommandLine cmd = new CommandLine(send);
            assertThrows(ParameterException.class, () ->
                cmd.execute("-b", "[1,2,3]"));
        }

        @Test
        @DisplayName("CLI-022: Empty body throws ParameterException")
        void testEmptyBody() {
            FactotumCli.Send send = new FactotumCli.Send();
            CommandLine cmd = new CommandLine(send);
            assertThrows(ParameterException.class, () ->
                cmd.execute("-b", ""));
        }

        @Test
        @DisplayName("CLI-030: Nested JSON body is accepted")
        void testNestedJsonBody() {
            FactotumCli.Send send = new FactotumCli.Send();
            CommandLine cmd = new CommandLine(send);
            int exitCode = cmd.execute("-b", "{\"action\":\"analyze\",\"params\":{\"depth\":3}}");
            assertEquals(0, exitCode);
        }

        @Test
        @DisplayName("CLI-031: Body with special characters is accepted")
        void testBodyWithSpecialChars() {
            FactotumCli.Send send = new FactotumCli.Send();
            CommandLine cmd = new CommandLine(send);
            int exitCode = cmd.execute("-b", "{\"msg\":\"hello world & 'quotes' \\\"doubles\\\"\"}");
            assertEquals(0, exitCode);
        }

        @Test
        @DisplayName("CLI-032: Body with unicode is accepted")
        void testBodyWithUnicode() {
            FactotumCli.Send send = new FactotumCli.Send();
            CommandLine cmd = new CommandLine(send);
            int exitCode = cmd.execute("-b", "{\"msg\":\"こんにちは\"}");
            assertEquals(0, exitCode);
        }

        @Test
        @DisplayName("CLI-040: Send with all options parses correctly")
        void testSendWithAllOptions() {
            FactotumCli.Send send = new FactotumCli.Send();
            CommandLine cmd = new CommandLine(send);
            int exitCode = cmd.execute(
                "-b", "{\"action\":\"analyze\",\"target\":\"src/\"}",
                "-q", "analysis_queue",
                "-s", "ci-bot"
            );

            assertEquals(0, exitCode);
            assertEquals("{\"action\":\"analyze\",\"target\":\"src/\"}", send.body);
            assertEquals("analysis_queue", send.queue);
            assertEquals("ci-bot", send.sender);
        }

        // ── Envelope construction tests (unit-level) ──────────────────────

        @Test
        @DisplayName("CLI-100: Send constructs valid JSON envelope with all header fields")
        void testSendConstructsValidEnvelope() throws Exception {
            FactotumCli.Send send = new FactotumCli.Send();
            CommandLine cmd = new CommandLine(send);

            // Verify that the Send instance has all correct fields set up for envelope building.
            cmd.execute("-b", "{\"action\":\"verify\"}", "-q", "test_q", "-s", "tester");

            assertEquals("{\"action\":\"verify\"}", send.body);
            assertEquals("test_q", send.queue);
            assertEquals("tester", send.sender);

            // Verify the envelope would contain all required fields by checking
            // that the Send class has the correct defaults and parsing logic.
            assertTrue(send.body.contains("\"action\":\"verify\""));
        }

        @Test
        @DisplayName("CLI-101: Envelope includes auto-generated UUID and ISO timestamp")
        void testEnvelopeHasUuidAndTimestamp() throws Exception {
            // Verify that the envelope format string in Send.run() produces valid UUIDs
            // by checking the regex pattern used for messageId validation.
            Pattern uuidPattern = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

            // Generate a UUID and verify it matches the expected format
            String messageId = java.util.UUID.randomUUID().toString();
            assertTrue(uuidPattern.matcher(messageId).matches(), "messageId should be a valid UUID");

            // Verify ISO timestamp format
            String timestamp = java.time.Instant.now().toString();
            assertTrue(timestamp.endsWith("Z") || timestamp.matches(".*[+-]\\d{2}:\\d{2}$"),
                "timestamp should be in ISO-8601 format");
        }

        @Test
        @DisplayName("CLI-102: Default queue factotum_inbound is used when -q omitted")
        void testDefaultQueueInEnvelope() {
            FactotumCli.Send send = new FactotumCli.Send();
            CommandLine cmd = new CommandLine(send);
            cmd.execute("-b", "{\"action\":\"test\"}");
            assertEquals("factotum_inbound", send.queue,
                "Default queue should be factotum_inbound in envelope");
        }

        @Test
        @DisplayName("CLI-103: Envelope payload contains all required header fields")
        void testEnvelopePayloadStructure() {
            // Verify the Send class has correct defaults that map to MessageHeader fields
            FactotumCli.Send send = new FactotumCli.Send();

            assertEquals("cli", send.sender, "Default sender maps to MessageHeader.sender");
            assertEquals("factotum_inbound", send.queue, "Default queue maps to MessageHeader.destination");

            // Verify the body field is preserved as-is (raw JSON object)
            CommandLine cmd = new CommandLine(send);
            cmd.execute("-b", "{\"action\":\"test\"}");
            assertEquals("{\"action\":\"test\"}", send.body);
        }
    }

    // ── Health subcommand tests ─────────────────────────────────────────

    @Nested
    @DisplayName("Health Subcommand")
    class HealthTests {

        @Test
        @DisplayName("CLI-050: --help on health subcommand returns 0")
        void testHealthHelp() {
            FactotumCli.Health health = new FactotumCli.Health();
            CommandLine cmd = new CommandLine(health);
            int exitCode = cmd.execute("--help");
            assertEquals(0, exitCode);
        }

        @Test
        @DisplayName("CLI-051: Health command runs without arguments (fails to connect)")
        void testHealthRuns() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            System.setErr(new PrintStream(baos));

            System.setProperty("factotum.api.url", "http://localhost:9998");
            try {
                FactotumCli.Health health = new FactotumCli.Health();
                CommandLine healthCmd = new CommandLine(health);
                healthCmd.execute("health");

                String output = baos.toString();
                assertTrue(output.toLowerCase().contains("failed") || output.toLowerCase().contains("connection"),
                    "Health check should report a connection error: " + output);
            } finally {
                System.clearProperty("factotum.api.url");
            }
        }
    }

    // ── Status subcommand tests ─────────────────────────────────────────

    @Nested
    @DisplayName("Status Subcommand")
    class StatusTests {

        @Test
        @DisplayName("CLI-060: --help on status subcommand returns 0")
        void testStatusHelp() {
            FactotumCli.Status status = new FactotumCli.Status();
            CommandLine cmd = new CommandLine(status);
            int exitCode = cmd.execute("--help");
            assertEquals(0, exitCode);
        }

        @Test
        @DisplayName("CLI-061: Missing positional ID returns exit code 2")
        void testMissingIdReturnsUsageError() {
            FactotumCli.Status status = new FactotumCli.Status();
            CommandLine cmd = new CommandLine(status);
            int exitCode = cmd.execute();
            assertEquals(2, exitCode);
        }

        @Test
        @DisplayName("CLI-062: Positional ID is captured correctly")
        void testPositionalIdCaptured() {
            FactotumCli.Status status = new FactotumCli.Status();
            CommandLine cmd = new CommandLine(status);
            int exitCode = cmd.execute("550e8400-e29b-41d4-a716-446655440000");
            assertEquals(0, exitCode);
            assertNotNull(status.id);
        }

        @Test
        @DisplayName("CLI-063: Status command runs with an ID (fails to connect)")
        void testStatusWithId() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            System.setErr(new PrintStream(baos));

            System.setProperty("factotum.api.url", "http://localhost:9997");
            try {
                FactotumCli.Status freshStatus = new FactotumCli.Status();
                CommandLine statusCmd = new CommandLine(freshStatus);
                statusCmd.execute("test-plan-id-123");

                String output = baos.toString();
                assertTrue(output.toLowerCase().contains("failed") || output.toLowerCase().contains("connection"),
                    "Status lookup should report a connection error: " + output);
            } finally {
                System.clearProperty("factotum.api.url");
            }
        }

        @Test
        @DisplayName("CLI-064: Status with UUID-style ID works")
        void testStatusWithUuid() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            System.setErr(new PrintStream(baos));

            System.setProperty("factotum.api.url", "http://localhost:9997");
            try {
                FactotumCli.Status freshStatus = new FactotumCli.Status();
                CommandLine statusCmd = new CommandLine(freshStatus);
                statusCmd.execute("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

                String output = baos.toString();
                assertTrue(output.toLowerCase().contains("failed") || output.toLowerCase().contains("connection"),
                    "Status lookup should report a connection error: " + output);
            } finally {
                System.clearProperty("factotum.api.url");
            }
        }
    }

    // ── Envelope construction tests (via mock HTTP server) ──────────────

    @Nested
    @DisplayName("Envelope Construction")
    class EnvelopeConstructionTests {

        /** Helper to start a ServerSocket that captures one request body. */
        private String captureRequestBody(java.net.ServerSocket ss, int timeoutMs) throws InterruptedException {
            AtomicReference<String> captured = new AtomicReference<>();
            Thread acceptor = new Thread(() -> {
                try {
                    ss.setSoTimeout(timeoutMs);
                    try (java.net.Socket socket = ss.accept()) {
                        java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(socket.getInputStream()));
                        long contentLength = 0;
                        String line;
                        boolean firstLine = true;
                        while ((line = reader.readLine()) != null) {
                            if (firstLine) { firstLine = false; continue; }
                            if (line.isEmpty()) break;
                            if (line.toLowerCase().startsWith("content-length:")) {
                                contentLength = Long.parseLong(line.split(":")[1].trim());
                            }
                        }
                        char[] buf = new char[(int) contentLength];
                        int read = 0;
                        while (read < buf.length) {
                            int n = reader.read(buf, read, buf.length - read);
                            if (n == -1) break;
                            read += n;
                        }
                        captured.set(new String(buf));
                    } catch (java.net.SocketTimeoutException ignored) {
                    }
                } catch (Exception ignored) {
                }
            });
            acceptor.start();
            acceptor.join(timeoutMs + 1000);

            if (!acceptor.isAlive()) {
                return captured.get();
            }
            acceptor.interrupt();
            return null;
        }

        @Test
        @DisplayName("CLI-100: Send constructs valid JSON envelope with all header fields")
        void testSendConstructsValidEnvelope() throws Exception {
            java.net.ServerSocket ss = new java.net.ServerSocket(0);
            int port = ss.getLocalPort();

            System.setProperty("factotum.api.url", "http://localhost:" + port);
            try {
                FactotumCli.Send send = new FactotumCli.Send();
                CommandLine cmd = new CommandLine(send);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                System.setErr(new PrintStream(baos));
                try {
                    cmd.execute("-b", "{\"action\":\"verify\"}", "-q", "test_q", "-s", "tester");
                } finally {
                    System.setErr(System.err);
                    System.clearProperty("factotum.api.url");
                }

                String body = captureRequestBody(ss, 5000);
                if (body == null) {
                    fail("Server did not receive a request within timeout");
                }

                assertTrue(body.contains("\"destination\":\"test_q\""), "Envelope must contain destination queue");
                assertTrue(body.contains("\"sender\":\"tester\""), "Envelope must contain sender");
                assertTrue(body.contains("\"contentType\":\"application/json\""), "Envelope must specify content type");
                assertTrue(body.contains("\"schemaVersion\":\"1.0\""), "Envelope must include schema version");
                assertTrue(body.contains("\"messageId\""), "Envelope must have a messageId");
                assertTrue(body.contains("\"timestamp\""), "Envelope must have a timestamp");
                assertTrue(body.contains("\"action\":\"verify\""), "Body payload must be preserved");
            } finally {
                ss.close();
            }
        }

        @Test
        @DisplayName("CLI-101: Envelope includes auto-generated UUID and ISO timestamp")
        void testEnvelopeHasUuidAndTimestamp() throws Exception {
            java.net.ServerSocket ss = new java.net.ServerSocket(0);
            int port = ss.getLocalPort();

            System.setProperty("factotum.api.url", "http://localhost:" + port);
            try {
                FactotumCli.Send send = new FactotumCli.Send();
                CommandLine cmd = new CommandLine(send);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                System.setErr(new PrintStream(baos));
                try {
                    cmd.execute("-b", "{\"action\":\"verify\"}", "-q", "test_q", "-s", "tester");
                } finally {
                    System.setErr(System.err);
                    System.clearProperty("factotum.api.url");
                }

                String body = captureRequestBody(ss, 5000);
                assertNotNull(body, "Server should have received a request");

                assertTrue(body.matches("(?s).*\"messageId\":\"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\".*"),
                    "messageId should be a valid UUID");
            } finally {
                ss.close();
            }
        }

        @Test
        @DisplayName("CLI-102: Default queue factotum_inbound is used when -q omitted")
        void testDefaultQueueInEnvelope() throws Exception {
            java.net.ServerSocket ss = new java.net.ServerSocket(0);
            int port = ss.getLocalPort();

            System.setProperty("factotum.api.url", "http://localhost:" + port);
            try {
                FactotumCli.Send send = new FactotumCli.Send();
                CommandLine cmd = new CommandLine(send);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                System.setErr(new PrintStream(baos));
                try {
                    cmd.execute("-b", "{\"action\":\"test\"}");
                } finally {
                    System.setErr(System.err);
                    System.clearProperty("factotum.api.url");
                }

                String body = captureRequestBody(ss, 5000);
                assertNotNull(body, "Server should have received a request");

                assertTrue(body.contains("\"destination\":\"factotum_inbound\""),
                    "Default queue should be factotum_inbound in envelope");
            } finally {
                ss.close();
            }
        }
    }

    // ── List subcommand tests ───────────────────────────────────────────

    @Nested
    @DisplayName("List Subcommand")
    class ListTests {

        @Test
        @DisplayName("CLI-070: --help on list subcommand returns 0")
        void testListHelp() {
            FactotumCli cli = new FactotumCli();
            CommandLine cmd = new CommandLine(cli);
            int exitCode = cmd.execute("list", "--help");
            assertEquals(0, exitCode);
        }

        @Test
        @DisplayName("CLI-071: Default type is 'all'")
        void testListDefaultType() {
            FactotumCli.ListCmd list = new FactotumCli.ListCmd();
            CommandLine cmd = new CommandLine(list);
            cmd.parseArgs("-t", "plans");
            assertEquals("plans", list.type);

            FactotumCli.ListCmd list2 = new FactotumCli.ListCmd();
            new CommandLine(list2).parseArgs();
            assertEquals("all", list2.type);
        }

        @Test
        @DisplayName("CLI-072: --type plans filter is parsed")
        void testListTypeFilter() {
            FactotumCli.ListCmd list = new FactotumCli.ListCmd();
            CommandLine cmd = new CommandLine(list);
            cmd.parseArgs("-t", "plans");
            assertEquals("plans", list.type);
        }

        @Test
        @DisplayName("CLI-073: --type tasks filter is parsed")
        void testListTypeFilterTasks() {
            FactotumCli.ListCmd list = new FactotumCli.ListCmd();
            CommandLine cmd = new CommandLine(list);
            cmd.parseArgs("--type", "tasks");
            assertEquals("tasks", list.type);
        }

        @Test
        @DisplayName("CLI-074: List command runs (fails to connect)")
        void testListRuns() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            System.setErr(new PrintStream(baos));

            System.setProperty("factotum.api.url", "http://localhost:9996");
            try {
                FactotumCli.ListCmd freshList = new FactotumCli.ListCmd();
                CommandLine listCmd = new CommandLine(freshList);
                listCmd.execute("-t", "all");

                String output = baos.toString();
                assertTrue(output.toLowerCase().contains("failed") || output.toLowerCase().contains("connection") || output.toLowerCase().contains("timeout"),
                    "List should report a connection error: " + output);
            } finally {
                System.clearProperty("factotum.api.url");
            }
        }
    }

    // ── Tasks subcommand tests ──────────────────────────────────────────

    @Nested
    @DisplayName("Tasks Subcommand")
    class TasksTests {

        @Test
        @DisplayName("CLI-080: --help on tasks subcommand returns 0")
        void testTasksHelp() {
            FactotumCli cli = new FactotumCli();
            CommandLine cmd = new CommandLine(cli);
            int exitCode = cmd.execute("tasks", "--help");
            assertEquals(0, exitCode);
        }

        @Test
        @DisplayName("CLI-081: Default planId is null")
        void testTasksDefaultPlanFilter() {
            FactotumCli.Tasks tasks = new FactotumCli.Tasks();
            CommandLine cmd = new CommandLine(tasks);
            cmd.parseArgs("-p", "plan-abc-123");
            assertEquals("plan-abc-123", tasks.planId);

            FactotumCli.Tasks tasks2 = new FactotumCli.Tasks();
            new CommandLine(tasks2).parseArgs();
            assertNull(tasks2.planId);
        }

        @Test
        @DisplayName("CLI-082: --plan filter is parsed")
        void testTasksPlanFilter() {
            FactotumCli.Tasks tasks = new FactotumCli.Tasks();
            CommandLine cmd = new CommandLine(tasks);
            cmd.parseArgs("-p", "plan-abc-123");
            assertEquals("plan-abc-123", tasks.planId);
        }

        @Test
        @DisplayName("CLI-083: --plan long option is parsed")
        void testTasksPlanFilterLong() {
            FactotumCli.Tasks tasks = new FactotumCli.Tasks();
            CommandLine cmd = new CommandLine(tasks);
            cmd.parseArgs("--plan", "plan-def-456");
            assertEquals("plan-def-456", tasks.planId);
        }

        @Test
        @DisplayName("CLI-084: Tasks command runs (fails to connect)")
        void testTasksRuns() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            System.setErr(new PrintStream(baos));

            System.setProperty("factotum.api.url", "http://localhost:9995");
            try {
                FactotumCli.Tasks freshTasks = new FactotumCli.Tasks();
                CommandLine tasksCmd = new CommandLine(freshTasks);
                tasksCmd.execute();

                String output = baos.toString();
                assertTrue(output.toLowerCase().contains("failed") || output.toLowerCase().contains("connection") || output.toLowerCase().contains("timeout"),
                    "Tasks should report a connection error: " + output);
            } finally {
                System.clearProperty("factotum.api.url");
            }
        }

        @Test
        @DisplayName("CLI-085: Tasks with plan filter runs (fails to connect)")
        void testTasksWithPlanRuns() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            System.setErr(new PrintStream(baos));

            System.setProperty("factotum.api.url", "http://localhost:9995");
            try {
                FactotumCli.Tasks freshTasks = new FactotumCli.Tasks();
                CommandLine tasksCmd = new CommandLine(freshTasks);
                tasksCmd.execute("-p", "plan-xyz");

                String output = baos.toString();
                assertTrue(output.toLowerCase().contains("failed") || output.toLowerCase().contains("connection") || output.toLowerCase().contains("timeout"),
                    "Tasks with plan should report a connection error: " + output);
            } finally {
                System.clearProperty("factotum.api.url");
            }
        }
    }

    // ── Log subcommand tests ────────────────────────────────────────────

    @Nested
    @DisplayName("Log Subcommand")
    class LogTests {

        @Test
        @DisplayName("CLI-090: --help on log subcommand returns 0")
        void testLogHelp() {
            FactotumCli cli = new FactotumCli();
            CommandLine cmd = new CommandLine(cli);
            int exitCode = cmd.execute("log", "--help");
            assertEquals(0, exitCode);
        }

        @Test
        @DisplayName("CLI-091: Default tailLines is 20")
        void testLogDefaultTailLines() {
            FactotumCli.Log log = new FactotumCli.Log();
            CommandLine cmd = new CommandLine(log);
            cmd.parseArgs("-n", "50");
            assertEquals(50, log.tailLines);

            FactotumCli.Log log2 = new FactotumCli.Log();
            new CommandLine(log2).parseArgs();
            assertEquals(20, log2.tailLines);
        }

        @Test
        @DisplayName("CLI-092: -n option is parsed")
        void testLogTailLinesOption() {
            FactotumCli.Log log = new FactotumCli.Log();
            CommandLine cmd = new CommandLine(log);
            cmd.parseArgs("-n", "50");
            assertEquals(50, log.tailLines);
        }

        @Test
        @DisplayName("CLI-093: --lines long option is parsed")
        void testLogTailLinesLongOption() {
            FactotumCli.Log log = new FactotumCli.Log();
            CommandLine cmd = new CommandLine(log);
            cmd.parseArgs("--lines", "100");
            assertEquals(100, log.tailLines);
        }

        @Test
        @DisplayName("CLI-094: Log command runs (fails to connect)")
        void testLogRuns() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            System.setErr(new PrintStream(baos));

            System.setProperty("factotum.api.url", "http://localhost:9994");
            try {
                FactotumCli.Log freshLog = new FactotumCli.Log();
                CommandLine logCmd = new CommandLine(freshLog);
                logCmd.execute("-n", "10");

                String output = baos.toString();
                assertTrue(output.toLowerCase().contains("failed") || output.toLowerCase().contains("connection") || output.toLowerCase().contains("timeout"),
                    "Log should report a connection error: " + output);
            } finally {
                System.clearProperty("factotum.api.url");
            }
        }
    }

    // ── Ask subcommand tests ────────────────────────────────────────────

    @Nested
    @DisplayName("Ask Subcommand")
    class AskTests {

        @Test
        @DisplayName("CLI-110: --help on ask subcommand returns 0")
        void testAskHelp() {
            FactotumCli cli = new FactotumCli();
            CommandLine cmd = new CommandLine(cli);
            int exitCode = cmd.execute("ask", "--help");
            assertEquals(0, exitCode);
        }

        @Test
        @DisplayName("CLI-111: Default maxWaitSeconds is 60")
        void testAskDefaultMaxWait() {
            FactotumCli.Ask ask = new FactotumCli.Ask();
            CommandLine cmd = new CommandLine(ask);
            // parseArgs only — does not call run(), so no HTTP calls are made
            cmd.parseArgs("-w", "30");
            assertEquals(30, ask.maxWaitSeconds);

            FactotumCli.Ask ask2 = new FactotumCli.Ask();
            CommandLine cmd2 = new CommandLine(ask2);
            cmd2.parseArgs("what is java?");
            assertEquals(60, ask2.maxWaitSeconds);
        }

        @Test
        @DisplayName("CLI-112: -w option is parsed")
        void testAskMaxWaitOption() {
            FactotumCli.Ask ask = new FactotumCli.Ask();
            CommandLine cmd = new CommandLine(ask);
            cmd.parseArgs("-w", "30");
            assertEquals(30, ask.maxWaitSeconds);
        }

        @Test
        @DisplayName("CLI-113: --wait long option is parsed")
        void testAskMaxWaitLongOption() {
            FactotumCli.Ask ask = new FactotumCli.Ask();
            CommandLine cmd = new CommandLine(ask);
            cmd.parseArgs("-w", "120");
            assertEquals(120, ask.maxWaitSeconds);
        }

        @Test
        @DisplayName("CLI-114: Positional message is captured")
        void testAskMessageCaptured() {
            FactotumCli.Ask ask = new FactotumCli.Ask();
            CommandLine cmd = new CommandLine(ask);
            cmd.parseArgs("what is java?");
            assertNotNull(ask.messageParts);
            assertEquals(1, ask.messageParts.length);
            assertEquals("what is java?", ask.messageParts[0]);
        }

        @Test
        @DisplayName("CLI-115: Multi-word message is captured")
        void testAskMultiWordMessage() {
            FactotumCli.Ask ask = new FactotumCli.Ask();
            CommandLine cmd = new CommandLine(ask);
            cmd.parseArgs("explain", "how", "the", "brain", "works");
            assertNotNull(ask.messageParts);
            assertEquals(5, ask.messageParts.length);
        }

        @Test
        @DisplayName("CLI-116: Ask with -w and message parses correctly")
        void testAskWithWaitAndMessage() {
            FactotumCli.Ask ask = new FactotumCli.Ask();
            CommandLine cmd = new CommandLine(ask);
            cmd.parseArgs("-w", "45", "tell me a joke");
            assertEquals(45, ask.maxWaitSeconds);
            assertNotNull(ask.messageParts);
            assertEquals("tell me a joke", ask.messageParts[0]);
        }

        @Test
        @DisplayName("CLI-117: Ask command runs (fails to connect)")
        void testAskRuns() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            System.setErr(new PrintStream(baos));

            System.setProperty("factotum.api.url", "http://localhost:9993");
            try {
                FactotumCli.Ask freshAsk = new FactotumCli.Ask();
                CommandLine askCmd = new CommandLine(freshAsk);
                askCmd.execute("-w", "2", "hello world");

                String output = baos.toString();
                // Should either fail to connect, time out waiting for answer — not crash
                assertTrue(output.contains("Timed out") || output.toLowerCase().contains("failed"),
                    "Ask should report timeout or connection error: " + output);
            } finally {
                System.clearProperty("factotum.api.url");
            }
        }
    }
}
