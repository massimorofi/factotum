package com.factotum.brain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.factotum.executor.TaskExecutorFactory;
import com.factotum.queue.model.FactotumMessage;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Orchestrates the AI decision pipeline: receives a message, asks the Brain agent for a decision,
 * and executes the resulting tasks (sequentially or in parallel).
 */
@ApplicationScoped
public class BrainService {

    private static final Logger log = Logger.getLogger(BrainService.class);

    @Inject BrainAgentProvider brainAgentProvider;
    @Inject TaskExecutorFactory taskExecutorFactory;
    @Inject ObjectMapper objectMapper;

    /**
     * Processes an incoming message through the Brain agent and executes any resulting tasks.
     * Returns true if processing succeeded, false otherwise.
     */
    public boolean processEvent(FactotumMessage msg, long msgId) {
        try {
            LlmAgent brain = brainAgentProvider.getBrain();

            // Build context from message for the Brain to reason over
            String prompt = buildPrompt(msg);

            // Get decision from the Brain agent
            BrainDecision decision = getBrainDecision(brain, prompt);

            log.infof("Brain decision: %s for message %d", decision.action(), msgId);

            switch (decision.action()) {
                case "spawn-task":
                    executeTasks(decision.tasks());
                    return true;
                case "create-plan":
                case "update-plan":
                    // Plan management logic would go here
                    log.infof("Plan action: %s", decision.action());
                    return true;
                case "discard":
                    log.infof("Message discarded by Brain: %s", decision.reasoning());
                    return true;
                default:
                    log.warnf("Unknown brain action: %s for message %d", decision.action(), msgId);
                    return false;
            }
        } catch (Exception e) {
            log.errorf(e, "Error processing event for message %d", msgId);
            return false;
        }
    }

    /** Serializes the incoming message to JSON for sending as a prompt to the Brain agent. */
    private String buildPrompt(FactotumMessage msg) {
        try {
            return objectMapper.writeValueAsString(msg);
        } catch (Exception e) {
            log.errorf(e, "Error serializing message for Brain");
            return "{}";
        }
    }

    /** Sends the prompt to the Brain agent and parses its JSON decision response. */
    private BrainDecision getBrainDecision(LlmAgent brain, String prompt) {
        // ADK 1.2.0 uses reactive runAsync(InvocationContext) instead of synchronous run(String).
        // We block on the Flowable to collect all events and concatenate their text content.
        try {
            Content userContent = Content.fromParts(Part.builder().text(prompt).build());
            InvocationContext ctx = InvocationContext.builder()
                .userContent(userContent)
                .build();

            String response = extractText(brain.runAsync(ctx));

            // Parse the response into BrainDecision using Jackson
            return objectMapper.readValue(response, BrainDecision.class);
        } catch (Exception e) {
            log.errorf(e, "Error getting decision from Brain");
            throw new RuntimeException("Brain processing failed", e);
        }
    }

    /** Executes a list of tasks: sequential ones first, then fans out parallel tasks concurrently. */
    private void executeTasks(java.util.List<TaskSpecification> tasks) {
        if (tasks == null || tasks.isEmpty()) return;

        // Separate parallel-capable tasks from sequential ones
        java.util.List<TaskSpecification> parallelTasks = new java.util.ArrayList<>();
        java.util.List<TaskSpecification> sequentialTasks = new java.util.ArrayList<>();

        for (TaskSpecification task : tasks) {
            Boolean isParallel = getBooleanParam(task.parameters(), "parallel");
            if (isParallel != null && isParallel) {
                parallelTasks.add(task);
            } else {
                sequentialTasks.add(task);
            }
        }

        // Execute sequential tasks first (they may set up state for parallel workers)
        for (TaskSpecification task : sequentialTasks) {
            executeSingleTask(task);
        }

        // Fan-out parallel tasks concurrently
        if (!parallelTasks.isEmpty()) {
            log.infof("Executing %d tasks in parallel", parallelTasks.size());
            java.util.concurrent.CompletableFuture<?>[] futures = parallelTasks.stream()
                .map(this::executeAsync)
                .toArray(java.util.concurrent.CompletableFuture<?>[]::new);
            java.util.concurrent.CompletableFuture.allOf(futures).join();
        }
    }

    /** Runs a single task synchronously, logging its start and completion. */
    private void executeSingleTask(TaskSpecification task) {
        log.infof("Task %s started", task.taskId());
        try {
            String response = runExecutor(task);
            log.infof("Task %s completed: %s", task.taskId(), truncate(response, 200));
        } catch (Exception e) {
            log.errorf(e, "Task %s failed", task.taskId());
        }
    }

    /** Runs a single task on the common ForkJoinPool. */
    private java.util.concurrent.CompletableFuture<Void> executeAsync(TaskSpecification task) {
        return java.util.concurrent.CompletableFuture.runAsync(() -> executeSingleTask(task));
    }

    /** Creates an LLM executor agent for the given instruction and runs it against the task parameters. */
    private String runExecutor(TaskSpecification task) throws Exception {
        LlmAgent executor = taskExecutorFactory.createExecutorAgent(task.instruction());
        String input = objectMapper.writeValueAsString(task.parameters());
        Content userContent = Content.fromParts(Part.builder().text(input).build());
        InvocationContext ctx = InvocationContext.builder()
            .userContent(userContent)
            .build();
        return extractText(executor.runAsync(ctx));
    }

    /** Safely extracts a Boolean value from a parameter map that may hold Strings or Booleans. */
    private Boolean getBooleanParam(Map<String, Object> params, String key) {
        if (params == null || !params.containsKey(key)) return null;
        Object val = params.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return null;
    }

    /** Truncates a string to the given maximum length, appending "..." when cut. */
    private String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }

    /** Collects all events from the reactive Flowable and concatenates their text parts into a single string. */
    private String extractText(io.reactivex.rxjava3.core.Flowable<Event> eventsFlowable) throws Exception {
        java.util.List<Event> events = eventsFlowable.toList().blockingGet();
        StringBuilder sb = new StringBuilder();
        for (Event event : events) {
            if (event.content().isPresent()) {
                Content content = event.content().get();
                java.util.List<Part> parts = content.parts().orElse(java.util.Collections.emptyList());
                for (Part part : parts) {
                    String text = part.text().orElse("");
                    if (!text.isEmpty()) {
                        sb.append(text);
                    }
                }
            }
        }
        return sb.toString();
    }
}
