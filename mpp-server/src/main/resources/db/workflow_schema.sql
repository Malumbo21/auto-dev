-- Workflow Events: 事件溯源存储
CREATE TABLE IF NOT EXISTS workflow_events (
    id TEXT PRIMARY KEY,
    workflow_id TEXT NOT NULL,
    sequence_number INTEGER NOT NULL,
    event_type TEXT NOT NULL,
    event_data TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    checkpoint_id TEXT,
    UNIQUE(workflow_id, sequence_number)
);

CREATE INDEX IF NOT EXISTS idx_workflow_events_workflow_id ON workflow_events(workflow_id);
CREATE INDEX IF NOT EXISTS idx_workflow_events_sequence ON workflow_events(workflow_id, sequence_number);
CREATE INDEX IF NOT EXISTS idx_workflow_events_timestamp ON workflow_events(timestamp);

-- Workflow Checkpoints: 检查点存储
CREATE TABLE IF NOT EXISTS workflow_checkpoints (
    id TEXT PRIMARY KEY,
    workflow_id TEXT NOT NULL,
    sequence_number INTEGER NOT NULL,
    state TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    size_bytes INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_workflow_checkpoints_workflow_id ON workflow_checkpoints(workflow_id);
CREATE INDEX IF NOT EXISTS idx_workflow_checkpoints_sequence ON workflow_checkpoints(workflow_id, sequence_number DESC);

-- Workflow Signals: 信号队列
CREATE TABLE IF NOT EXISTS workflow_signals (
    id TEXT PRIMARY KEY,
    workflow_id TEXT NOT NULL,
    signal_name TEXT NOT NULL,
    signal_data TEXT NOT NULL,
    received_at INTEGER NOT NULL,
    processed INTEGER DEFAULT 0,
    processed_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_workflow_signals_workflow_id ON workflow_signals(workflow_id, processed);

-- Workflow Metadata: 工作流元数据
CREATE TABLE IF NOT EXISTS workflow_metadata (
    workflow_id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL,
    task TEXT NOT NULL,
    status TEXT NOT NULL,
    owner_id TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    completed_at INTEGER,
    metadata TEXT,
    parent_workflow_id TEXT,
    version TEXT
);

CREATE INDEX IF NOT EXISTS idx_workflow_metadata_status ON workflow_metadata(status);
CREATE INDEX IF NOT EXISTS idx_workflow_metadata_owner ON workflow_metadata(owner_id);
CREATE INDEX IF NOT EXISTS idx_workflow_metadata_parent ON workflow_metadata(parent_workflow_id);
