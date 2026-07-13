create table low_stock_threshold (
    id         uuid           primary key,
    created_at timestamptz    not null,
    store_id   uuid           not null references store(id),
    item_id    uuid           not null references item(id),
    threshold  numeric(19,4)  not null,
    unique(store_id, item_id)
);
