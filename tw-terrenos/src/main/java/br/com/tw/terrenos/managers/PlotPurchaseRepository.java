package br.com.tw.terrenos.managers;

import br.com.tw.core.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class PlotPurchaseRepository {
    private final DatabaseManager database;

    public PlotPurchaseRepository(DatabaseManager database) {
        this.database = database;
        createTables();
    }

    private void createTables() {
        String purchases = "CREATE TABLE IF NOT EXISTS tw_plot_purchases ("
                + "id BIGSERIAL PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, "
                + "player_name VARCHAR(16) NOT NULL, plot_world VARCHAR(64) NOT NULL, "
                + "amount DOUBLE PRECISION NOT NULL CHECK (amount >= 0), "
                + "plots_before INTEGER NOT NULL DEFAULT 0, status VARCHAR(16) NOT NULL, "
                + "created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)";
        String audit = "CREATE TABLE IF NOT EXISTS tw_plot_audit_log ("
                + "id BIGSERIAL PRIMARY KEY, action VARCHAR(32) NOT NULL, "
                + "actor_uuid VARCHAR(36), actor_name VARCHAR(16), target_uuid VARCHAR(36), target_name VARCHAR(16), "
                + "plot_world VARCHAR(64) NOT NULL, plot_id VARCHAR(64), "
                + "details TEXT NOT NULL DEFAULT '', created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)";
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(purchases);
            statement.execute("ALTER TABLE tw_plot_purchases ADD COLUMN IF NOT EXISTS plots_before INTEGER NOT NULL DEFAULT 0");
            statement.execute(audit);
            statement.execute("ALTER TABLE tw_plot_audit_log ADD COLUMN IF NOT EXISTS target_name VARCHAR(16)");
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS tw_plot_purchases_one_pending "
                    + "ON tw_plot_purchases (player_uuid) WHERE status = 'PENDING'");
            statement.execute("CREATE INDEX IF NOT EXISTS tw_plot_audit_player "
                    + "ON tw_plot_audit_log (actor_uuid, created_at DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS tw_plot_audit_target "
                    + "ON tw_plot_audit_log (target_uuid, created_at DESC)");
        } catch (SQLException exception) {
            throw failure("Não foi possível preparar as tabelas de auditoria de terrenos.", exception);
        }
        database.recordSchemaVersion("tw_terrenos", 1);
    }

    public PurchaseDebit debitForPurchase(UUID playerId, String playerName, String world, double amount, int plotsBefore) {
        String lockBalance = "SELECT balance FROM tw_users WHERE uuid = ? FOR UPDATE";
        String updateBalance = "UPDATE tw_users SET balance = balance - ? WHERE uuid = ? AND balance >= ?";
        String insert = "INSERT INTO tw_plot_purchases "
                + "(player_uuid, player_name, plot_world, amount, plots_before, status) "
                + "VALUES (?, ?, ?, ?, ?, 'PENDING') RETURNING id";
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                double currentBalance;
                try (PreparedStatement statement = connection.prepareStatement(lockBalance)) {
                    statement.setString(1, playerId.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalStateException("Os dados financeiros do jogador não existem no banco.");
                        }
                        currentBalance = result.getDouble(1);
                    }
                }
                if (currentBalance < amount) {
                    connection.rollback();
                    return null;
                }

                try (PreparedStatement statement = connection.prepareStatement(updateBalance)) {
                    statement.setDouble(1, amount);
                    statement.setString(2, playerId.toString());
                    statement.setDouble(3, amount);
                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        return null;
                    }
                }

                long purchaseId;
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    statement.setString(1, playerId.toString());
                    statement.setString(2, playerName);
                    statement.setString(3, world);
                    statement.setDouble(4, amount);
                    statement.setInt(5, plotsBefore);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException("O banco não retornou o identificador da compra.");
                        }
                        purchaseId = result.getLong(1);
                    }
                }
                connection.commit();
                return new PurchaseDebit(purchaseId, currentBalance - amount);
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw failure("Não foi possível registrar a compra de terreno.", exception);
        }
    }

    public Double refund(long purchaseId) {
        String select = "SELECT player_uuid, amount FROM tw_plot_purchases "
                + "WHERE id = ? AND status = 'PENDING' FOR UPDATE";
        String credit = "UPDATE tw_users SET balance = balance + ? WHERE uuid = ? RETURNING balance";
        String update = "UPDATE tw_plot_purchases SET status = 'REFUNDED', updated_at = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND status = 'PENDING'";
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                UUID playerId;
                double amount;
                try (PreparedStatement statement = connection.prepareStatement(select)) {
                    statement.setLong(1, purchaseId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return null;
                        }
                        playerId = UUID.fromString(result.getString(1));
                        amount = result.getDouble(2);
                    }
                }

                double newBalance;
                try (PreparedStatement statement = connection.prepareStatement(credit)) {
                    statement.setDouble(1, amount);
                    statement.setString(2, playerId.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException("O jogador da compra pendente não existe em tw_users.");
                        }
                        newBalance = result.getDouble(1);
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(update)) {
                    statement.setLong(1, purchaseId);
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("A compra deixou de estar pendente durante o reembolso.");
                    }
                }
                connection.commit();
                return newBalance;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw failure("Não foi possível reembolsar a compra de terreno " + purchaseId + ".", exception);
        }
    }

    public boolean complete(long purchaseId) {
        String update = "UPDATE tw_plot_purchases SET status = 'COMPLETED', updated_at = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND status = 'PENDING'";
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(update)) {
            statement.setLong(1, purchaseId);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw failure("Não foi possível confirmar a compra de terreno " + purchaseId + ".", exception);
        }
    }

    public List<PendingPurchase> findPending(UUID playerId) {
        String query = "SELECT id, amount, plot_world, plots_before FROM tw_plot_purchases "
                + "WHERE player_uuid = ? AND status = 'PENDING' ORDER BY id";
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<PendingPurchase> purchases = new ArrayList<>();
                while (result.next()) {
                    purchases.add(new PendingPurchase(result.getLong("id"), result.getDouble("amount"),
                            result.getString("plot_world"), result.getInt("plots_before")));
                }
                return purchases;
            }
        } catch (SQLException exception) {
            throw failure("Não foi possível recuperar as compras pendentes do jogador.", exception);
        }
    }

    public void recordAudit(String action, UUID actorId, String actorName, UUID targetId, String targetName,
                            String world, String plotId, String details) {
        String insert = "INSERT INTO tw_plot_audit_log "
                + "(action, actor_uuid, actor_name, target_uuid, target_name, plot_world, plot_id, details) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, action);
            statement.setString(2, actorId == null ? null : actorId.toString());
            statement.setString(3, actorName);
            statement.setString(4, targetId == null ? null : targetId.toString());
            statement.setString(5, targetName);
            statement.setString(6, world);
            statement.setString(7, plotId);
            statement.setString(8, details == null ? "" : details);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw failure("Não foi possível registrar uma ação de auditoria de terrenos.", exception);
        }
    }

    public List<String> getAudit(String playerName, int limit) {
        String purchases = "SELECT created_at, status, amount, plot_world, id FROM tw_plot_purchases "
                + "WHERE LOWER(player_name) = LOWER(?) ORDER BY created_at DESC LIMIT ?";
        String audit = "SELECT created_at, action, actor_name, target_name, plot_world, plot_id, details "
                + "FROM tw_plot_audit_log WHERE LOWER(actor_name) = LOWER(?) OR LOWER(target_name) = LOWER(?) "
                + "ORDER BY created_at DESC LIMIT ?";
        List<String> lines = new ArrayList<>();
        try (Connection connection = database.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(purchases)) {
                statement.setString(1, playerName);
                statement.setInt(2, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        lines.add(result.getTimestamp("created_at") + " | compra #" + result.getLong("id")
                                + " | " + result.getString("status") + " | $"
                                + result.getDouble("amount") + " | " + result.getString("plot_world"));
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(audit)) {
                statement.setString(1, playerName);
                statement.setString(2, playerName);
                statement.setInt(3, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        lines.add(result.getTimestamp("created_at") + " | " + result.getString("action")
                                + " | por " + result.getString("actor_name") + " | alvo "
                                + result.getString("target_name") + " | " + result.getString("plot_world")
                                + " " + result.getString("plot_id") + " | " + result.getString("details"));
                    }
                }
            }
            lines.sort((left, right) -> right.compareTo(left));
            return lines;
        } catch (SQLException exception) {
            throw failure("Não foi possível consultar a auditoria do jogador " + playerName + ".", exception);
        }
    }

    private void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private IllegalStateException failure(String message, SQLException cause) {
        database.getLogger().log(Level.SEVERE, message, cause);
        return new IllegalStateException(message, cause);
    }

    public static final class PurchaseDebit {
        private final long id;
        private final double balance;

        private PurchaseDebit(long id, double balance) {
            this.id = id;
            this.balance = balance;
        }

        public long getId() {
            return id;
        }

        public double getBalance() {
            return balance;
        }
    }

    public static final class PendingPurchase {
        private final long id;
        private final double amount;
        private final String world;
        private final int plotsBefore;

        private PendingPurchase(long id, double amount, String world, int plotsBefore) {
            this.id = id;
            this.amount = amount;
            this.world = world;
            this.plotsBefore = plotsBefore;
        }

        public long getId() {
            return id;
        }

        public double getAmount() {
            return amount;
        }

        public String getWorld() {
            return world;
        }

        public int getPlotsBefore() {
            return plotsBefore;
        }
    }
}
