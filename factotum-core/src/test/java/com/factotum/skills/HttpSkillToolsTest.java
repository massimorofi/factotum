package com.factotum.skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpSkillToolsTest {

    @Test
    void testHttpSkillToolsInstantiation() {
        HttpSkillTools tools = new HttpSkillTools();
        assertNotNull(tools);
    }

    @Test
    void testExecuteGetWithInvalidUrl() {
        HttpSkillTools tools = new HttpSkillTools();
        // Should return an error string, not throw
        String result = tools.executeGet("not-a-valid-url");
        assertTrue(result.startsWith("Execution error:"));
    }

    @Test
    void testExecuteGetWithNullUrl() {
        HttpSkillTools tools = new HttpSkillTools();
        // Should handle null gracefully
        assertDoesNotThrow(() -> tools.executeGet(null));
    }
}
