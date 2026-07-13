CREATE TABLE suppliers (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(255),
    address VARCHAR(255),
    lead_time NUMERIC,
    accuracy NUMERIC,
    rating NUMERIC,
    status VARCHAR(50)
);

CREATE TABLE batches (
    id UUID PRIMARY KEY,
    batch_no VARCHAR(255) NOT NULL,
    item_id UUID REFERENCES item(id),
    received_via VARCHAR(255),
    project VARCHAR(255),
    status VARCHAR(50),
    expiry_date DATE
);

CREATE TABLE serial_numbers (
    id UUID PRIMARY KEY,
    serial_no VARCHAR(255) NOT NULL,
    batch_id UUID REFERENCES batches(id) ON DELETE CASCADE
);
