package com.factotum.brain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Discovers and parses Agent Skills (SKILL.md files) from the classpath.
 * Follows the agentskills.io specification: YAML frontmatter + Markdown body.
 */
@ApplicationScoped
public class SkillLoader {

    private static final Logger log = Logger.getLogger(SkillLoader.class);
    private static final String SKILLS_DIR = "skills/";

    @Inject
    ObjectMapper objectMapper;

    /**
     * Loads all skills from the classpath skills/ directory.
     */
    public List<Skill> loadSkills() {
        try {
            var skillDirs = listSkillDirectories();
            List<Skill> skills = new ArrayList<>();
            for (String dir : skillDirs) {
                Skill skill = loadSingleSkill(dir);
                if (skill != null) {
                    skills.add(skill);
                }
            }
            log.infof("Loaded %d agent skill(s)", skills.size());
            return Collections.unmodifiableList(skills);
        } catch (Exception e) {
            log.warnf(e, "Failed to load agent skills from classpath");
            return List.of();
        }
    }

    /**
     * Returns the full instructions string for all loaded skills,
     * suitable for embedding in an LLM agent's system prompt.
     */
    public String buildSkillInstructions() {
        var skills = loadSkills();
        if (skills.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Available Agent Skills\n\n");
        for (var skill : skills) {
            sb.append("### Skill: ").append(skill.name()).append("\n\n");
            sb.append(skill.instructions()).append("\n\n---\n\n");
        }
        return sb.toString();
    }

    private List<String> listSkillDirectories() throws IOException {
        var entries = Thread.currentThread().getContextClassLoader()
            .getResource(SKILLS_DIR).openConnection();
        if (!(entries instanceof java.net.JarURLConnection jarConn)) {
            // Running from exploded classpath (dev/test)
            String path = SKILLS_DIR.endsWith("/") ? SKILLS_DIR : SKILLS_DIR + "/";
            try {
                var baseDir = new java.io.File(Thread.currentThread().getContextClassLoader()
                    .getResource(path).toURI());
                if (!baseDir.isDirectory()) return List.of();
                String[] children = baseDir.list((dir, name) -> {
                    java.io.File f = new java.io.File(dir, name);
                    return f.isDirectory() && new java.io.File(f, "SKILL.md").exists();
                });
                return children != null ? List.of(children) : List.of();
            } catch (java.net.URISyntaxException e) {
                log.warnf(e, "Cannot resolve skill directory from classpath");
                return List.of();
            }
        }

        try (var is = jarConn.getInputStream();
             var zip = new java.util.zip.ZipInputStream(is)) {
            List<String> dirs = new ArrayList<>();
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith(SKILLS_DIR) && name.endsWith("/SKILL.md")) {
                    String dirName = name.substring(SKILLS_DIR.length(), name.length() - "SKILL.md".length());
                    dirs.add(dirName);
                }
            }
            return dirs;
        }
    }

    private Skill loadSingleSkill(String skillDir) {
        String skillMdPath = SKILLS_DIR + skillDir + "SKILL.md";
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(skillMdPath)) {
            if (is == null) return null;

            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return parseSkill(skillDir, content);
        } catch (Exception e) {
            log.warnf(e, "Failed to load skill from %s", skillMdPath);
            return null;
        }
    }

    private Skill parseSkill(String name, String content) throws Exception {
        // Split frontmatter (--- delimited YAML) from body
        int firstDelim = content.indexOf("---");
        if (firstDelim < 0 || firstDelim > 3) {
            log.warnf("No valid frontmatter found in skill %s", name);
            return new Skill(name, "", content.trim(), null, null, Map.of());
        }

        String yamlBlock = content.substring(firstDelim + 3, content.indexOf("---", firstDelim + 3)).trim();
        String body = content.substring(content.indexOf("---", firstDelim + 3) + 3).trim();

        // Parse YAML frontmatter using Jackson (already on classpath via quarkus-jackson)
        Map<String, Object> frontmatter = objectMapper.readValue(
            yamlBlock, new TypeReference<>() {});

        String description = asString(frontmatter.get("description"));
        String license = asString(frontmatter.get("license"));
        String compatibility = asString(frontmatter.get("compatibility"));

        @SuppressWarnings("unchecked")
        Map<String, Object> rawMeta = (Map<String, Object>) frontmatter.get("metadata");
        Map<String, String> metadata = Map.of();
        if (rawMeta != null) {
            metadata = rawMeta.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey, e -> String.valueOf(e.getValue())));
        }

        return new Skill(name, description, body, license, compatibility, metadata);
    }

    private String asString(Object obj) {
        if (obj == null) return null;
        return obj.toString();
    }
}
