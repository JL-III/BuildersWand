package com.playtheatria.buildersWand.prefab;

import com.playtheatria.buildersWand.wand.WandItems;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Runtime catalog, durable ownership, voucher, and player-selection boundary for prefabs.
 * Placement deliberately lives outside this service so it can reuse the wand's common plan path.
 */
public final class PrefabService implements AutoCloseable, Listener {

    public static final String PERMISSION_ALL = "builderswand.prefab.all";
    public static final String PERMISSION_ADMIN = "builderswand.prefab.admin";
    public static final String PERMISSION_PREFIX = "builderswand.prefab.";

    public enum Access {
        ACCESSIBLE,
        LOCKED,
        NOT_FOUND,
        UNAVAILABLE,
        DISABLED
    }

    public enum SelectionStatus {
        SELECTED,
        LOCKED,
        NOT_FOUND,
        NOT_HOLDING_WAND,
        NO_WAND_PERMISSION,
        UNAVAILABLE,
        DISABLED
    }

    public enum GrantOutcome {
        GRANTED,
        ALREADY_UNLOCKED,
        NOT_FOUND,
        UNAVAILABLE,
        DISABLED
    }

    public enum RevokeOutcome {
        REVOKED,
        NOT_UNLOCKED,
        INVALID_ID,
        UNAVAILABLE,
        DISABLED
    }

    public enum VoucherIssueOutcome {
        ISSUED,
        NOT_FOUND,
        UNAVAILABLE,
        DISABLED
    }

    public enum RedeemOutcome {
        REDEEMED,
        NOT_A_VOUCHER,
        NOT_FOUND,
        ALREADY_UNLOCKED,
        UNAVAILABLE,
        DISABLED,
        UNLOCKED_BUT_NOT_CONSUMED
    }

    /** Stable selection binding. A changed catalog hash invalidates it automatically. */
    public record Selection(
            String prefabId,
            long catalogGeneration,
            String contentHash
    ) {
        public Selection {
            prefabId = PrefabId.requireValid(prefabId);
            Objects.requireNonNull(contentHash, "contentHash");
        }
    }

    public record SelectionResult(
            SelectionStatus status,
            Optional<PrefabDefinition> definition
    ) {
        public SelectionResult {
            Objects.requireNonNull(status, "status");
            definition = definition == null ? Optional.empty() : definition;
            if ((status == SelectionStatus.SELECTED) != definition.isPresent()) {
                throw new IllegalArgumentException("Only a selected result carries a prefab definition");
            }
        }
    }

    /** Empty availability means the ownership database is unavailable and access failed closed. */
    public record AccessiblePrefabs(boolean available, List<PrefabDefinition> prefabs) {
        public AccessiblePrefabs {
            prefabs = List.copyOf(prefabs);
            if (!available && !prefabs.isEmpty()) {
                throw new IllegalArgumentException("An unavailable prefab list must be empty");
            }
        }
    }

    public record VoucherIssue(VoucherIssueOutcome outcome, Optional<ItemStack> voucher) {
        public VoucherIssue {
            voucher = voucher == null ? Optional.empty() : voucher.map(ItemStack::clone);
            if ((outcome == VoucherIssueOutcome.ISSUED) != voucher.isPresent()) {
                throw new IllegalArgumentException("Only an issued voucher result carries an item");
            }
        }

        @Override
        public Optional<ItemStack> voucher() {
            return voucher.map(ItemStack::clone);
        }
    }

    /** Non-mutating result for the author-facing {@code validate} command. */
    public record ValidationReport(
            boolean success,
            Optional<PrefabDefinition> definition,
            List<String> issues
    ) {
        public ValidationReport {
            definition = definition == null ? Optional.empty() : definition;
            issues = List.copyOf(issues);
            if (success != definition.isPresent()
                    || (success && !issues.isEmpty())
                    || (!success && issues.isEmpty())) {
                throw new IllegalArgumentException("Prefab validation result is inconsistent");
            }
        }
    }

