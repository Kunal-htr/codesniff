CREATE TABLE analysis_history (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    batch_id VARCHAR(255) NOT NULL,
    file_names JSONB NOT NULL,
    total_pairs INT NOT NULL,
    highest_similarity DOUBLE PRECISION NOT NULL,
    average_similarity DOUBLE PRECISION NOT NULL,
    full_result_json JSONB NOT NULL,
    is_pinned BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_analysis_history_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_analysis_history_user_id ON analysis_history(user_id);
