-- liquibase formatted sql
-- Quartz 2.3.2 JDBC JobStore tables for clustered scheduling
-- Extracted from org/quartz/impl/jdbcjobstore/tables_postgres.sql in quartz-2.3.2.jar
-- All tables placed in data_flow schema to match tablePrefix = data_flow.qrtz_

--changeset parser-service:20260418-quartz-001
--comment: Quartz JDBC JobStore - qrtz_job_details
CREATE TABLE data_flow.qrtz_job_details
(
    sched_name        VARCHAR(120) NOT NULL,
    job_name          VARCHAR(200) NOT NULL,
    job_group         VARCHAR(200) NOT NULL,
    description       VARCHAR(250) NULL,
    job_class_name    VARCHAR(250) NOT NULL,
    is_durable        BOOL         NOT NULL,
    is_nonconcurrent  BOOL         NOT NULL,
    is_update_data    BOOL         NOT NULL,
    requests_recovery BOOL         NOT NULL,
    job_data          BYTEA        NULL,
    PRIMARY KEY (sched_name, job_name, job_group)
);

--changeset parser-service:20260418-quartz-002
--comment: Quartz JDBC JobStore - qrtz_triggers
CREATE TABLE data_flow.qrtz_triggers
(
    sched_name     VARCHAR(120) NOT NULL,
    trigger_name   VARCHAR(200) NOT NULL,
    trigger_group  VARCHAR(200) NOT NULL,
    job_name       VARCHAR(200) NOT NULL,
    job_group      VARCHAR(200) NOT NULL,
    description    VARCHAR(250) NULL,
    next_fire_time BIGINT       NULL,
    prev_fire_time BIGINT       NULL,
    priority       INTEGER      NULL,
    trigger_state  VARCHAR(16)  NOT NULL,
    trigger_type   VARCHAR(8)   NOT NULL,
    start_time     BIGINT       NOT NULL,
    end_time       BIGINT       NULL,
    calendar_name  VARCHAR(200) NULL,
    misfire_instr  SMALLINT     NULL,
    job_data       BYTEA        NULL,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, job_name, job_group)
        REFERENCES data_flow.qrtz_job_details (sched_name, job_name, job_group)
);

--changeset parser-service:20260418-quartz-003
--comment: Quartz JDBC JobStore - qrtz_simple_triggers
CREATE TABLE data_flow.qrtz_simple_triggers
(
    sched_name      VARCHAR(120) NOT NULL,
    trigger_name    VARCHAR(200) NOT NULL,
    trigger_group   VARCHAR(200) NOT NULL,
    repeat_count    BIGINT       NOT NULL,
    repeat_interval BIGINT       NOT NULL,
    times_triggered BIGINT       NOT NULL,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
        REFERENCES data_flow.qrtz_triggers (sched_name, trigger_name, trigger_group)
);

--changeset parser-service:20260418-quartz-004
--comment: Quartz JDBC JobStore - qrtz_cron_triggers
CREATE TABLE data_flow.qrtz_cron_triggers
(
    sched_name      VARCHAR(120) NOT NULL,
    trigger_name    VARCHAR(200) NOT NULL,
    trigger_group   VARCHAR(200) NOT NULL,
    cron_expression VARCHAR(120) NOT NULL,
    time_zone_id    VARCHAR(80),
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
        REFERENCES data_flow.qrtz_triggers (sched_name, trigger_name, trigger_group)
);

--changeset parser-service:20260418-quartz-005
--comment: Quartz JDBC JobStore - qrtz_simprop_triggers
CREATE TABLE data_flow.qrtz_simprop_triggers
(
    sched_name    VARCHAR(120)   NOT NULL,
    trigger_name  VARCHAR(200)   NOT NULL,
    trigger_group VARCHAR(200)   NOT NULL,
    str_prop_1    VARCHAR(512)   NULL,
    str_prop_2    VARCHAR(512)   NULL,
    str_prop_3    VARCHAR(512)   NULL,
    int_prop_1    INT            NULL,
    int_prop_2    INT            NULL,
    long_prop_1   BIGINT         NULL,
    long_prop_2   BIGINT         NULL,
    dec_prop_1    NUMERIC(13, 4) NULL,
    dec_prop_2    NUMERIC(13, 4) NULL,
    bool_prop_1   BOOL           NULL,
    bool_prop_2   BOOL           NULL,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
        REFERENCES data_flow.qrtz_triggers (sched_name, trigger_name, trigger_group)
);

--changeset parser-service:20260418-quartz-006
--comment: Quartz JDBC JobStore - qrtz_blob_triggers
CREATE TABLE data_flow.qrtz_blob_triggers
(
    sched_name    VARCHAR(120) NOT NULL,
    trigger_name  VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    blob_data     BYTEA        NULL,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
        REFERENCES data_flow.qrtz_triggers (sched_name, trigger_name, trigger_group)
);

--changeset parser-service:20260418-quartz-007
--comment: Quartz JDBC JobStore - qrtz_calendars
CREATE TABLE data_flow.qrtz_calendars
(
    sched_name    VARCHAR(120) NOT NULL,
    calendar_name VARCHAR(200) NOT NULL,
    calendar      BYTEA        NOT NULL,
    PRIMARY KEY (sched_name, calendar_name)
);