    private final PrefabRuntimeSettings settings;
    private final WandItems wandItems;
    private final Logger logger;
    private final PrefabMetadataParser metadataParser;
    private final PrefabSchematicParser schematicParser;
    private final PrefabImporter importer;
    private final PrefabCatalog catalog;
    private final PrefabEntitlementStore entitlements;
    private final PrefabVouchers vouchers;
    private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();
    private volatile boolean catalogAvailable;
    private Consumer<Player> selectionChanged = ignored -> { };

    public PrefabService(
            JavaPlugin plugin,
            PrefabRuntimeSettings settings,
            WandItems wandItems
    ) {
        Objects.requireNonNull(plugin, "plugin");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.wandItems = Objects.requireNonNull(wandItems, "wandItems");
        this.logger = plugin.getLogger();
        this.metadataParser = new PrefabMetadataParser();
        this.schematicParser = new SpongeSchematicV3Parser(settings.maxVolume(),
                SpongeSchematicV3Parser.DEFAULT_MAX_COMPRESSED_BYTES,
                SpongeSchematicV3Parser.DEFAULT_MAX_DECOMPRESSED_BYTES);
        this.importer = new PrefabImporter(
                BukkitPrefabBlockPolicy.conservativeDefaults(),
                settings.maxCells(),
                settings.maxVolume(),
                settings.maxAxisSpan(),
                settings.maxClearanceCells(),
                wandItems.configuredMaximumUses()
        );
        this.catalog = new PrefabCatalog(
                schematicParser,
                importer,
                settings.defaultActivationUses()
        );
        Path database = plugin.getDataFolder().toPath()
                .resolve(PrefabEntitlementStore.DATABASE_FILE_NAME);
        this.entitlements = PrefabEntitlementStore.open(database, logger);
        this.vouchers = new PrefabVouchers(plugin);

        if (settings.enabled()) {
            PrefabCatalogReloadResult initial = reload("plugin-enable");
            if (!initial.success()) {
                logger.severe("Prefab catalog did not load; prefab access will fail closed: "
                        + String.join("; ", initial.issues()));
            }
        }
    }

    public PrefabRuntimeSettings settings() {
        return settings;
    }

    public boolean enabled() {
        return settings.enabled();
    }

    /** True only when both catalog and durable ownership are available. */
    public boolean available() {
        return settings.enabled() && catalogAvailable && entitlements.isAvailable();
    }

    public PrefabCatalogSnapshot catalogSnapshot() {
        return catalog.snapshot();
    }

    public PrefabCatalogReloadResult reload() {
        return reload("runtime");
    }

    public PrefabCatalogReloadResult reload(String auditActor) {
        PrefabCatalogSnapshot before = catalog.snapshot();
        if (!settings.enabled()) {
            PrefabCatalogReloadResult disabled = new PrefabCatalogReloadResult(
                    false,
                    before.generation(),
                    before,
                    List.of("Prefabs are disabled in config.yml")
            );
            audit("reload", auditActor, null, "catalog", "DISABLED");
            return disabled;
        }
        try {
            Files.createDirectories(settings.directory());
        } catch (IOException | SecurityException exception) {
            logger.log(Level.SEVERE, "Could not create the prefab catalog directory", exception);
            PrefabCatalogReloadResult failed = new PrefabCatalogReloadResult(
                    false,
                    before.generation(),
                    before,
                    List.of("Cannot create prefab directory: " + safeMessage(exception))
            );
            audit("reload", auditActor, null, "catalog", "DIRECTORY_FAILURE");
            return failed;
        }

        PrefabCatalogReloadResult result = catalog.reload(settings.directory());
        if (result.success()) {
            catalogAvailable = true;
            invalidateSelectionsAfterReload();
            logger.info("Loaded prefab catalog generation "
                    + result.liveSnapshot().generation() + " with "
                    + result.liveSnapshot().prefabs().size() + " design(s).");
        }
        audit("reload", auditActor, null, "catalog",
                result.success() ? "LOADED_GENERATION_" + result.liveSnapshot().generation() : "FAILED");
        return result;
    }

