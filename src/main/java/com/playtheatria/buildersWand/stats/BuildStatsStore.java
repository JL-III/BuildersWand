package com.playtheatria.buildersWand.stats;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite-backed aggregate build statistics.
 *
 * <p>The store deliberately fails open: if the database cannot be initialized, gameplay can
 * retain the returned disabled instance and every operation becomes a safe no-op.</p>
 */
public final class BuildStatsStore implements AutoCloseable {

    public static final String DATABASE_FILE_NAME = "build-stats.sqlite";

    private static final String UPSERT_PLAYER = """
            INSERT INTO player_build_stats (
                player_uuid,
                last_known_name,
                total_uses,
                non_water_blocks_placed,
                water_source_blocks_placed,
                prints_completed,
                largest_completed_print,
                updated_at_epoch_millis
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET
                last_known_name = excluded.last_known_name,
                total_uses = player_build_stats.total_uses + excluded.total_uses,
                non_water_blocks_placed = player_build_stats.non_water_blocks_placed
                        + excluded.non_water_blocks_placed,
                water_source_blocks_placed = player_build_stats.water_source_blocks_placed
                        + excluded.water_source_blocks_placed,
                prints_completed = player_build_stats.prints_completed + excluded.prints_completed,
                largest_completed_print = MAX(
                        player_build_stats.largest_completed_print,
                        excluded.largest_completed_print
                ),
                updated_at_epoch_millis = excluded.updated_at_epoch_millis
            """;

    private static final String SELECT_PLAYER = """
            SELECT
                last_known_name,
                total_uses,
                non_water_blocks_placed,
                water_source_blocks_placed,
                prints_completed,
                largest_completed_print,
                refills_completed,
                refill_uses_purchased,
                refill_denarii_spent,
                updated_at_epoch_millis
            FROM player_build_stats
            WHERE player_uuid = ?
            """;

    private static final String UPSERT_PAID_REFILL = """
            INSERT INTO player_build_stats (
                player_uuid,
                last_known_name,
                refills_completed,
                refill_uses_purchased,
                refill_denarii_spent,
                updated_at_epoch_millis
            ) VALUES (?, ?, 1, ?, ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET
                last_known_name = excluded.last_known_name,
                refills_completed = player_build_stats.refills_completed + 1,
                refill_uses_purchased = player_build_stats.refill_uses_purchased
                        + excluded.refill_uses_purchased,
                refill_denarii_spent = player_build_stats.refill_denarii_spent
                        + excluded.refill_denarii_spent,
                updated_at_epoch_millis = excluded.updated_at_epoch_millis
            """;

    private static final String SELECT_SERVER = """
            SELECT
                COUNT(*) AS players_tracked,
                COALESCE(SUM(total_uses), 0) AS total_uses,
                COALESCE(SUM(non_water_blocks_placed), 0) AS non_water_blocks_placed,
                COALESCE(SUM(water_source_blocks_placed), 0) AS water_source_blocks_placed,
                COALESCE(SUM(prints_completed), 0) AS prints_completed,
                COALESCE(MAX(largest_completed_print), 0) AS largest_completed_print,
                COALESCE(SUM(refills_completed), 0) AS refills_completed,
                COALESCE(SUM(refill_uses_purchased), 0) AS refill_uses_purchased,
                COALESCE(SUM(refill_denarii_spent), 0) AS refill_denarii_spent
            FROM player_build_stats
            """;

    private final Logger logger;
    private Connection connection;
    private boolean runtimeFailureLogged;

    private BuildStatsStore(Connection connection, Logger logger) {
        this.connection = connection;
        this.logger = logger;
    }

    /** Opens the default database in the plugin's data folder. */
    public static BuildStatsStore open(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return open(plugin.getDataFolder().toPath().resolve(DATABASE_FILE_NAME), plugin.getLogger());
    }

