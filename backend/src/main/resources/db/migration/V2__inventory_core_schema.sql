alter table store_inventory add column quantity_damaged numeric(19,4) not null default 0;

create table notification (
    id uuid primary key,
    created_at timestamptz not null,
    user_id uuid not null references app_users(id),
    type varchar(80) not null,
    message varchar(1000) not null,
    read boolean not null
);

create table expected_receipt (
    id uuid primary key,
    created_at timestamptz not null,
    store_id uuid not null references store(id),
    supplier_name varchar(255) not null,
    expected_date date not null,
    status varchar(80) not null,
    created_by_id uuid not null references app_users(id)
);

create table expected_receipt_line (
    id uuid primary key,
    created_at timestamptz not null,
    expected_receipt_id uuid not null references expected_receipt(id),
    item_id uuid not null references item(id),
    expected_quantity numeric(19,4) not null,
    received_quantity numeric(19,4) not null,
    condition varchar(80) not null
);

create table goods_received_note (
    id uuid primary key,
    created_at timestamptz not null,
    reference_number varchar(255) not null unique,
    expected_receipt_id uuid not null references expected_receipt(id),
    store_id uuid not null references store(id),
    received_by_id uuid not null references app_users(id),
    received_at timestamptz not null,
    status varchar(80) not null
);
