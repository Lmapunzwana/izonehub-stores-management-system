create table stock_count (
    id uuid primary key,
    created_at timestamptz not null,
    store_id uuid not null references store(id),
    initiated_by_id uuid not null references app_users(id),
    status varchar(80) not null
);

create table stock_count_line (
    id uuid primary key,
    created_at timestamptz not null,
    stock_count_id uuid not null references stock_count(id),
    item_id uuid not null references item(id),
    system_quantity_snapshot numeric(19,4) not null,
    physical_quantity numeric(19,4),
    variance_quantity numeric(19,4),
    status varchar(80) not null
);
