create table material_issue_voucher (
    id uuid primary key,
    created_at timestamptz not null,
    reference_number varchar(255) not null unique,
    store_id uuid not null references store(id),
    project_code varchar(255) not null,
    issued_by_id uuid not null references app_users(id),
    issued_at timestamptz not null,
    status varchar(80) not null
);

create table miv_line (
    id uuid primary key,
    created_at timestamptz not null,
    miv_id uuid not null references material_issue_voucher(id),
    item_id uuid not null references item(id),
    issued_quantity numeric(19,4) not null,
    returned_quantity numeric(19,4) not null
);

create table stock_return (
    id uuid primary key,
    created_at timestamptz not null,
    miv_id uuid not null references material_issue_voucher(id),
    store_id uuid not null references store(id),
    returned_by_id uuid not null references app_users(id),
    returned_at timestamptz not null
);

create table stock_return_line (
    id uuid primary key,
    created_at timestamptz not null,
    stock_return_id uuid not null references stock_return(id),
    item_id uuid not null references item(id),
    quantity numeric(19,4) not null,
    condition varchar(80) not null
);

create table stock_adjustment (
    id uuid primary key,
    created_at timestamptz not null,
    reference_number varchar(255) not null unique,
    store_id uuid not null references store(id),
    item_id uuid not null references item(id),
    adjusted_by_id uuid not null references app_users(id),
    reason_code varchar(80) not null,
    quantity_before numeric(19,4) not null,
    quantity_after numeric(19,4) not null,
    notes varchar(2000),
    requires_countersignature boolean not null,
    countersigned_by_id uuid references app_users(id)
);
