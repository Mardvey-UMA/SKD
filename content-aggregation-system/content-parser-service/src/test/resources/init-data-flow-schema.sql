-- Create the data_flow schema before Liquibase runs migrations.
-- This is needed because the first parser migration creates parser_tasks without
-- a schema prefix (relying on currentSchema=data_flow in the JDBC URL), but
-- Liquibase itself runs as user postgres against the plain JDBC URL which
-- may not have currentSchema set. Newer migrations use explicit data_flow. prefix.
CREATE SCHEMA IF NOT EXISTS data_flow;
