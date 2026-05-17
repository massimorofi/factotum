package com.factotum.api;

import com.factotum.queue.model.FactotumMessage;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;
import java.util.UUID;

/**
 * REST API entry point for the Factotum AI pipeline.
 * Accepts inbound messages, validates authentication, and returns an acknowledgment with the assigned message ID.
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FactotumResource {

    @ConfigProperty(name = "factotum.api-key", defaultValue = "") String apiKey;

    /**
     * Accepts a new message and enqueues it for processing.
     * Validates the Bearer token before accepting the payload.
     */
    @POST
    @Path("/messages")
    public Response enqueueMessage(@HeaderParam("Authorization") String auth, FactotumMessage request) {
        if (!isAuthorized(auth)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        UUID generatedId = request.header().messageId() != null ? request.header().messageId() : UUID.randomUUID();

        return Response.status(Response.Status.ACCEPTED)
            .entity(Map.of("messageId", generatedId, "queue", request.header().destination(), "enqueued", true))
            .build();
    }

    /**
     * Public health check endpoint — no authentication required.
     */
    @GET
    @Path("/admin/health")
    public Response getHealthCheck() {
        return Response.ok(Map.of("status", "ok")).build();
    }

    private boolean isAuthorized(String auth) {
        if (auth == null || !auth.equals("Bearer " + apiKey)) {
            return false;
        }
        return true;
    }
}
