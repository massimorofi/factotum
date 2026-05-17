package com.factotum.executor;

import com.factotum.skills.HttpSkillTools;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.FunctionTool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Factory that creates LLM executor agents for individual tasks.
 * Each executor is configured with the task's instruction and available tools (e.g., HTTP GET).
 */
@ApplicationScoped
public class TaskExecutorFactory {

    @Inject HttpSkillTools httpTools;

    /** Creates a new TaskExecutor agent bound to the given instruction context. */
    public LlmAgent createExecutorAgent(String instructionContext) {
        return LlmAgent.builder()
            .name("TaskExecutor")
            .instruction(instructionContext)
            .tools(FunctionTool.create(httpTools, "executeGet"))
            .build();
    }
}
