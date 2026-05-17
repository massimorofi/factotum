-- Enable PGMQ extension for message queue support
CREATE EXTENSION IF NOT EXISTS pgmq;

-- Pre-create queues so pgmq.read() doesn't fail on startup
SELECT pgmq.create('factotum_inbound');

-- Workflow plan steps tracking table
CREATE TABLE IF NOT EXISTS plan_steps (
    id             VARCHAR(64) PRIMARY KEY,
    plan_id        UUID          NOT NULL,
    status         VARCHAR(32)   NOT NULL DEFAULT 'pending',
    started_at     TIMESTAMP,
    completed_at   TIMESTAMP
);

-- Create a schema alias so DurableWorkflowEngine's qualified name works
CREATE SCHEMA IF NOT EXISTS factotum;
ALTER TABLE plan_steps SET SCHEMA factotum;