--changeset parser-service:20260418-quartz-008
--comment: Quartz JDBC JobStore - qrtz_paused_trigger_grps
CREATE TABLE data_flow.qrtz_paused_trigger_grps
(
    sched_name    VARCHAR(120) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    PRIMARY KEY (sched_name, trigger_group)
);

--changeset parser-service:20260418-quartz-009
--comment: Quartz JDBC JobStore - qrtz_fired_triggers
CREATE TABLE data_flow.qrtz_fired_triggers
(
    sched_name        VARCHAR(120) NOT NULL,
    entry_id          VARCHAR(95)  NOT NULL,
    trigger_name      VARCHAR(200) NOT NULL,
    trigger_group     VARCHAR(200) NOT NULL,
    instance_name     VARCHAR(200) NOT NULL,
    fired_time        BIGINT       NOT NULL,
    sched_time        BIGINT       NOT NULL,
    priority          INTEGER      NOT NULL,
    state             VARCHAR(16)  NOT NULL,
    job_name          VARCHAR(200) NULL,
    job_group         VARCHAR(200) NULL,
    is_nonconcurrent  BOOL         NULL,
    requests_recovery BOOL         NULL,
    PRIMARY KEY (sched_name, entry_id)
);

--changeset parser-service:20260418-quartz-010
--comment: Quartz JDBC JobStore - qrtz_scheduler_state
CREATE TABLE data_flow.qrtz_scheduler_state
(
    sched_name        VARCHAR(120) NOT NULL,
    instance_name     VARCHAR(200) NOT NULL,
    last_checkin_time BIGINT       NOT NULL,
    checkin_interval  BIGINT       NOT NULL,
    PRIMARY KEY (sched_name, instance_name)
);

--changeset parser-service:20260418-quartz-011
--comment: Quartz JDBC JobStore - qrtz_locks
CREATE TABLE data_flow.qrtz_locks
(
    sched_name VARCHAR(120) NOT NULL,
    lock_name  VARCHAR(40)  NOT NULL,
    PRIMARY KEY (sched_name, lock_name)
);

--changeset parser-service:20260418-quartz-012
--comment: Quartz JDBC JobStore - all indexes
CREATE INDEX idx_qrtz_j_req_recovery
    ON data_flow.qrtz_job_details (sched_name, requests_recovery);
CREATE INDEX idx_qrtz_j_grp
    ON data_flow.qrtz_job_details (sched_name, job_group);

CREATE INDEX idx_qrtz_t_j
    ON data_flow.qrtz_triggers (sched_name, job_name, job_group);
CREATE INDEX idx_qrtz_t_jg
    ON data_flow.qrtz_triggers (sched_name, job_group);
CREATE INDEX idx_qrtz_t_c
    ON data_flow.qrtz_triggers (sched_name, calendar_name);
CREATE INDEX idx_qrtz_t_g
    ON data_flow.qrtz_triggers (sched_name, trigger_group);
CREATE INDEX idx_qrtz_t_state
    ON data_flow.qrtz_triggers (sched_name, trigger_state);
CREATE INDEX idx_qrtz_t_n_state
    ON data_flow.qrtz_triggers (sched_name, trigger_name, trigger_group, trigger_state);
CREATE INDEX idx_qrtz_t_n_g_state
    ON data_flow.qrtz_triggers (sched_name, trigger_group, trigger_state);
CREATE INDEX idx_qrtz_t_next_fire_time
    ON data_flow.qrtz_triggers (sched_name, next_fire_time);
CREATE INDEX idx_qrtz_t_nft_st
    ON data_flow.qrtz_triggers (sched_name, trigger_state, next_fire_time);
CREATE INDEX idx_qrtz_t_nft_misfire
    ON data_flow.qrtz_triggers (sched_name, misfire_instr, next_fire_time);
CREATE INDEX idx_qrtz_t_nft_st_misfire
    ON data_flow.qrtz_triggers (sched_name, misfire_instr, next_fire_time, trigger_state);
CREATE INDEX idx_qrtz_t_nft_st_misfire_grp
    ON data_flow.qrtz_triggers (sched_name, misfire_instr, next_fire_time, trigger_group, trigger_state);

CREATE INDEX idx_qrtz_ft_trig_inst_name
    ON data_flow.qrtz_fired_triggers (sched_name, instance_name);
CREATE INDEX idx_qrtz_ft_inst_job_req_rcvry
    ON data_flow.qrtz_fired_triggers (sched_name, instance_name, requests_recovery);
CREATE INDEX idx_qrtz_ft_j_g
    ON data_flow.qrtz_fired_triggers (sched_name, job_name, job_group);
CREATE INDEX idx_qrtz_ft_jg
    ON data_flow.qrtz_fired_triggers (sched_name, job_group);
CREATE INDEX idx_qrtz_ft_t_g
    ON data_flow.qrtz_fired_triggers (sched_name, trigger_name, trigger_group);
CREATE INDEX idx_qrtz_ft_tg
    ON data_flow.qrtz_fired_triggers (sched_name, trigger_group);