    /** Validates one sidecar and schematic without replacing the live catalog generation. */
    public ValidationReport validate(String requestedId) {
        if (!settings.enabled()) {
            return invalid("Prefabs are disabled in config.yml");
        }
        final String prefabId;
        try {
            prefabId = PrefabId.requireValid(requestedId);
        } catch (IllegalArgumentException exception) {
            return invalid(exception.getMessage());
        }

        try {
            Path directory = settings.directory().toRealPath();
            List<Path> candidates;
            try (Stream<Path> stream = Files.list(directory)) {
                candidates = stream
                        .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .filter(path -> {
                            String name = path.getFileName().toString();
                            return name.endsWith(".yml") || name.endsWith(".yaml");
                        })
                        .sorted()
                        .toList();
            }

            List<PrefabMetadata> matchingMetadata = new ArrayList<>();
            List<Path> matchingPaths = new ArrayList<>();
            List<String> parseIssues = new ArrayList<>();
            for (Path candidate : candidates) {
                try {
                    PrefabMetadata metadata = metadataParser.parse(
                            candidate, settings.defaultActivationUses()
                    );
                    if (metadata.id().equals(prefabId)) {
                        matchingMetadata.add(metadata);
                        matchingPaths.add(candidate);
                    }
                } catch (PrefabValidationException exception) {
                    if (candidate.getFileName().toString().equals(prefabId + ".yml")
                            || candidate.getFileName().toString().equals(prefabId + ".yaml")) {
                        parseIssues.addAll(exception.issues());
                    }
                }
            }
            if (!parseIssues.isEmpty()) {
                return new ValidationReport(false, Optional.empty(), parseIssues);
            }
            if (matchingMetadata.isEmpty()) {
                return invalid("No metadata sidecar declares prefab ID '" + prefabId + "'");
            }
            if (matchingMetadata.size() > 1) {
                return invalid("Multiple metadata sidecars declare prefab ID '" + prefabId + "'");
            }

            PrefabMetadata metadata = matchingMetadata.getFirst();
            Path metadataPath = matchingPaths.getFirst();
            Path schematic = directory.resolve(metadata.schematic()).normalize();
            if (!schematic.startsWith(directory)
                    || !Files.isRegularFile(schematic, LinkOption.NOFOLLOW_LINKS)) {
                return invalid(metadataPath.getFileName() + ": schematic is missing: "
                        + metadata.schematic());
            }
            Path realSchematic = schematic.toRealPath();
            if (!realSchematic.getParent().equals(directory)) {
                return invalid(metadataPath.getFileName()
                        + ": schematic symlinks outside the prefab directory");
            }
            PrefabDefinition definition = importer.importPrefab(
                    metadata,
                    schematicParser.parse(realSchematic)
            );
            PrefabDimensions dimensions = definition.dimensions();
            if (dimensions.width() > settings.maxAxisSpan()
                    || dimensions.height() > settings.maxAxisSpan()
                    || dimensions.depth() > settings.maxAxisSpan()) {
                return invalid("Prefab dimensions " + dimensions.width() + "x"
                        + dimensions.height() + "x" + dimensions.depth()
                        + " exceed the configured " + settings.maxAxisSpan() + "-block axis span");
            }
            return new ValidationReport(true, Optional.of(definition), List.of());
        } catch (PrefabValidationException exception) {
            return new ValidationReport(false, Optional.empty(), exception.issues());
        } catch (IOException | RuntimeException exception) {
            return invalid(safeMessage(exception));
        }
    }

    public Access access(Player player, String prefabId) {
        Objects.requireNonNull(player, "player");
        if (!settings.enabled()) {
            return Access.DISABLED;
        }
        if (!catalogAvailable || !entitlements.isAvailable()) {
            return Access.UNAVAILABLE;
        }
        Optional<PrefabDefinition> definition = catalog.snapshot().find(prefabId);
        if (definition.isEmpty()) {
            return Access.NOT_FOUND;
        }
        PrefabEntitlementStore.Access durable = entitlements.access(player.getUniqueId(), prefabId);
        if (durable == PrefabEntitlementStore.Access.UNAVAILABLE) {
            return Access.UNAVAILABLE;
        }
        if (durable == PrefabEntitlementStore.Access.UNLOCKED
                || player.hasPermission(PERMISSION_ALL)
                || player.hasPermission(PERMISSION_PREFIX + definition.get().id())) {
            return Access.ACCESSIBLE;
        }
        return Access.LOCKED;
    }

