CREATE TABLE IF NOT EXISTS haven_virtual_inventories (
    id         TEXT    NOT NULL PRIMARY KEY,
    owner_uuid TEXT    NOT NULL,
    name       TEXT    NOT NULL,
    rows       INTEGER NOT NULL DEFAULT 3,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_vinv_owner ON haven_virtual_inventories(owner_uuid);

CREATE TABLE IF NOT EXISTS haven_virtual_inventory_items (
    inventory_id TEXT    NOT NULL,
    slot         INTEGER NOT NULL,
    item_data    BLOB    NOT NULL,
    PRIMARY KEY (inventory_id, slot),
    FOREIGN KEY (inventory_id) REFERENCES haven_virtual_inventories(id) ON DELETE CASCADE
);
