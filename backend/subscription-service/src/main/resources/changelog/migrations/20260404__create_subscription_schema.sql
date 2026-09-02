-- liquibase formatted sql
-- changeset mattew:20260404__create_subscription_schema

CREATE SCHEMA IF NOT EXISTS subscription;
-- rollback DROP SCHEMA IF EXISTS subscription CASCADE;
