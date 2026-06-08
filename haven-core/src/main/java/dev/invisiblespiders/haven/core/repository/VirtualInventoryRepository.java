package dev.invisiblespiders.haven.core.repository;

import dev.invisiblespiders.haven.api.model.VirtualInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import javax.sql.DataSource;
import java.io.*;
import java.sql.*;
import java.util.*;

public class VirtualInventoryRepository {

    private final DataSource ds;

    public VirtualInventoryRepository(DataSource ds) {
        this.ds = ds;
    }

    public Optional<VirtualInventory> findById(UUID id) throws SQLException {
        String sql = "SELECT id, owner_uuid, name, rows, created_at FROM haven_virtual_inventories WHERE id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                VirtualInventory inv = map(rs);
                loadItems(c, inv);
                return Optional.of(inv);
            }
        }
    }

    public List<VirtualInventory> findByOwner(UUID ownerUuid) throws SQLException {
        String sql = "SELECT id, owner_uuid, name, rows, created_at FROM haven_virtual_inventories WHERE owner_uuid = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ownerUuid.toString());
            List<VirtualInventory> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    VirtualInventory inv = map(rs);
                    loadItems(c, inv);
                    list.add(inv);
                }
            }
            return list;
        }
    }

    public void save(VirtualInventory inv) throws SQLException {
        String upsert = """
            INSERT INTO haven_virtual_inventories (id, owner_uuid, name, rows, created_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET name = excluded.name
            """;
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(upsert)) {
                    ps.setString(1, inv.getId().toString());
                    ps.setString(2, inv.getOwnerUuid().toString());
                    ps.setString(3, inv.getName());
                    ps.setInt(4, inv.getRows());
                    ps.setLong(5, inv.getCreatedAt());
                    ps.executeUpdate();
                }
                saveItems(c, inv);
                c.commit();
            } catch (SQLException | IOException e) {
                c.rollback();
                throw new SQLException("Failed to save virtual inventory " + inv.getId(), e);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public void delete(UUID id) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                "DELETE FROM haven_virtual_inventories WHERE id = ?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        }
    }

    private VirtualInventory map(ResultSet rs) throws SQLException {
        return new VirtualInventory(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("owner_uuid")),
            rs.getString("name"),
            rs.getInt("rows"),
            rs.getLong("created_at")
        );
    }

    private void loadItems(Connection c, VirtualInventory inv) throws SQLException {
        String sql = "SELECT slot, item_data FROM haven_virtual_inventory_items WHERE inventory_id = ?";
        Map<Integer, ItemStack> contents = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, inv.getId().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt("slot");
                    byte[] data = rs.getBytes("item_data");
                    try {
                        ItemStack item = deserializeItem(data);
                        if (item != null) contents.put(slot, item);
                    } catch (Exception e) {
                        // Corrupt item data — skip slot rather than crash
                    }
                }
            }
        }
        inv.loadContents(contents);
    }

    private void saveItems(Connection c, VirtualInventory inv) throws SQLException, IOException {
        try (PreparedStatement del = c.prepareStatement(
                "DELETE FROM haven_virtual_inventory_items WHERE inventory_id = ?")) {
            del.setString(1, inv.getId().toString());
            del.executeUpdate();
        }
        Map<Integer, ItemStack> contents = inv.getContents();
        if (contents.isEmpty()) return;
        String ins = "INSERT INTO haven_virtual_inventory_items (inventory_id, slot, item_data) VALUES (?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(ins)) {
            for (Map.Entry<Integer, ItemStack> e : contents.entrySet()) {
                ps.setString(1, inv.getId().toString());
                ps.setInt(2, e.getKey());
                ps.setBytes(3, serializeItem(e.getValue()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private byte[] serializeItem(ItemStack item) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(baos)) {
            out.writeObject(item);
        }
        return baos.toByteArray();
    }

    private ItemStack deserializeItem(byte[] data) throws IOException, ClassNotFoundException {
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
            return (ItemStack) in.readObject();
        }
    }
}