    public AccessiblePrefabs accessiblePrefabs(Player player) {
        Objects.requireNonNull(player, "player");
        if (!available()) {
            return new AccessiblePrefabs(false, List.of());
        }
        Optional<Set<String>> durableIds = entitlements.unlockedPrefabIds(player.getUniqueId());
        if (durableIds.isEmpty()) {
            return new AccessiblePrefabs(false, List.of());
        }
        boolean all = player.hasPermission(PERMISSION_ALL);
        List<PrefabDefinition> accessible = new ArrayList<>();
        for (PrefabDefinition definition : catalog.snapshot().prefabs().values()) {
            if (all
                    || durableIds.get().contains(definition.id())
                    || player.hasPermission(PERMISSION_PREFIX + definition.id())) {
                accessible.add(definition);
            }
        }
        return new AccessiblePrefabs(true, accessible);
    }

    public SelectionResult select(Player player, String prefabId) {
        Objects.requireNonNull(player, "player");
        if (!settings.enabled()) {
            return refusedSelection(SelectionStatus.DISABLED);
        }
        if (!player.hasPermission(WandItems.PERMISSION_USE)) {
            return refusedSelection(SelectionStatus.NO_WAND_PERMISSION);
        }
        ItemStack wand = player.getInventory().getItemInOffHand();
        if (!wandItems.isWand(wand) || wand.getAmount() != 1) {
            return refusedSelection(SelectionStatus.NOT_HOLDING_WAND);
        }
        Access access = access(player, prefabId);
        if (access != Access.ACCESSIBLE) {
            return refusedSelection(switch (access) {
                case LOCKED -> SelectionStatus.LOCKED;
                case NOT_FOUND -> SelectionStatus.NOT_FOUND;
                case DISABLED -> SelectionStatus.DISABLED;
                case UNAVAILABLE -> SelectionStatus.UNAVAILABLE;
                case ACCESSIBLE -> throw new IllegalStateException("unreachable");
            });
        }
        PrefabCatalogSnapshot snapshot = catalog.snapshot();
        PrefabDefinition definition = snapshot.find(prefabId).orElseThrow();
        wandItems.ensureFirstWielder(wand, player);
        player.getInventory().setItemInOffHand(wand);
        selections.put(player.getUniqueId(), new Selection(
                definition.id(), snapshot.generation(), definition.contentHash()
        ));
        notifySelectionChanged(player);
        return new SelectionResult(SelectionStatus.SELECTED, Optional.of(definition));
    }

    public Optional<Selection> selection(Player player) {
        if (player == null) {
            return Optional.empty();
        }
        Optional<Selection> selected = selection(player.getUniqueId());
        if (selected.isPresent()
                && access(player, selected.get().prefabId()) != Access.ACCESSIBLE) {
            selections.remove(player.getUniqueId(), selected.get());
            notifySelectionChanged(player);
            return Optional.empty();
        }
        return selected;
    }

    public Optional<Selection> selection(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        Selection selection = selections.get(playerId);
        if (selection == null) {
            return Optional.empty();
        }
        if (definition(selection).isEmpty()) {
            selections.remove(playerId, selection);
            return Optional.empty();
        }
        return Optional.of(selection);
    }

    public Optional<PrefabDefinition> selectedDefinition(Player player) {
        return selection(player).flatMap(this::definition);
    }

    public Optional<PrefabDefinition> selectedDefinition(UUID playerId) {
        return selection(playerId).flatMap(this::definition);
    }

    public boolean clearSelection(Player player) {
        if (player == null || selections.remove(player.getUniqueId()) == null) {
            return false;
        }
        notifySelectionChanged(player);
        return true;
    }

    public void setSelectionChangedListener(Consumer<Player> listener) {
        this.selectionChanged = listener == null ? ignored -> { } : listener;
    }

