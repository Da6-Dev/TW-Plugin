package br.com.tw.maquinas.data;

import br.com.tw.core.database.DatabaseManager;
import br.com.tw.maquinas.models.PlacedMachine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.logging.Level;

public class MachineRepository {

    private final DatabaseManager db;

    public MachineRepository(DatabaseManager db) {
        this.db = db;
        createTable();
    }

    private void createTable() {
        String query = "CREATE TABLE IF NOT EXISTS tw_machines (" +
                "loc_key VARCHAR(128) PRIMARY KEY, " +
                "owner_uuid VARCHAR(36) NOT NULL, " +
                "owner_name VARCHAR(16) NOT NULL, " +
                "type_id VARCHAR(32) NOT NULL, " +
                "stack_amount BIGINT DEFAULT 1, " +
                "fuel BIGINT DEFAULT 0, " +
                "stored_earnings DOUBLE PRECISION DEFAULT 0.0, " +
                "speed_level INT DEFAULT 1, " +
                "storage_level INT DEFAULT 1, " +
                "fuel_eff_level INT DEFAULT 1, " +
                "total_generated DOUBLE PRECISION DEFAULT 0.0, " +
                "battery_cycles BIGINT DEFAULT 0, " +
                "lubricant_cycles BIGINT DEFAULT 0" +
                ")";

        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(query);
            stmt.execute("ALTER TABLE tw_machines ADD COLUMN IF NOT EXISTS speed_level INT DEFAULT 1");
            stmt.execute("ALTER TABLE tw_machines ADD COLUMN IF NOT EXISTS storage_level INT DEFAULT 1");
            stmt.execute("ALTER TABLE tw_machines ADD COLUMN IF NOT EXISTS fuel_eff_level INT DEFAULT 1");
            stmt.execute("ALTER TABLE tw_machines ADD COLUMN IF NOT EXISTS total_generated DOUBLE PRECISION DEFAULT 0.0");
            stmt.execute("ALTER TABLE tw_machines ADD COLUMN IF NOT EXISTS battery_cycles BIGINT DEFAULT 0");
            stmt.execute("ALTER TABLE tw_machines ADD COLUMN IF NOT EXISTS lubricant_cycles BIGINT DEFAULT 0");
        } catch (SQLException e) {
            throw failure("Falha ao criar ou atualizar a tabela de máquinas.", e);
        }
        db.recordSchemaVersion("tw_maquinas", 3);
    }

    public Map<String, PlacedMachine> loadAllMachines() {
        Map<String, PlacedMachine> map = new HashMap<>();
        String query = "SELECT * FROM tw_machines";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                PlacedMachine machine = new PlacedMachine(
                        rs.getString("loc_key"),
                        UUID.fromString(rs.getString("owner_uuid")),
                        rs.getString("owner_name"),
                        rs.getString("type_id"),
                        rs.getLong("stack_amount"),
                        rs.getLong("fuel"),
                        rs.getDouble("stored_earnings"),
                        rs.getInt("speed_level"),
                        rs.getInt("storage_level"),
                        rs.getInt("fuel_eff_level"),
                        rs.getDouble("total_generated"),
                        rs.getLong("battery_cycles"),
                        rs.getLong("lubricant_cycles")
                );
                map.put(machine.getLocationKey(), machine);
            }
        } catch (SQLException | RuntimeException e) {
            throw failure("Falha ao carregar as máquinas.", e);
        }
        return map;
    }

    public void saveMachine(PlacedMachine machine) {
        String query = "INSERT INTO tw_machines (loc_key, owner_uuid, owner_name, type_id, stack_amount, fuel, "
                + "stored_earnings, speed_level, storage_level, fuel_eff_level, total_generated, battery_cycles, "
                + "lubricant_cycles) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (loc_key) DO UPDATE SET stack_amount = EXCLUDED.stack_amount, fuel = EXCLUDED.fuel, "
                + "stored_earnings = EXCLUDED.stored_earnings, speed_level = EXCLUDED.speed_level, "
                + "storage_level = EXCLUDED.storage_level, fuel_eff_level = EXCLUDED.fuel_eff_level, "
                + "total_generated = EXCLUDED.total_generated, battery_cycles = EXCLUDED.battery_cycles, "
                + "lubricant_cycles = EXCLUDED.lubricant_cycles";

        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, machine.getLocationKey());
            ps.setString(2, machine.getOwnerUuid().toString());
            ps.setString(3, machine.getOwnerName());
            ps.setString(4, machine.getTypeId());
            ps.setLong(5, machine.getStackAmount());
            ps.setLong(6, machine.getFuel());
            ps.setDouble(7, machine.getStoredEarnings());
            ps.setInt(8, machine.getSpeedLevel());
            ps.setInt(9, machine.getStorageLevel());
            ps.setInt(10, machine.getFuelEfficiencyLevel());
            ps.setDouble(11, machine.getTotalGenerated());
            ps.setLong(12, machine.getBatteryCycles());
            ps.setLong(13, machine.getLubricantCycles());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw failure("Falha ao salvar máquina em " + machine.getLocationKey() + ".", e);
        }
    }

    public boolean deleteMachine(String locKey) {
        String query = "DELETE FROM tw_machines WHERE loc_key = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, locKey);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw failure("Falha ao remover máquina em " + locKey + ".", e);
        }
    }

    public OptionalDouble collectEarnings(String locKey, UUID ownerId, double expectedStored, double payout) {
        if (locKey == null || ownerId == null || !Double.isFinite(expectedStored) || expectedStored <= 0.0
                || !Double.isFinite(payout) || payout <= 0.0) {
            throw new IllegalArgumentException("Dados inválidos para recolher a produção da máquina.");
        }

        String clearMachine = "UPDATE tw_machines SET stored_earnings = 0 "
                + "WHERE loc_key = ? AND owner_uuid = ? AND stored_earnings = ?";
        String findBalance = "SELECT balance FROM tw_users WHERE uuid = ? FOR UPDATE";
        String updateBalance = "UPDATE tw_users SET balance = ? WHERE uuid = ?";

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement clear = conn.prepareStatement(clearMachine)) {
                    clear.setString(1, locKey);
                    clear.setString(2, ownerId.toString());
                    clear.setDouble(3, expectedStored);
                    if (clear.executeUpdate() != 1) {
                        conn.rollback();
                        return OptionalDouble.empty();
                    }
                }

                double balance;
                try (PreparedStatement select = conn.prepareStatement(findBalance)) {
                    select.setString(1, ownerId.toString());
                    try (ResultSet result = select.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException("O proprietário não possui dados persistidos no TW-Core.");
                        }
                        balance = result.getDouble("balance");
                    }
                }
                double updatedBalance = balance + payout;
                if (!Double.isFinite(updatedBalance)) {
                    throw new SQLException("O pagamento excederia o limite numérico do saldo.");
                }
                try (PreparedStatement update = conn.prepareStatement(updateBalance)) {
                    update.setDouble(1, updatedBalance);
                    update.setString(2, ownerId.toString());
                    if (update.executeUpdate() != 1) {
                        throw new SQLException("Não foi possível atualizar o saldo do proprietário.");
                    }
                }
                conn.commit();
                return OptionalDouble.of(updatedBalance);
            } catch (SQLException | RuntimeException e) {
                rollback(conn, e);
                throw e;
            } finally {
                restoreAutoCommit(conn);
            }
        } catch (SQLException e) {
            throw failure("Falha ao recolher a produção da máquina " + locKey + ".", e);
        }
    }

    public OptionalLong upgradeMachine(String locKey, UUID playerId, String upgrade, int currentLevel, long cost) {
        String column;
        switch (upgrade) {
            case "speed":
                column = "speed_level";
                break;
            case "storage":
                column = "storage_level";
                break;
            case "fuel-efficiency":
                column = "fuel_eff_level";
                break;
            default:
                throw new IllegalArgumentException("Tipo de melhoria desconhecido.");
        }
        if (locKey == null || playerId == null || currentLevel < 1 || cost < 0L) {
            throw new IllegalArgumentException("Dados inválidos para evoluir a máquina.");
        }

        String updateMachine = "UPDATE tw_machines SET " + column + " = " + column
                + " + 1 WHERE loc_key = ? AND " + column + " = ?";
        String updateTokens = "UPDATE tw_users SET tokens = tokens - ? WHERE uuid = ? AND tokens >= ? RETURNING tokens";

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement machine = conn.prepareStatement(updateMachine)) {
                    machine.setString(1, locKey);
                    machine.setInt(2, currentLevel);
                    if (machine.executeUpdate() != 1) {
                        conn.rollback();
                        return OptionalLong.empty();
                    }
                }

                long tokens;
                try (PreparedStatement user = conn.prepareStatement(updateTokens)) {
                    user.setLong(1, cost);
                    user.setString(2, playerId.toString());
                    user.setLong(3, cost);
                    try (ResultSet result = user.executeQuery()) {
                        if (!result.next()) {
                            conn.rollback();
                            return OptionalLong.empty();
                        }
                        tokens = result.getLong("tokens");
                    }
                }
                conn.commit();
                return OptionalLong.of(tokens);
            } catch (SQLException | RuntimeException e) {
                rollback(conn, e);
                throw e;
            } finally {
                restoreAutoCommit(conn);
            }
        } catch (SQLException e) {
            throw failure("Falha ao evoluir a máquina " + locKey + ".", e);
        }
    }

    private void rollback(Connection conn, Exception original) {
        try {
            conn.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private void restoreAutoCommit(Connection conn) {
        try {
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            db.getLogger().log(Level.WARNING, "Não foi possível restaurar o modo autocommit.", e);
        }
    }

    private IllegalStateException failure(String message, Exception cause) {
        db.getLogger().log(Level.SEVERE, message, cause);
        return new IllegalStateException(message, cause);
    }
}
