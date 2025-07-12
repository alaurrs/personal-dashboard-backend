ALTER TABLE user_documents
    ADD CONSTRAINT unique_user_summary_type_content
        UNIQUE (user_id, summary_type, content);