    public GrantOutcome grant(UUID playerId, String prefabId, String source, String auditActor) {
        if (!settings.enabled()) {
            audit("grant", auditActor, playerId, prefabId, GrantOutcome.DISABLED.name());
            return GrantOutcome.DISABLED;
        }
        if (!catalogAvailable || catalog.snapshot().find(prefabId).isEmpty()) {
            GrantOutcome refused = catalogAvailable
                    ? GrantOutcome.NOT_FOUND
                    : GrantOutcome.UNAVAILABLE;
            audit("grant", auditActor, playerId, prefabId, refused.name());
            return refused;
        }
        PrefabEntitlementStore.GrantResult result = entitlements.grant(playerId, prefabId, source);
        GrantOutcome outcome = switch (result) {
            case GRANTED -> GrantOutcome.GRANTED;
            case ALREADY_UNLOCKED -> GrantOutcome.ALREADY_UNLOCKED;
            case UNAVAILABLE -> GrantOutcome.UNAVAILABLE;
        };
        audit("grant", auditActor, playerId, prefabId, outcome.name());
        return outcome;
    }

    public RevokeOutcome revoke(UUID playerId, String prefabId, String auditActor) {
        if (!settings.enabled()) {
            audit("revoke", auditActor, playerId, prefabId, RevokeOutcome.DISABLED.name());
            return RevokeOutcome.DISABLED;
        }
        final String validId;
        try {
            validId = PrefabId.requireValid(prefabId);
        } catch (IllegalArgumentException exception) {
            audit("revoke", auditActor, playerId, prefabId, RevokeOutcome.INVALID_ID.name());
            return RevokeOutcome.INVALID_ID;
        }
        PrefabEntitlementStore.RevokeResult result = entitlements.revoke(playerId, validId);
        RevokeOutcome outcome = switch (result) {
            case REVOKED -> RevokeOutcome.REVOKED;
            case NOT_UNLOCKED -> RevokeOutcome.NOT_UNLOCKED;
            case UNAVAILABLE -> RevokeOutcome.UNAVAILABLE;
        };
        if (outcome == RevokeOutcome.REVOKED) {
            Selection selected = selections.get(playerId);
            if (selected != null
                    && selected.prefabId().equals(validId)
                    && selections.remove(playerId, selected)) {
                Player online = Bukkit.getPlayer(playerId);
                if (online != null) {
                    notifySelectionChanged(online);
                }
            }
        }
        audit("revoke", auditActor, playerId, validId, outcome.name());
        return outcome;
    }

    /** Creates but does not deliver an audited voucher for one currently loaded prefab. */
    public VoucherIssue createVoucher(
            UUID targetPlayerId,
            String prefabId,
            String auditActor
    ) {
        if (!settings.enabled()) {
            audit("voucher", auditActor, targetPlayerId, prefabId,
                    VoucherIssueOutcome.DISABLED.name());
            return refusedVoucher(VoucherIssueOutcome.DISABLED);
        }
        if (!available()) {
            audit("voucher", auditActor, targetPlayerId, prefabId,
                    VoucherIssueOutcome.UNAVAILABLE.name());
            return refusedVoucher(VoucherIssueOutcome.UNAVAILABLE);
        }
        Optional<PrefabDefinition> definition = catalog.snapshot().find(prefabId);
        if (definition.isEmpty()) {
            audit("voucher", auditActor, targetPlayerId, prefabId,
                    VoucherIssueOutcome.NOT_FOUND.name());
            return refusedVoucher(VoucherIssueOutcome.NOT_FOUND);
        }
        ItemStack voucher = vouchers.create(definition.get());
        audit("voucher", auditActor, targetPlayerId, prefabId, VoucherIssueOutcome.ISSUED.name());
        return new VoucherIssue(VoucherIssueOutcome.ISSUED, Optional.of(voucher));
    }

