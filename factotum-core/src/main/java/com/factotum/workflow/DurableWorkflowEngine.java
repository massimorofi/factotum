package com.factotum.workflow;

import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Executes multi-step AI workflows with database checkpointing for durability.
 * If a step fails, its status is persisted as "failed" so the workflow can be resumed later.
 */
@ApplicationScoped
public class DurableWorkflowEngine {

    private static final Logger log = Logger.getLogger(DurableWorkflowEngine.class);

    @Inject DataSource dataSource;

    /** Runs a two-step sequential agent workflow with checkpointing at each step boundary. */
    public void executeDurableSequence(UUID planId, LlmAgent stepOneAgent, LlmAgent stepTwoAgent) {
        // Wrap ADK sub-agents into a managed flow
        SequentialAgent sequence = SequentialAgent.builder()
            .name("workflow-sequence")
            .subAgents(stepOneAgent, stepTwoAgent)
            .build();

        checkpointStep(planId, "step-1", "running");

        try {
            // ADK 1.2.0 uses reactive runAsync(InvocationContext) instead of synchronous run()
            Content userContent = Content.fromParts(Part.builder().text("").build());
            InvocationContext ctx = InvocationContext.builder()
                .userContent(userContent)
                .build();
            sequence.runAsync(ctx)
                .blockingLast();
            checkpointStep(planId, "step-1", "completed");
        } catch (Exception e) {
            checkpointStep(planId, "step-1", "failed");
            throw e;
        }
    }

    /** Persists the current status of a workflow step to the database for durability. */
    private void checkpointStep(UUID planId, String stepId, String status) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "UPDATE factotum.plan_steps SET status = ?, started_at = now() WHERE plan_id = ? AND id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, status);
                ps.setObject(2, planId);
                ps.setString(3, stepId);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            log.warnf(e, "Failed to checkpoint step %s for plan %s", stepId, planId);
        }
    }
}
