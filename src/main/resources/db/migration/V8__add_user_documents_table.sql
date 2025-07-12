create table user_documents (
                                id uuid primary key default gen_random_uuid(),
                                user_id uuid references users(id) on delete cascade,
                                source text,
                                content text,
                                embedding vector(1536), -- taille pour OpenAI ada-002
                                metadata jsonb,
                                created_at timestamp default now()
);