    /** Redeems the main-hand voucher, consuming it only after the durable grant succeeds. */
    public RedeemOutcome redeem(Player player) {
        Objects.requireNonNull(player, "player");
        if (!settings.enabled()) {
            audit("redeem", player.getName(), player.getUniqueId(), "unknown",
                    RedeemOutcome.DISABLED.name());
            return RedeemOutcome.DISABLED;
        }
        if (!available()) {
            audit("redeem", player.getName(), player.getUniqueId(), "unknown",
                    RedeemOutcome.UNAVAILABLE.name());
            return RedeemOutcome.UNAVAILABLE;
        }
        Optional<String> prefabId = vouchers.prefabId(
                player.getInventory().getItemInMainHand()
        );
        if (prefabId.isEmpty()) {
            audit("redeem", player.getName(), player.getUniqueId(), "unknown",
                    RedeemOutcome.NOT_A_VOUCHER.name());
            return RedeemOutcome.NOT_A_VOUCHER;
        }
        if (catalog.snapshot().find(prefabId.get()).isEmpty()) {
            audit("redeem", player.getName(), player.getUniqueId(), prefabId.get(),
                    RedeemOutcome.NOT_FOUND.name());
            return RedeemOutcome.NOT_FOUND;
        }

        PrefabEntitlementStore.GrantResult grant = entitlements.grant(
                player.getUniqueId(), prefabId.get(), "blueprint-voucher"
        );
        if (grant == PrefabEntitlementStore.GrantResult.UNAVAILABLE) {
            audit("redeem", player.getName(), player.getUniqueId(), prefabId.get(), "UNAVAILABLE");
            return RedeemOutcome.UNAVAILABLE;
        }
        if (grant == PrefabEntitlementStore.GrantResult.ALREADY_UNLOCKED) {
            audit("redeem", player.getName(), player.getUniqueId(), prefabId.get(),
                    "ALREADY_UNLOCKED_NOT_CONSUMED");
            return RedeemOutcome.ALREADY_UNLOCKED;
        }
        if (!vouchers.consumeOneFromMainHand(player, prefabId.get())) {
            audit("redeem", player.getName(), player.getUniqueId(), prefabId.get(),
                    "GRANTED_NOT_CONSUMED");
            logger.severe("Granted prefab " + prefabId.get() + " to " + player.getUniqueId()
                    + " but its main-hand voucher changed before it could be consumed.");
            return RedeemOutcome.UNLOCKED_BUT_NOT_CONSUMED;
        }
        audit("redeem", player.getName(), player.getUniqueId(), prefabId.get(), "REDEEMED");
        return RedeemOutcome.REDEEMED;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearSelection(event.getPlayer());
    }

    @Override
    public void close() {
        selections.clear();
        entitlements.close();
        catalogAvailable = false;
    }

    private Optional<PrefabDefinition> definition(Selection selection) {
        return catalog.snapshot().find(selection.prefabId())
                .filter(definition -> definition.contentHash().equals(selection.contentHash()));
    }

    private void invalidateSelectionsAfterReload() {
        List<UUID> invalid = selections.entrySet().stream()
                .filter(entry -> definition(entry.getValue()).isEmpty())
                .map(Map.Entry::getKey)
                .toList();
        for (UUID playerId : invalid) {
            selections.remove(playerId);
            Player online = Bukkit.getPlayer(playerId);
            if (online != null) {
                notifySelectionChanged(online);
            }
        }
    }

    private void notifySelectionChanged(Player player) {
        try {
            selectionChanged.accept(player);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING,
                    "Could not clear placement state after a prefab selection changed for "
                            + player.getUniqueId(), exception);
        }
    }

    private void audit(
            String operation,
            String actor,
            UUID target,
            String prefabId,
            String result
    ) {
        logger.info("Prefab " + operation
                + " actor=" + safeActor(actor)
                + " target=" + target
                + " prefab=" + safeActor(prefabId)
                + " result=" + safeActor(result));
    }

    private static String safeActor(String actor) {
        return actor == null || actor.isBlank() ? "unknown" : actor.replaceAll("\\s+", "_");
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private static SelectionResult refusedSelection(SelectionStatus status) {
        return new SelectionResult(status, Optional.empty());
    }

    private static VoucherIssue refusedVoucher(VoucherIssueOutcome outcome) {
        return new VoucherIssue(outcome, Optional.empty());
    }

    private static ValidationReport invalid(String issue) {
        return new ValidationReport(false, Optional.empty(), List.of(issue));
    }
}
