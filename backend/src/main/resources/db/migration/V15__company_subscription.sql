CREATE TABLE company_subscription (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    allowed_store_slots INTEGER NOT NULL DEFAULT 3
);

-- Seed an initial record for the company so there's always at least one limit
INSERT INTO company_subscription (id, created_at, allowed_store_slots) 
VALUES (gen_random_uuid(), NOW(), 3);
