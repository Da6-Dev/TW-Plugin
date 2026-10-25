package br.com.tw.spawners.data;

import br.com.tw.core.database.DatabaseManager;
import br.com.tw.spawners.models.PlacedSpawner;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.OptionalLong;
import java.util.logging.Level;

public class SpawnerRepository {

    private final DatabaseManager db;

    public SpawnerRepository(DatabaseManager db) {
        this.db = db;
        createTable();
    }

    private void createTable() {
        String query = "CREATE TABLE IF NOT EXISTS tw_spawners (" +
                "loc_key VARCHAR(128) PRIMARY KEY, " +
                "owner_uuid VARCHAR(36) NOT NULL, " +
                "owner_name VARCHAR(16) NOT NULL, " +
                "type_id VARCHAR(32) NOT NULL, " +
                "stack_amount BIGINT DEFAULT 1, " +
                "last_spawn_time BIGINT DEFAULT 0, " +
                "speed_level INT DEFAULT 1, " +
                "loot_level INT DEFAULT 1, " +
                "amount_level INT DEFAULT 1, " +
                "virtual_mode BOOLEAN DEFAULT FALSE, " +
                "auto_sell BOOLEAN DEFAULT FALSE, " +
                "stored_money DOUBLE PRECISION DEFAULT 0.0, " +
                "stored_tokens BIGINT DEFAULT 0, " +
                "stored_xp BIGINT DEFAULT 0, " +
                "stored_souls BIGINT DEFAULT 0, " +
                "total_kills BIGINT DEFAULT 0, " +
                "booster_mult DOUBLE PRECISION DEFAULT 1.0, " +
                "booster_end BIGINT DEFAULT 0, " +
                "pending_physical BIGINT DEFAULT 0" +
                ")";

        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(query);
            stmt.execute("ALTER TABLE tw_spawners ADD COLUMN IF NOT EXISTS auto_sell BOOLEAN DEFAULT FALSE");
            stmt.execute("ALTER TABLE tw_spawners ADD COLUMN IF NOT EXISTS stored_xp BIGINT DEFAULT 0");
            stmt.execute("ALTER TABLE tw_spawners ADD COLUMN IF NOT EXISTS stored_souls BIGINT DEFAULT 0");
            stmt.execute("ALTER TABLE tw_spawners ADD COLUMN IF NOT EXISTS booster_mult DOUBLE PRECISION DEFAULT 1.0");
            stmt.execute("ALTER TABLE tw_spawners ADD COLUMN IF NOT EXISTS booster_end BIGINT DEFAULT 0");
            stmt.execute("ALTER TABLE tw_spawners ADD COLUMN IF NOT EXISTS pending_physical BIGINT DEFAULT 0");
        } catch (SQLException e) {
            throw failure("Falha ao criar ou atualizar a tabela de spawners.", e);
        }
        db.recordSchemaVersion("tw_spawners", 3);
    }

    public Map<String, PlacedSpawner> loadAllSpawners() {
        Map<String, PlacedSpawner> map = new HashMap<>();
        String query = "SELECT * FROM tw_spawners";

        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                PlacedSpawner spawner = new PlacedSpawner(
                        rs.getString("loc_key"), UUID.fromString(rs.getString("owner_uuid")), rs.getString("owner_name"),
                        rs.getString("type_id"), rs.getLong("stack_amount"), rs.getLong("last_spawn_time"),
                        rs.getInt("speed_level"), rs.getInt("loot_level"), rs.getInt("amount_level"),
                        rs.getBoolean("virtual_mode"), rs.getBoolean("auto_sell"), rs.getDouble("stored_money"),
                        rs.getLong("stored_tokens"), rs.getLong("stored_xp"), rs.getLong("stored_souls"),
                        rs.getLong("total_kills"), rs.getDouble("booster_mult"), rs.getLong("booster_end")
                );
                spawner.setPendingPhysicalSpawn(rs.getLong("pending_physical"));
                map.put(spawner.getLocationKey(), spawner);
            }
        } catch (SQLException e) {
            throw failure("Falha ao carregar os spawners.", e);
        }
        return map;
    }

    public void saveSpawner(PlacedSpawner s) {
        String query = "INSERT INTO tw_spawners (loc_key, owner_uuid, owner_name, type_id, stack_amount, last_spawn_time, speed_level, loot_level, amount_level, virtual_mode, auto_sell, stored_money, stored_tokens, stored_xp, stored_souls, total_kills, booster_mult, booster_end, pending_physical) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (loc_key) DO UPDATE SET stack_amount = EXCLUDED.stack_amount, last_spawn_time = EXCLUDED.last_spawn_time, " +
                "speed_level = EXCLUDED.speed_level, loot_level = EXCLUDED.loot_level, amount_level = EXCLUDED.amount_level, " +
                "virtual_mode = EXCLUDED.virtual_mode, auto_sell = EXCLUDED.auto_sell, stored_money = EXCLUDED.stored_money, " +
                "stored_tokens = EXCLUDED.stored_tokens, stored_xp = EXCLUDED.stored_xp, stored_souls = EXCLUDED.stored_souls, " +
                "total_kills = EXCLUDED.total_kills, booster_mult = EXCLUDED.booster_mult, booster_end = EXCLUDED.booster_end, " +
                "pending_physical = EXCLUDED.pending_physical";

        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, s.getLocationKey()); ps.setString(2, s.getOwnerUuid().toString()); ps.setString(3, s.getOwnerName());
            ps.setString(4, s.getTypeId()); ps.setLong(5, s.getStackAmount()); ps.setLong(6, s.getLastSpawnTime());
            ps.setInt(7, s.getSpeedLevel()); ps.setInt(8, s.getLootLevel()); ps.setInt(9, s.getAmountLevel());
            ps.setBoolean(10, s.isVirtualMode()); ps.setBoolean(11, s.isAutoSell()); ps.setDouble(12, s.getStoredMoney());
            ps.setLong(13, s.getStoredTokens()); ps.setLong(14, s.getStoredXp()); ps.setLong(15, s.getStoredSouls());
            ps.setLong(16, s.getTotalKills()); ps.setDouble(17, s.getBoosterMultiplier()); ps.setLong(18, s.getBoosterEndTime());
            ps.setLong(19, s.getPendingPhysicalSpawn());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw failure("Falha ao salvar spawner em " + s.getLocationKey() + ".", e);
        }
    }

    public SpawnerPayout collectToUser(String locKey, UUID ownerId, double expectedMoney,
                                           long expectedTokens, double moneyToPay) {
            if (locKey == null || ownerId == null || !Double.isFinite(expectedMoney) || expectedMoney < 0.0
                    || expectedTokens < 0L || !Double.isFinite(moneyToPay) || moneyToPay < 0.0) {
                throw new IllegalArgumentException("Dados inválidos para recolher o cofre.");
            }
            String clear = "UPDATE tw_spawners SET stored_money = 0, stored_tokens = 0 "
                    + "WHERE loc_key = ? AND owner_uuid = ? AND stored_money = ? AND stored_tokens = ?";
            String user = "UPDATE tw_users SET balance = balance + ?, tokens = tokens + ? "
                    + "WHERE uuid = ? AND balance <= ? AND tokens <= ? RETURNING balance, tokens";
            try (Connection conn = db.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    try (PreparedStatement update = conn.prepareStatement(clear)) {
                        update.setString(1, locKey);
                        update.setString(2, ownerId.toString());
                        update.setDouble(3, expectedMoney);
                        update.setLong(4, expectedTokens);
                        if (update.executeUpdate() != 1) {
                            conn.rollback();
                            return null;
                        }
                    }

                    SpawnerPayout payout;
                    try (PreparedStatement updateUser = conn.prepareStatement(user)) {
                        updateUser.setDouble(1, moneyToPay);
                        updateUser.setLong(2, expectedTokens);
                        updateUser.setString(3, ownerId.toString());
                        updateUser.setDouble(4, Double.MAX_VALUE - moneyToPay);
                        updateUser.setLong(5, Long.MAX_VALUE - expectedTokens);
                        try (ResultSet result = updateUser.executeQuery()) {
                            if (!result.next()) {
                                throw new SQLException("O saldo do proprietário não existe ou excederia o limite numérico.");
                            }
                            payout = new SpawnerPayout(result.getDouble("balance"),
                                    result.getLong("tokens"), moneyToPay, expectedTokens);
                        }
                    }
                    conn.commit();
                    return payout;
                } catch (SQLException | RuntimeException e) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackFailure) {
                        e.addSuppressed(rollbackFailure);
                    }
                    throw e;
                } finally {
                    try {
                        conn.setAutoCommit(true);
                    } catch (SQLException e) {
                        db.getLogger().log(Level.WARNING, "Não foi possível restaurar o autocommit do banco.", e);
                    }
                }
            } catch (SQLException e) {
                throw failure("Falha ao recolher o cofre do spawner " + locKey + ".", e);
            }
        }
    public boolean deleteSpawner(String locKey) {
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("DELETE FROM tw_spawners WHERE loc_key = ?")) {
            ps.setString(1, locKey); return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw failure("Falha ao remover spawner em " + locKey + ".", e);
        }
    }

    public OptionalLong upgradeSpawner(String locKey, UUID ownerId, String upgrade, int currentLevel, long cost) {
        String assignment;
        String condition;
        switch (upgrade) {
            case "speed":
                assignment = "speed_level = speed_level + 1";
                condition = "speed_level = ?";
                break;
            case "loot":
                assignment = "loot_level = loot_level + 1";
                condition = "loot_level = ?";
                break;
            case "amount":
                assignment = "amount_level = amount_level + 1";
                condition = "amount_level = ?";
                break;
            case "auto-sell":
                assignment = "auto_sell = TRUE";
                condition = "auto_sell = FALSE";
                break;
            default:
                throw new IllegalArgumentException("Melhoria de spawner desconhecida.");
        }
        if (locKey == null || ownerId == null || cost < 0L
                || (!"auto-sell".equals(upgrade) && currentLevel < 1)) {
            throw new IllegalArgumentException("Dados inválidos para melhoria de spawner.");
        }
        String updateSpawner = "UPDATE tw_spawners SET " + assignment
                + " WHERE loc_key = ? AND owner_uuid = ? AND " + condition;
        String updateTokens = "UPDATE tw_users SET tokens = tokens - ? WHERE uuid = ? AND tokens >= ? RETURNING tokens";

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement spawner = conn.prepareStatement(updateSpawner)) {
                    spawner.setString(1, locKey);
                    spawner.setString(2, ownerId.toString());
                    if (!"auto-sell".equals(upgrade)) spawner.setInt(3, currentLevel);
                    if (spawner.executeUpdate() != 1) {
                        conn.rollback();
                        return OptionalLong.empty();
                    }
                }
                try (PreparedStatement user = conn.prepareStatement(updateTokens)) {
                    user.setLong(1, cost);
                    user.setString(2, ownerId.toString());
                    user.setLong(3, cost);
                    try (ResultSet result = user.executeQuery()) {
                        if (!result.next()) {
                            conn.rollback();
                            return OptionalLong.empty();
                        }
                        long remainingTokens = result.getLong("tokens");
                        conn.commit();
                        return OptionalLong.of(remainingTokens);
                    }
                }
            } catch (SQLException | RuntimeException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
                throw e;
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    db.getLogger().log(Level.WARNING, "Não foi possível restaurar o autocommit do banco.", e);
                }
            }
        } catch (SQLException e) {
            throw failure("Falha ao evoluir o spawner " + locKey + ".", e);
        }
    }

    private IllegalStateException failure(String message, SQLException cause) {
        db.getLogger().log(Level.SEVERE, message, cause);
        return new IllegalStateException(message, cause);
    }
}