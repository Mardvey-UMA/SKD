#!/bin/bash
set -e

echo "=== Initializing content_agg_db schemas and extensions ==="
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- ML/Content Pipeline schemas
    CREATE SCHEMA IF NOT EXISTS config;
    CREATE SCHEMA IF NOT EXISTS data_flow;
    CREATE EXTENSION IF NOT EXISTS vector SCHEMA data_flow;

    -- Backend service schemas
    CREATE SCHEMA IF NOT EXISTS auth;
    CREATE SCHEMA IF NOT EXISTS users;
    CREATE SCHEMA IF NOT EXISTS interactions;
    CREATE SCHEMA IF NOT EXISTS subscription;
    CREATE SCHEMA IF NOT EXISTS feed;

    -- Grant access
    GRANT ALL ON SCHEMA config TO $POSTGRES_USER;
    GRANT ALL ON SCHEMA data_flow TO $POSTGRES_USER;
    GRANT ALL ON SCHEMA auth TO $POSTGRES_USER;
    GRANT ALL ON SCHEMA users TO $POSTGRES_USER;
    GRANT ALL ON SCHEMA interactions TO $POSTGRES_USER;
    GRANT ALL ON SCHEMA subscription TO $POSTGRES_USER;
    GRANT ALL ON SCHEMA feed TO $POSTGRES_USER;
EOSQL

echo "=== All schemas initialized successfully ==="
