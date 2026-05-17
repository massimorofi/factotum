package com.factotum.brain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BrainDecisionTest {

    @Test
    void testBrainDecisionWithSpawnTask() {
        UUID taskId = UUID.randomUUID();
        TaskSpecification task = new TaskSpecification(taskId, "analyze code", Map.of("target", "src"));

        BrainDecision decision = new BrainDecision("spawn-task", "Need to analyze the code", List.of(task));

        assertEquals("spawn-task", decision.action());
        assertEquals("Need to analyze the code", decision.reasoning());
        assertNotNull(decision.tasks());
        assertEquals(1, decision.tasks().size());
        assertEquals(taskId, decision.tasks().get(0).taskId());
    }

    @Test
    void testBrainDecisionWithDiscard() {
        BrainDecision decision = new BrainDecision("discard", "Irrelevant message", null);

        assertEquals("discard", decision.action());
        assertEquals("Irrelevant message", decision.reasoning());
        assertNull(decision.tasks());
    }

    @Test
    void testBrainDecisionWithCreatePlan() {
        BrainDecision decision = new BrainDecision("create-plan", "New plan required", null);

        assertEquals("create-plan", decision.action());
    }

    @Test
    void testBrainDecisionWithUpdatePlan() {
        BrainDecision decision = new BrainDecision("update-plan", "Updating existing plan", null);

        assertEquals("update-plan", decision.action());
    }

    @Test
    void testBrainDecisionMultipleTasks() {
        TaskSpecification t1 = new TaskSpecification(UUID.randomUUID(), "task 1", Map.of());
        TaskSpecification t2 = new TaskSpecification(UUID.randomUUID(), "task 2", Map.of("x", 1));
        TaskSpecification t3 = new TaskSpecification(UUID.randomUUID(), "task 3", null);

        BrainDecision decision = new BrainDecision(
            "spawn-task", "Three tasks needed", List.of(t1, t2, t3)
        );

        assertEquals(3, decision.tasks().size());
    }

    @Test
    void testBrainDecisionImmutability() {
        BrainDecision decision = new BrainDecision("discard", "reason", null);

        // Records are immutable - only accessors exist
        assertDoesNotThrow(() -> {
            String action = decision.action();
            String reasoning = decision.reasoning();
            assertNotNull(action);
            assertEquals("discard", action);
            assertEquals("reason", reasoning);
        });
    }

    @Test
    void testBrainDecisionEqualsAndHashCode() {
        BrainDecision d1 = new BrainDecision("discard", "same reason", null);
        BrainDecision d2 = new BrainDecision("discard", "same reason", null);

        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    void testBrainDecisionToString() {
        BrainDecision decision = new BrainDecision("spawn-task", "reasoning text", null);
        String str = decision.toString();
        assertTrue(str.contains("action=spawn-task"));
        assertTrue(str.contains("reasoning=reasoning text"));
    }

    @Test
    void testTaskSpecificationCreation() {
        UUID taskId = UUID.randomUUID();
        TaskSpecification spec = new TaskSpecification(taskId, "do something", Map.of("key", "value"));

        assertEquals(taskId, spec.taskId());
        assertEquals("do something", spec.instruction());
        assertEquals("value", spec.parameters().get("key"));
    }

    @Test
    void testTaskSpecificationWithNullParameters() {
        TaskSpecification spec = new TaskSpecification(UUID.randomUUID(), "no params", null);

        assertNull(spec.parameters());
    }

    @Test
    void testTaskSpecificationImmutability() {
        UUID taskId = UUID.randomUUID();
        TaskSpecification spec = new TaskSpecification(taskId, "instruction", Map.of("k", "v"));

        assertDoesNotThrow(() -> {
            assertEquals(taskId, spec.taskId());
            assertEquals("instruction", spec.instruction());
        });
    }
}
