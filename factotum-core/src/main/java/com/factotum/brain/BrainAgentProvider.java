package com.factotum.brain;

import com.google.adk.agents.LlmAgent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.annotation.PostConstruct;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Creates and configures the Brain LLM agent at startup.
 * Composes system instructions from a base template plus all loaded Agent Skills.
 */
@ApplicationScoped
public class BrainAgentProvider {

    private static final Logger log = Logger.getLogger(BrainAgentProvider.class);

    @ConfigProperty(name = "factotum.llm.brain-model") String brainModel;

    private LlmAgent brainAgent;

    @jakarta.inject.Inject
    SkillLoader skillLoader;

    /** Initializes the Brain agent with base instructions and loaded skill definitions. */
    @PostConstruct
    public void setup() {
        String baseInstruction = """
            You are the Brain of Factotum, an AI orchestration system.
            Analyze the incoming JSON event, check existing plans, and issue precise step actions.""";

        String skillInstructions = skillLoader.buildSkillInstructions();
        if (!skillInstructions.isEmpty()) {
            log.infof("Composing brain instruction with %d loaded skill(s)",
                skillLoader.loadSkills().size());
        } else {
            log.warn("No agent skills found in classpath — brain will use default instructions");
        }

        this.brainAgent = LlmAgent.builder()
            .name("TheBrain")
            .model(brainModel)
            .instruction(baseInstruction + "\n\n" + skillInstructions)
            .build();
    }

    public LlmAgent getBrain() {
        return this.brainAgent;
    }
}
