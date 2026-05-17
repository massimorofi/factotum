package com.factotum.skills;

import com.google.adk.tools.Annotations.Schema;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

/**
 * HTTP tool exposed to LLM agents as a callable function.
 * Allows the Brain and TaskExecutors to make GET requests to external APIs.
 */
@ApplicationScoped
public class HttpSkillTools {

    private final HttpClient httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build();

    /** Performs an HTTP GET request and returns the response body as a string. */
    @Schema(
        name = "http_get",
        description = "Perform a lightweight HTTP GET request to an external local API endpoint"
    )
    public String executeGet(@Schema(description = "Target API URL", optional = false) String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            return "Execution error: " + e.getMessage();
        }
    }
}
