package com.playtheatria.buildersWand.prefab;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite-backed, player-global prefab ownership.
 *
 * <p>Unlike optional statistics, ownership fails closed. Callers can distinguish unavailable from
 * locked and must not grant, redeem, autocomplete, or place when this store is unavailable.</p>
 */
public final class PrefabEntitlementStore implements AutoCloseable {

    public static final String DATABASE_FILE_NAME = "prefab-entitlements.sqlite";

    public enum Access {
        UNLOCKED,
        LOCKED,
        UNAVAILABLE
    }

    public enum GrantResult {
        GRANTED,
        ALREADY_UNLOCKED,
        UNAVAILABLE
    }

    public enum RevokeResult {
        REVOKED,
        NOT_UNLOCKED,
        UNAVAILABLE
    }

    private final Logger logger;
    private final LongSupplier clock;
    private Connection connection;
    private boolean runtimeFailureLogged;

    private PrefabEntitlementStore(Connection connection, Logger logger, LongSupplier clock) {
        this.connection = connection;
        this.logger = logger;
        this.clock = clock;
    }

    public static PrefabEntitlementStore open(Path databasePath, Logger logger) {
        return open(databasePath, logger, System::currentTimeMillis);
    }

    static PrefabEntitlementStore open(
            Path databasePath,
            Logger logger,
            LongSupplier clock
    ) {
        Objects.requireNonNull(databasePath, "databasePath");
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(clock, "clock");
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
            return new PrefabEntitlementStore(connection, logger, clock);
        } catch (ClassNotFoundException | IOException | SQLException | SecurityException exception) {
            closeAfterFailure(connection, logger);
            logger.log(Level.SEVERE,
                    "Prefab ownership could not be initialized; prefab access will fail closed.",
                    exception);
            return new PrefabEntitlementStore(null, logger, clock);
        }
    }

    public synchronized boolean isAvailable() {
        return connection != null;
    }

    public synchronized GrantResult grant(UUID playerId, String prefabId, String source) {
        Objects.requireNonNull(playerId, "playerId");
        String validId = PrefabId.requireValid(prefabId);
        String validSource = requireSource(source);
        if (connection == null) {
            return GrantResult.UNAVAILABLE;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO prefab_unlocks (
                    player_uuid,
                    prefab_id,
                    unlocked_at_epoch_millis,
                    source
                ) VALUES (?, ?, ?, ?)
                ON CONFLICT(player_uuid, prefab_id) DO NOTHING
                """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, validId);
            statement.setLong(3, clock.getAsLong());
            statement.setString(4, validSource);
            return statement.executeUpdate() == 1
                    ? GrantResult.GRANTED
                    : GrantResult.ALREADY_UNLOCKED;
        } catch (SQLException exception) {
            logRuntimeFailure("grant prefab ownership", exception);
            return GrantResult.UNAVAILABLE;
        }
    }

    public synchronized RevokeResult revoke(UUID playerId, String prefabId) {
        Objects.requireNonNull(playerId, "playerId");
        String validId = PrefabId.requireValid(prefabId);
        if (connection == null) {
            return RevokeResult.UNAVAILABLE;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM prefab_unlocks
                WHERE player_uuid = ? AND prefab_id = ?
                """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, validId);
            return statement.executeUpdate() == 1
                    ? RevokeResult.REVOKED
                    : RevokeResult.NOT_UNLOCKED;
        } catch (SQLException exception) {
            logRuntimeFailure("revoke prefab ownership", exception);
            return RevokeResult.UNAVAILABLE;
        }
    }

    public synchronized Access access(UUID playerId, String prefabId) {
        if (connection == null) {
            return Access.UNAVAILABLE;
        }
        if (playerId == null || prefabId == null) {
            return Access.LOCKED;
        }
        final String validId;
        try {
            validId = PrefabId.requireValid(prefabId);
        } catch (IllegalArgumentException exception) {
            return Access.LOCKED;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM prefab_unlocks
                WHERE player_uuid = ? AND prefab_id = ?
                """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, validId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Access.UNLOCKED : Access.LOCKED;
            }
        } catch (SQLException exception) {
            logRuntimeFailure("check prefab ownership", exception);
            return Access.UNAVAILABLE;
        }
    }

    public synchronized Optional<PrefabEntitlement> find(UUID playerId, String prefabId) {
        if (connection == null || playerId == null || prefabId == null) {
            return Optional.empty();
        }
        final String validId;
        try {
            validId = PrefabId.requireValid(prefabId);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT unlocked_at_epoch_millis, source
                FROM prefab_unlocks
                WHERE player_uuid = ? AND prefab_id = ?
                """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, validId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PrefabEntitlement(
                        playerId,
                        validId,
                        result.getLong("unlocked_at_epoch_millis"),
                        result.getString("source")
                ));
            }
        } catch (SQLException exception) {
            logRuntimeFailure("read prefab ownership", exception);
            return Optional.empty();
        }
    }

    /** Empty optional means unavailable; an available player with no unlocks has an empty set. */
    public synchronized Optional<Set<String>> unlockedPrefabIds(UUID playerId) {
        if (connection == null || playerId == null) {
            return Optional.empty();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT prefab_id
                FROM prefab_unlocks
                WHERE player_uuid = ?
                ORDER BY prefab_id
                """)) {
            statement.setString(1, playerId.toString());
            Set<String> result = new LinkedHashSet<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(rows.getString("prefab_id"));
                }
            }
            return Optional.of(Collections.unmodifiableSet(result));
        } catch (SQLException exception) {
            logRuntimeFailure("list prefab ownership", exception);
            return Optional.empty();
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
            logger.log(Level.WARNING,
                    "Could not close the prefab entitlement database cleanly.",
                    exception);
        }
    }

    private static void initialize(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
            statement.execute("PRAGMA busy_timeout = 100");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS prefab_unlocks (
                        player_uuid TEXT NOT NULL,
                        prefab_id TEXT NOT NULL,
                        unlocked_at_epoch_millis INTEGER NOT NULL
                                CHECK (unlocked_at_epoch_millis >= 0),
                        source TEXT NOT NULL,
                        PRIMARY KEY (player_uuid, prefab_id)
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS prefab_unlocks_by_prefab
                    ON prefab_unlocks(prefab_id)
                    """);
        }
    }

    private static String requireSource(String source) {
        Objects.requireNonNull(source, "source");
        String normalized = source.trim();
        if (normalized.isEmpty() || normalized.length() > 160) {
            throw new IllegalArgumentException("Entitlement source must contain 1-160 characters");
        }
        return normalized;
    }

    private void logRuntimeFailure(String operation, SQLException exception) {
        if (runtimeFailureLogged) {
            return;
        }
        runtimeFailureLogged = true;
        logger.log(Level.SEVERE,
                "Could not " + operation + "; prefab access will fail closed for that operation.",
                exception);
    }

    private static void closeAfterFailure(Connection connection, Logger logger) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            logger.log(Level.WARNING,
                    "Could not close the prefab entitlement database after initialization failure.",
                    exception);
        }
    }
}
