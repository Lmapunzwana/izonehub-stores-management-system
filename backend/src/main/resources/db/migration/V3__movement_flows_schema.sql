create table material_request (
    id uuid primary key,
    created_at timestamptz not null,
    requesting_store_id uuid not null references store(id),
    source_store_id uuid not null references store(id),
    project_code varchar(255) not null,
    status varchar(80) not null,
    raised_by_id uuid not null references app_users(id),
    approved_by_id uuid references app_users(id),
    rejection_reason varchar(1000),
    transfer_reason varchar(1000)
);

create table material_request_line (
    id uuid primary key,
    created_at timestamptz not null,
    material_request_id uuid not null references material_request(id),
    item_id uuid not null references item(id),
    requested_quantity numeric(19,4) not null,
    approved_quantity numeric(19,4) not null,
    dispatched_quantity numeric(19,4) not null,
    received_quantity numeric(19,4) not null
);

create table dispatch (
    id uuid primary key,
    created_at timestamptz not null,
    material_request_id uuid not null references material_request(id),
    dispatched_by_id uuid not null references app_users(id),
    collector_name varchar(255) not null,
    collector_employee_id varchar(255) not null,
    dispatched_at timestamptz not null
);

create table receipt (
    id uuid primary key,
    created_at timestamptz not null,
    material_request_id uuid not null references material_request(id),
    received_by_id uuid not null references app_users(id),
    received_at timestamptz not null,
    status varchar(80) not null
);

create table discrepancy (
    id uuid primary key,
    created_at timestamptz not null,
    receipt_id uuid not null references receipt(id),
    item_id uuid not null references item(id),
    dispatched_quantity numeric(19,4) not null,
    received_quantity numeric(19,4) not null,
    frozen_quantity numeric(19,4) not null,
    status varchar(80) not null,
    resolved_by_id uuid references app_users(id),
    resolution_notes varchar(2000),
    resolved_at timestamptz
);
