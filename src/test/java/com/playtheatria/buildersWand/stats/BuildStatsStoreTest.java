package com.playtheatria.buildersWand.stats;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildStatsStoreTest {

    private static final Logger LOGGER = Logger.getLogger(BuildStatsStoreTest.class.getName());

    @TempDir
    Path tempDirectory;

    @Test
    void accumulatesWaveDeltasAndUsesPlannedSizeOnlyForCompletedPrints() {
        UUID playerId = UUID.randomUUID();

        try (BuildStatsStore store = openStore()) {
            assertTrue(store.recordWave(playerId, "Builder", new WaveStatsDelta(
                    30,
                    20,
                    4,
                    false,
                    100
            )));
            assertTrue(store.recordWave(playerId, "BuilderRenamed", new WaveStatsDelta(
                    75,
                    50,
                    10,
                    true,
                    125
            )));
            assertTrue(store.recordWave(playerId, "BuilderRenamed", new WaveStatsDelta(
                    40,
                    36,
                    4,
                    true,
                    80
            )));

            PlayerBuildStats stats = store.findPlayer(playerId).orElseThrow();
            assertEquals("BuilderRenamed", stats.lastKnownName());
            assertEquals(145, stats.totalUses());
            assertEquals(106, stats.nonWaterBlocksPlaced());
            assertEquals(18, stats.waterSourceBlocksPlaced());
            assertEquals(124, stats.totalBlocksPlaced());
            assertEquals(2, stats.printsCompleted());
            assertEquals(125, stats.largestCompletedPrint());
            assertTrue(stats.updatedAtEpochMillis() > 0);
        }
    }

    @Test
    void reportsServerTotalsAcrossUniquePlayers() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        try (BuildStatsStore store = openStore()) {
            store.recordWave(first, "First", 10, 7, 1, true, 20);
            store.recordWave(first, "First", 5, 3, 2, false, 500);
            store.recordWave(second, "Second", 25, 12, 8, true, 30);

            ServerBuildStats totals = store.serverTotals().orElseThrow();
            assertEquals(2, totals.playersTracked());
            assertEquals(40, totals.totalUses());
            assertEquals(22, totals.nonWaterBlocksPlaced());
            assertEquals(11, totals.waterSourceBlocksPlaced());
            assertEquals(33, totals.totalBlocksPlaced());
            assertEquals(2, totals.printsCompleted());
            assertEquals(30, totals.largestCompletedPrint());
        }
    }

    @Test
    void aggregatesSuccessfulPaidRefillsPerPlayerAndAcrossTheServer() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        try (BuildStatsStore store = openStore()) {
            assertTrue(store.recordPaidRefill(first, "First", 100, 5_000));
            assertTrue(store.recordPaidRefill(first, "FirstRenamed", 500, 25_000));
            assertTrue(store.recordPaidRefill(second, "Second", 200, 10_000));

            PlayerBuildStats player = store.findPlayer(first).orElseThrow();
            assertEquals("FirstRenamed", player.lastKnownName());
            assertEquals(0, player.totalUses());
            assertEquals(2, player.refillsCompleted());
            assertEquals(600, player.refillUsesPurchased());
            assertEquals(30_000, player.refillDenariiSpent());

            ServerBuildStats server = store.serverTotals().orElseThrow();
            assertEquals(2, server.playersTracked());
            assertEquals(3, server.refillsCompleted());
            assertEquals(800, server.refillUsesPurchased());
            assertEquals(40_000, server.refillDenariiSpent());

            assertTrue(store.claimRecognitionIfReached(
                    first,
                    BuildStatistic.REFILL_DENARII_SPENT,
                    30_000
            ));
            assertFalse(store.claimRecognitionIfReached(
                    first,
                    BuildStatistic.REFILL_DENARII_SPENT,
                    30_000
            ));
        }
    }

    @Test
    void recognitionIsClaimedOnlyOnceAndOnlyAfterThresholdIsReached() {
        UUID playerId = UUID.randomUUID();

        try (BuildStatsStore store = openStore()) {
            store.recordWave(playerId, "Builder", 40, 20, 5, true, 40);

            assertFalse(store.claimRecognitionIfReached(
                    playerId,
                    BuildStatistic.TOTAL_USES,
                    100
            ));
            assertTrue(store.claimRecognitionIfReached(
                    playerId,
                    BuildStatistic.TOTAL_BLOCKS_PLACED,
                    25
            ));
            assertFalse(store.claimRecognitionIfReached(
                    playerId,
                    BuildStatistic.TOTAL_BLOCKS_PLACED,
                    25
            ));

            store.recordWave(playerId, "Builder", 60, 60, 0, false, 60);
            assertTrue(store.claimRecognitionIfReached(
                    playerId,
                    BuildStatistic.TOTAL_USES,
                    100
            ));
            assertFalse(store.claimRecognitionIfReached(
                    playerId,
                    BuildStatistic.TOTAL_USES,
                    100
            ));
            assertFalse(store.claimRecognitionIfReached(
                    playerId,
                    BuildStatistic.LARGEST_COMPLETED_PRINT,
                    50
            ));
        }
    }

    @Test
    void persistsTotalsAndRecognitionClaimsAcrossReopen() {
        Path database = databasePath();
        UUID playerId = UUID.randomUUID();

        try (BuildStatsStore first = BuildStatsStore.open(database, LOGGER)) {
            assertTrue(first.recordWave(playerId, "Builder", 100, 75, 25, true, 100));
            assertTrue(first.claimRecognitionIfReached(
                    playerId,
                    BuildStatistic.TOTAL_USES,
                    100
            ));
        }

        try (BuildStatsStore reopened = BuildStatsStore.open(database, LOGGER)) {
            assertEquals(100, reopened.findPlayer(playerId).orElseThrow().totalUses());
            assertFalse(reopened.claimRecognitionIfReached(
                    playerId,
                    BuildStatistic.TOTAL_USES,
                    100
            ));
        }
    }

    @Test
    void migratesExistingBuildOnlyDatabaseWithoutLosingTotalsOrRecognitions() throws Exception {
        Path database = databasePath();
        UUID playerId = UUID.randomUUID();
        createLegacyDatabase(database, playerId);

        try (BuildStatsStore migrated = BuildStatsStore.open(database, LOGGER)) {
            assertTrue(migrated.isAvailable());
            PlayerBuildStats beforeRefill = migrated.findPlayer(playerId).orElseThrow();
            assertEquals(40, beforeRefill.totalUses());
            assertEquals(25, beforeRefill.totalBlocksPlaced());
            assertEquals(1, beforeRefill.printsCompleted());
            assertEquals(40, beforeRefill.largestCompletedPrint());
            assertEquals(0, beforeRefill.refillsCompleted());
            assertEquals(0, beforeRefill.refillUsesPurchased());
            assertEquals(0, beforeRefill.refillDenariiSpent());
            assertFalse(migrated.claimRecognitionIfReached(
                    playerId,
                    BuildStatistic.TOTAL_USES,
                    40
            ));
            assertTrue(migrated.recordPaidRefill(playerId, "LegacyRenamed", 125, 6_250));
        }

        try (BuildStatsStore reopened = BuildStatsStore.open(database, LOGGER)) {
            PlayerBuildStats stats = reopened.findPlayer(playerId).orElseThrow();
            assertEquals("LegacyRenamed", stats.lastKnownName());
            assertEquals(40, stats.totalUses());
            assertEquals(1, stats.refillsCompleted());
            assertEquals(125, stats.refillUsesPurchased());
            assertEquals(6_250, stats.refillDenariiSpent());
        }
    }

    @Test
    void availableEmptyStoreReturnsZeroServerTotalsAndNoPlayer() {
        UUID unknown = UUID.randomUUID();

        try (BuildStatsStore store = openStore()) {
            assertTrue(store.isAvailable());
            assertEquals(Optional.empty(), store.findPlayer(unknown));
            assertEquals(new ServerBuildStats(0, 0, 0, 0, 0, 0),
                    store.serverTotals().orElseThrow());
        }
    }

    @Test
    void initializationFailureProducesSafeDisabledStore() throws Exception {
        Path directoryInsteadOfDatabase = tempDirectory.resolve("not-a-database");
        java.nio.file.Files.createDirectories(directoryInsteadOfDatabase);

        BuildStatsStore store = BuildStatsStore.open(directoryInsteadOfDatabase, LOGGER);
        assertFalse(store.isAvailable());
        assertFalse(store.recordWave(
                UUID.randomUUID(),
                "Builder",
                1,
                1,
                0,
                true,
                1
        ));
        assertEquals(Optional.empty(), store.serverTotals());
        store.close();
        store.close();
    }

    @Test
    void rejectsNegativeDeltasWithoutChangingTotals() {
        try (BuildStatsStore store = openStore()) {
            assertFalse(store.recordWave(
                    UUID.randomUUID(),
                    "Builder",
                    new WaveStatsDelta(-1, 0, 0, false, 1)
            ));
            assertFalse(store.recordPaidRefill(UUID.randomUUID(), "Builder", -1, 50));
            assertFalse(store.recordPaidRefill(UUID.randomUUID(), "Builder", 10, -1));
            assertEquals(0, store.serverTotals().orElseThrow().playersTracked());
        }
    }

    private static void createLegacyDatabase(Path database, UUID playerId) throws Exception {
        Files.createDirectories(database.getParent());
        Class.forName("org.sqlite.JDBC");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE player_build_stats (
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
                        updated_at_epoch_millis INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE recognition_claims (
                        player_uuid TEXT NOT NULL,
                        statistic TEXT NOT NULL,
                        threshold INTEGER NOT NULL CHECK (threshold > 0),
                        claimed_at_epoch_millis INTEGER NOT NULL,
                        PRIMARY KEY (player_uuid, statistic, threshold),
                        FOREIGN KEY (player_uuid) REFERENCES player_build_stats(player_uuid)
                                ON DELETE CASCADE
                    )
                    """);
            try (PreparedStatement insertPlayer = connection.prepareStatement("""
                    INSERT INTO player_build_stats VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                insertPlayer.setString(1, playerId.toString());
                insertPlayer.setString(2, "Legacy");
                insertPlayer.setLong(3, 40);
                insertPlayer.setLong(4, 20);
                insertPlayer.setLong(5, 5);
                insertPlayer.setLong(6, 1);
                insertPlayer.setLong(7, 40);
                insertPlayer.setLong(8, 1_000);
                insertPlayer.executeUpdate();
            }
            try (PreparedStatement insertRecognition = connection.prepareStatement("""
                    INSERT INTO recognition_claims VALUES (?, ?, ?, ?)
                    """)) {
                insertRecognition.setString(1, playerId.toString());
                insertRecognition.setString(2, "total_uses");
                insertRecognition.setLong(3, 40);
                insertRecognition.setLong(4, 1_000);
                insertRecognition.executeUpdate();
            }
        }
    }

    private BuildStatsStore openStore() {
        return BuildStatsStore.open(databasePath(), LOGGER);
    }

    private Path databasePath() {
        return tempDirectory.resolve("nested").resolve(BuildStatsStore.DATABASE_FILE_NAME);
    }
}