    /** Path/logger seam for tests and non-Bukkit bootstrap code. */
    public static BuildStatsStore open(Path databasePath, Logger logger) {
        Objects.requireNonNull(databasePath, "databasePath");
        Objects.requireNonNull(logger, "logger");

        Connection connection = null;
        try {
            Path normalized = databasePath.toAbsolutePath().normalize();
            Path parent = normalized.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + normalized);
            initialize(connection);
            return new BuildStatsStore(connection, logger);
        } catch (ClassNotFoundException | IOException | SQLException | SecurityException exception) {
            closeAfterFailedInitialization(connection, logger);
            logger.log(Level.SEVERE,
                    "Build statistics could not be initialized; building will continue without durable statistics.",
                    exception);
            return new BuildStatsStore(null, logger);
        }
    }

    public synchronized boolean isAvailable() {
        return connection != null;
    }

    /**
     * Atomically adds the contribution of one wave to a player's durable totals.
     *
     * @return true when the delta was persisted; false when disabled or rejected
     */
    public synchronized boolean recordWave(
            UUID playerId,
            String lastKnownName,
            WaveStatsDelta delta
    ) {
        if (connection == null) {
            return false;
        }
        if (playerId == null || delta == null || !valid(delta)) {
            logger.warning("Rejected an invalid build statistics wave delta; building will continue.");
            return false;
        }

        long completedPrints = delta.printCompleted() ? 1L : 0L;
        long largestCompletedPrint = delta.printCompleted() ? delta.plannedPrintableCells() : 0L;
        long now = System.currentTimeMillis();

        try (PreparedStatement statement = connection.prepareStatement(UPSERT_PLAYER)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, normalizedName(playerId, lastKnownName));
            statement.setLong(3, delta.completedUses());
            statement.setLong(4, delta.nonWaterBlocksPlaced());
            statement.setLong(5, delta.waterSourceBlocksPlaced());
            statement.setLong(6, completedPrints);
            statement.setLong(7, largestCompletedPrint);
            statement.setLong(8, now);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            logRuntimeFailure("record a build wave", exception);
            return false;
        }
    }

    /** Convenience overload for callers that do not need to retain a delta object. */
    public boolean recordWave(
            UUID playerId,
            String lastKnownName,
            long completedUses,
            long nonWaterBlocksPlaced,
            long waterSourceBlocksPlaced,
            boolean printCompleted,
            long plannedPrintableCells
    ) {
        return recordWave(playerId, lastKnownName, new WaveStatsDelta(
                completedUses,
                nonWaterBlocksPlaced,
                waterSourceBlocksPlaced,
                printCompleted,
                plannedPrintableCells
        ));
    }

    /**
     * Atomically adds one successful paid refill to the player's durable economic totals.
     *
     * @return true when the refill was persisted; false when disabled or rejected
     */
    public synchronized boolean recordPaidRefill(
            UUID playerId,
            String lastKnownName,
            long addedUses,
            long denariiSpent
    ) {
        if (connection == null) {
            return false;
        }
        if (playerId == null || addedUses <= 0 || denariiSpent < 0) {
            logger.warning("Rejected invalid paid refill statistics; gameplay will continue.");
            return false;
        }

        try (PreparedStatement statement = connection.prepareStatement(UPSERT_PAID_REFILL)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, normalizedName(playerId, lastKnownName));
            statement.setLong(3, addedUses);
            statement.setLong(4, denariiSpent);
            statement.setLong(5, System.currentTimeMillis());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            logRuntimeFailure("record a paid Builders Wand refill", exception);
            return false;
        }
    }

    public synchronized Optional<PlayerBuildStats> findPlayer(UUID playerId) {
        if (connection == null || playerId == null) {
            return Optional.empty();
        }

        try (PreparedStatement statement = connection.prepareStatement(SELECT_PLAYER)) {
            statement.setString(1, playerId.toString());
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PlayerBuildStats(
                        playerId,
                        results.getString("last_known_name"),
                        results.getLong("total_uses"),
                        results.getLong("non_water_blocks_placed"),
                        results.getLong("water_source_blocks_placed"),
                        results.getLong("prints_completed"),
                        results.getLong("largest_completed_print"),
                        results.getLong("updated_at_epoch_millis"),
                        results.getLong("refills_completed"),
                        results.getLong("refill_uses_purchased"),
                        results.getLong("refill_denarii_spent")
                ));
            }
        } catch (SQLException exception) {
            logRuntimeFailure("query player build statistics", exception);
            return Optional.empty();
        }
    }

    /** Empty means the store is unavailable; an available empty database returns zero totals. */
    public synchronized Optional<ServerBuildStats> serverTotals() {
        if (connection == null) {
            return Optional.empty();
        }

        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(SELECT_SERVER)) {
            if (!results.next()) {
                return Optional.of(new ServerBuildStats(0, 0, 0, 0, 0, 0, 0, 0, 0));
            }
            return Optional.of(new ServerBuildStats(
                    results.getLong("players_tracked"),
                    results.getLong("total_uses"),
                    results.getLong("non_water_blocks_placed"),
                    results.getLong("water_source_blocks_placed"),
                    results.getLong("prints_completed"),
                    results.getLong("largest_completed_print"),
                    results.getLong("refills_completed"),
                    results.getLong("refill_uses_purchased"),
                    results.getLong("refill_denarii_spent")
            ));
        } catch (SQLException exception) {
            logRuntimeFailure("query server build statistics", exception);
            return Optional.empty();
        }
    }

    /**
     * Claims a reached milestone exactly once.
     *
     * <p>The threshold check and unique claim insert occur in one SQLite statement. False means
     * the player has not reached it, it was already claimed, or the store is unavailable.</p>
     */
    public synchronized boolean claimRecognitionIfReached(
            UUID playerId,
            BuildStatistic statistic,
            long threshold
    ) {
        if (connection == null || playerId == null || statistic == null || threshold <= 0) {
            return false;
        }

        String sql = """
                INSERT INTO recognition_claims (
                    player_uuid,
                    statistic,
                    threshold,
                    claimed_at_epoch_millis
                )
                SELECT ?, ?, ?, ?
                FROM player_build_stats
                WHERE player_uuid = ?
                  AND (%s) >= ?
                ON CONFLICT(player_uuid, statistic, threshold) DO NOTHING
                """.formatted(statistic.valueExpression());

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, statistic.storageKey());
            statement.setLong(3, threshold);
            statement.setLong(4, System.currentTimeMillis());
            statement.setString(5, playerId.toString());
            statement.setLong(6, threshold);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            logRuntimeFailure("claim a build recognition threshold", exception);
            return false;
        }
    }

    @Override
    public synchronized void close() {
        if (connection == null) {
            return;
        }
        Connection toClose = connection;
        connection = null;
        try {
            toClose.close();
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Could not close the build statistics database cleanly.", exception);
        }
    }

    private static void initialize(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
            // Wave settlement runs on Paper's main thread. Fail this optional telemetry update
            // quickly instead of allowing an external writer to freeze server ticks for seconds.
            statement.execute("PRAGMA busy_timeout = 100");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_build_stats (
                        player_uuid TEXT PRIMARY KEY NOT NULL,
                        last_known_name TEXT NOT NULL,
                        total_uses INTEGER NOT NULL DEFAULT 0 CHECK (total_uses >= 0),
                        non_water_blocks_placed INTEGER NOT NULL DEFAULT 0
                                CHECK (non_water_blocks_placed >= 0),
                        water_source_blocks_placed INTEGER NOT NULL DEFAULT 0
                                CHECK (water_source_blocks_placed >= 0),
                        prints_completed INTEGER NOT NULL DEFAULT 0 CHECK (prints_completed >= 0),
                        largest_completed_print INTEGER NOT NULL DEFAULT 0
                                CHECK (largest_completed_print >= 0),
                        refills_completed INTEGER NOT NULL DEFAULT 0 CHECK (refills_completed >= 0),
                        refill_uses_purchased INTEGER NOT NULL DEFAULT 0
                                CHECK (refill_uses_purchased >= 0),
                        refill_denarii_spent INTEGER NOT NULL DEFAULT 0
                                CHECK (refill_denarii_spent >= 0),
                        updated_at_epoch_millis INTEGER NOT NULL
                    )
                    """);
            migrateRefillColumns(connection);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS recognition_claims (
                        player_uuid TEXT NOT NULL,
                        statistic TEXT NOT NULL,
                        threshold INTEGER NOT NULL CHECK (threshold > 0),
                        claimed_at_epoch_millis INTEGER NOT NULL,
                        PRIMARY KEY (player_uuid, statistic, threshold),
                        FOREIGN KEY (player_uuid) REFERENCES player_build_stats(player_uuid)
                                ON DELETE CASCADE
                    )
                    """);
        }
    }

    private static void migrateRefillColumns(Connection connection) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement inspect = connection.createStatement();
             ResultSet results = inspect.executeQuery("PRAGMA table_info(player_build_stats)")) {
            while (results.next()) {
                columns.add(results.getString("name"));
            }
        }

        addColumnIfMissing(connection, columns, "refills_completed",
                "INTEGER NOT NULL DEFAULT 0 CHECK (refills_completed >= 0)");
        addColumnIfMissing(connection, columns, "refill_uses_purchased",
                "INTEGER NOT NULL DEFAULT 0 CHECK (refill_uses_purchased >= 0)");
        addColumnIfMissing(connection, columns, "refill_denarii_spent",
                "INTEGER NOT NULL DEFAULT 0 CHECK (refill_denarii_spent >= 0)");
    }

    private static void addColumnIfMissing(
            Connection connection,
            Set<String> existingColumns,
            String column,
            String definition
    ) throws SQLException {
        if (existingColumns.contains(column)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE player_build_stats ADD COLUMN " + column + " " + definition);
        }
        existingColumns.add(column);
    }

    private static boolean valid(WaveStatsDelta delta) {
        return delta.completedUses() >= 0
                && delta.nonWaterBlocksPlaced() >= 0
                && delta.waterSourceBlocksPlaced() >= 0
                && delta.plannedPrintableCells() >= 0;
    }

    private static String normalizedName(UUID playerId, String lastKnownName) {
        if (lastKnownName == null || lastKnownName.isBlank()) {
            return playerId.toString();
        }
        return lastKnownName;
    }

    private static void closeAfterFailedInitialization(Connection connection, Logger logger) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException closeFailure) {
            logger.log(Level.WARNING,
                    "Could not close the build statistics database after an initialization failure.",
                    closeFailure);
        }
    }

    private void logRuntimeFailure(String operation, SQLException exception) {
        if (runtimeFailureLogged) {
            return;
        }
        runtimeFailureLogged = true;
        logger.log(Level.SEVERE,
                "Could not " + operation + "; building will continue without that statistics update.",
                exception);
    }
}
