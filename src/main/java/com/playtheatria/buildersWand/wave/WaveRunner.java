package com.playtheatria.buildersWand.wave;

import com.playtheatria.buildersWand.config.PluginConfig;
import com.playtheatria.buildersWand.form.Dims;
import com.playtheatria.buildersWand.form.PlanDecision;
import com.playtheatria.buildersWand.form.PlanPolicy;
import com.playtheatria.buildersWand.protect.PlacementLogger;
import com.playtheatria.buildersWand.protect.ProtectionBridge;
import com.playtheatria.buildersWand.stats.BuildStatsStore;
import com.playtheatria.buildersWand.stats.BuildStatistic;
import com.playtheatria.buildersWand.stats.WaveStatsDelta;
import com.playtheatria.buildersWand.utils.Err;
import com.playtheatria.buildersWand.wand.MaterialSelectionSnapshot;
import com.playtheatria.buildersWand.wand.PlacementUseCost;
import com.playtheatria.buildersWand.wand.UseCounter;
import com.playtheatria.buildersWand.wand.WandItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Owns every in-flight {@link Wave} and the single 1-tick runner task (design §9–§11). The
 * task is started lazily while any wave exists and cancelled when none remain.
 */
public final class WaveRunner {

    public enum CommitStatus {
        STARTED,
        QUOTED,
        BLOCKED
    }

    /** Result consumed by ordinary gestures and prefab confirmation. */
    public record CommitResult(CommitStatus status, PlacementQuote quote, String reason,
                               boolean adjustableRefusal) {
        public CommitResult {
            if (status == CommitStatus.QUOTED && quote == null) {
                throw new IllegalArgumentException("a quoted result requires a quote");
            }
            reason = reason == null ? "" : reason;
        }

        static CommitResult started() {
            return new CommitResult(CommitStatus.STARTED, null, "", false);
        }

        static CommitResult quoted(PlacementQuote quote) {
            return new CommitResult(CommitStatus.QUOTED, quote, "", false);
        }

        static CommitResult blocked(String reason) {
            return new CommitResult(CommitStatus.BLOCKED, null, reason, false);
        }

        static CommitResult adjustableRefusal(String reason) {
            return new CommitResult(CommitStatus.BLOCKED, null, reason, true);
        }
    }

    private final JavaPlugin plugin;
    private final ProtectionBridge protection;
    private final PlacementLogger placementLogger;
    private final PluginConfig config;
    private final WandItems wandItems;
    private final BuildStatsStore buildStats;
    private final Map<UUID, Wave> waves = new java.util.HashMap<>();

    private record ResourceEvaluation(Map<Material, Integer> available,
                                      PlacementBudget.Result budget) {
    }

    private BukkitTask task;

    public WaveRunner(JavaPlugin plugin, ProtectionBridge protection, PlacementLogger placementLogger,
                      PluginConfig config, WandItems wandItems, BuildStatsStore buildStats) {
        this.plugin = plugin;
        this.protection = protection;
        this.placementLogger = placementLogger;
        this.config = config;
        this.wandItems = wandItems;
        this.buildStats = buildStats;
    }

    // ---------------------------------------------------------------- commit (design §9)

    /**
     * Runs the §9 pipeline. Resource-short plans are refused without mutation. Prefabs return a
     * full immutable quote only after every printable cell and Use is affordable.
     */
    public CommitResult commit(Player player, Plan plan, PlacementQuote pendingQuote) {
        UUID id = player.getUniqueId();

        // 1. Busy
        Wave existing = waves.get(id);
        if (existing != null) {
            player.sendMessage(red("BLOCKED — A print is already running (" + existing.resolved()
                    + "/" + existing.total() + ")."));
            return CommitResult.blocked("A print is already running.");
        }

        if (!player.hasPermission(WandItems.PERMISSION_USE)) {
            String reason = "BLOCKED — You no longer have permission to use the Builders Wand.";
            player.sendMessage(red(reason));
            return CommitResult.blocked(reason);
        }

        ItemStack wand = player.getInventory().getItemInOffHand();
        if (!wandItems.isWand(wand) || wand.getAmount() != 1) {
            player.sendMessage(red("BLOCKED — Put one unstacked Builders Wand in your offhand."));
            return CommitResult.blocked("The Builders Wand is not ready in your offhand.");
        }
        wandItems.ensureFirstWielder(wand, player);
        player.getInventory().setItemInOffHand(wand);
        boolean usesBypass = player.hasPermission(WandItems.PERMISSION_USES_BYPASS);
        UseCounter.State wandUses = wandItems.uses(wand);

        // 2. Ordinary plans bind the live palette. Authored prefab plans bind their immutable
        //    content hash and per-target feedstock through PlanOptions instead.
        if (plan.options().livePaletteRequired()) {
            WandItems.MaterialSelection liveSelection = wandItems.materialSelection(player);
            Optional<MaterialSelectionSnapshot> selection = liveSelection.snapshot();
            if (selection.isEmpty()) {
                String reason = "BLOCKED — " + liveSelection.problem();
                player.sendMessage(red(reason));
                return CommitResult.blocked(reason);
            }
            if (!selection.get().equals(plan.materialSelection())) {
                String reason = "BLOCKED — Your hotbar palette changed. Restore it or cancel this plan.";
                player.sendMessage(red(reason));
                return CommitResult.blocked(reason);
            }
        }
        if (plan.materialSelection().containsWater() && plan.world().isUltraWarm()) {
            String reason = "BLOCKED — Water evaporates in this world. Nothing changed or spent.";
            player.sendMessage(red(reason));
            return CommitResult.blocked(reason);
        }
        if (!player.getWorld().equals(plan.world())) {
            String reason = "BLOCKED — The print plan belongs to another world.";
            player.sendMessage(red(reason));
            return CommitResult.blocked(reason);
        }
        Location interactionAnchor = new Location(plan.world(),
                plan.interactionAnchor().getBlockX() + 0.5,
                plan.interactionAnchor().getBlockY() + 0.5,
                plan.interactionAnchor().getBlockZ() + 0.5);
        if (player.getEyeLocation().distanceSquared(interactionAnchor)
                > (double) config.anchorReach * config.anchorReach) {
            String reason = "BLOCKED — Return within " + config.anchorReach
                    + " blocks of the original anchor. Nothing changed or spent.";
            player.sendMessage(red(reason));
            return CommitResult.blocked(reason);
        }
        if (plan.options().kind() == PlanOptions.Kind.ORDINARY
                && wandItems.getForm(wand) != plan.form()) {
            String reason = "BLOCKED — The wand mode changed. Cancel and preview the plan again.";
            player.sendMessage(red(reason));
            return CommitResult.blocked(reason);
        }
        if (plan.options().kind() == PlanOptions.Kind.ORDINARY
                && plan.form().supportsDensity()
                && wandItems.getDensity(wand) != plan.density()) {
            String reason = "BLOCKED — Shell/Solid changed. Cancel and preview the plan again.";
            player.sendMessage(red(reason));
            return CommitResult.blocked(reason);
        }

        // 3. Exact geometry legality (same span/cell/chunk policy as the live preview).
        if (plan.options().kind() == PlanOptions.Kind.ORDINARY) {
            List<org.bukkit.util.BlockVector> exactCells = plan.targets().stream()
                    .map(target -> new org.bukkit.util.BlockVector(
                            target.location().getBlockX(), target.location().getBlockY(),
                            target.location().getBlockZ()))
                    .toList();
            PlanDecision decision = new PlanPolicy(config.formLimits).evaluateAbsoluteCells(
                    plan.form(), plan.dims(), plan.density(), exactCells);
            if (!decision.allowed()) {
                String reason = "BLOCKED — " + decision.message() + ". Nothing changed or spent.";
                player.sendMessage(red(reason));
                return CommitResult.adjustableRefusal(reason);
            }
        }
        if (plan.options().requirePlacementLogging() && !placementLogger.available()) {
            String reason = "BLOCKED — Prefab placement logging is unavailable ("
                    + placementLogger.diagnostic() + "). Nothing changed or spent.";
            player.sendMessage(red(reason));
            return CommitResult.blocked(reason);
        }
        for (PlanValidationCell validation : plan.options().validationCells()) {
            String current = plan.world().getBlockAt(validation.location().getBlockX(),
                    validation.location().getBlockY(), validation.location().getBlockZ())
                    .getBlockData().getAsString();
            if (!current.equals(validation.expectedBlockData())) {
                String reason = "BLOCKED — The surface defining this plan changed at "
                        + validation.location().getBlockX() + ", "
                        + validation.location().getBlockY() + ", "
                        + validation.location().getBlockZ()
                        + ". Cancel and anchor it again.";
                player.sendMessage(red(reason));
                return CommitResult.blocked(reason);
            }
        }
        for (PlannedCell target : plan.targets()) {
            Location location = target.location();
            if (location.getBlockY() < plan.world().getMinHeight()
                    || location.getBlockY() >= plan.world().getMaxHeight()) {
                String reason = "BLOCKED — The plan crosses the world's build-height limit at "
                        + coords(location) + ". Nothing changed or spent.";
                player.sendMessage(red(reason));
                return CommitResult.blocked(reason);
            }
            if (!plan.world().getWorldBorder().isInside(location)) {
                String reason = "BLOCKED — The plan crosses the world border at "
                        + coords(location) + ". Nothing changed or spent.";
                player.sendMessage(red(reason));
                return CommitResult.blocked(reason);
            }
        }

        Set<Long> planChunks = new HashSet<>();
        for (PlannedCell target : plan.targets()) {
            planChunks.add(chunkKey(target.location()));
        }
        for (org.bukkit.util.BlockVector clearance : plan.options().clearanceCells()) {
            Location location = new Location(plan.world(), clearance.getBlockX(),
                    clearance.getBlockY(), clearance.getBlockZ());
            if (location.getBlockY() < plan.world().getMinHeight()
                    || location.getBlockY() >= plan.world().getMaxHeight()
                    || !plan.world().getWorldBorder().isInside(location)) {
                String reason = "BLOCKED — Prefab clearance leaves the safe world bounds at "
                        + coords(location) + ". Nothing changed or spent.";
                player.sendMessage(red(reason));
                return CommitResult.blocked(reason);
            }
            planChunks.add(chunkKey(location));
            Optional<String> denier = protection.deniedBy(player, location);
            if (denier.isPresent()) {
                String reason = "BLOCKED — " + denier.get() + " denied required prefab clearance at "
                        + coords(location) + ". Nothing changed or spent.";
                player.sendMessage(red(reason));
                return CommitResult.blocked(reason);
            }
            if (!location.getBlock().getType().isAir()) {
                String reason = "BLOCKED — Required prefab clearance is obstructed at "
                        + coords(location) + ". The wand never clears terrain.";
                player.sendMessage(red(reason));
                return CommitResult.blocked(reason);
            }
        }
        if (plan.options().prefab() && planChunks.size() > config.prefabSettings.maxChunks()) {
            String reason = "BLOCKED — This prefab touches " + planChunks.size()
                    + " chunks; max " + config.prefabSettings.maxChunks() + ".";
            player.sendMessage(red(reason));
            return CommitResult.blocked(reason);
        }

        // 4. Classify printable vs kept. Ordinary primitives preserve their established behavior:
        //    exact matches and occupied cells are kept. Prefabs may opt into strict conflicts in
        //    their own plan policy.
        List<PlannedCell> printable = new ArrayList<>();
        int kept = 0;
        for (PlannedCell target : plan.targets()) {
            Block block = target.location().getBlock();
            if (PlacementRules.isAlreadyBuilt(block, target.blockData())) {
                kept++;
            } else if (block.isReplaceable()) {
                printable.add(target);
            } else if (plan.options().strictExistingStates()) {
                String reason = "BLOCKED — A different block or block state conflicts with "
                        + plan.options().label() + " at " + coords(target.location())
                        + ". Nothing changed or spent.";
                player.sendMessage(red(reason));
                return CommitResult.blocked(reason);
            } else {
                kept++;
            }
        }

        // The operation cap applies only to cells that would actually mutate. Kept terrain costs
        // no block, Use, ghost, or wave work and must not make an otherwise small repair illegal.
        if (plan.options().kind() == PlanOptions.Kind.ORDINARY) {
            PlanDecision placementDecision = new PlanPolicy(config.formLimits)
                    .evaluatePlacementCount(plan.form(), printable.size());
            if (!placementDecision.allowed()) {
                String reason = "BLOCKED — " + placementDecision.message()
                        + ". Aim closer to reduce the shape; nothing changed or spent.";
                player.sendMessage(red(reason));
                return CommitResult.adjustableRefusal(reason);
            }
        }

        // 5. Protection, only for cells that could change.
        for (PlannedCell target : printable) {
            Location loc = target.location();
            Optional<String> denier = protection.deniedBy(player, loc);
            if (denier.isPresent()) {
                String reason = "BLOCKED — " + denier.get() + " denied this plan at "
                        + coords(loc) + ". Nothing changed or spent.";
                player.sendMessage(red(reason));
                return CommitResult.blocked(reason);
            }
        }

        if (plan.options().refuseLivingEntitiesAtStart()) {
            for (PlannedCell target : printable) {
                if (LivingBodyCollision.blocks(target.material(), plan.world(), target.location())) {
                    String reason = "BLOCKED — A living entity occupies the prefab at "
                            + coords(target.location()) + ". Clear the area and confirm again.";
                    player.sendMessage(red(reason));
                    return CommitResult.blocked(reason);
                }
            }
        }

        // 6. All kept
        if (printable.isEmpty()) {
            String reason = plan.options().prefab()
                    ? "BLOCKED — This prefab is already complete at that anchor. Nothing to print."
                    : "BLOCKED — Every target cell is already built or occupied. Nothing to print.";
            player.sendMessage(red(reason));
            return CommitResult.blocked(reason);
        }

        // 7. Simulate the complete material and Use budget in stable plan order. Per-cell
        // decisions remain useful to the preview, but no affordable subset is ever admitted.
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        ResourceEvaluation resources = evaluateResources(player, plan, printable, wandUses,
                creative, usesBypass);
        if (!resources.budget().fullyAffordable()) {
            String reason = blockedShortage(plan, resources.budget(), wandUses.remaining(),
                    usesBypass);
            player.sendMessage(red(reason));
            return CommitResult.adjustableRefusal(reason);
        }
        PlacementQuote quote = quote(plan, printable, kept, wand, wandUses, creative, usesBypass,
                resources);

        // Only prefabs require confirmation. A changed confirmation never places a different plan;
        // it is replaced with a new fully affordable quote.
        if (plan.options().alwaysConfirm()) {
            boolean quoteTimedOut = pendingQuote != null && pendingQuote.expiredAt(
                    System.nanoTime(), plan.options().confirmationSeconds());
            if (pendingQuote != null && (quoteTimedOut || !pendingQuote.sameBinding(quote))) {
                String expiryReason = quoteTimedOut
                        ? "its confirmation time elapsed"
                        : "resources, wand state, or the target changed";
                player.sendMessage(red("BLOCKED — The previous quote expired because "
                        + expiryReason + ". Nothing was placed."));
                return CommitResult.quoted(quote);
            }
            if (pendingQuote == null) {
                return CommitResult.quoted(quote);
            }
        }

        return startWave(player, quote, wand, wandUses);
    }

    private CommitResult startWave(Player player, PlacementQuote quote, ItemStack wand,
                                   UseCounter.State wandUses) {
        if (!quote.budget().fullyAffordable()) {
            throw new IllegalArgumentException("cannot start a resource-short placement quote");
        }
        UUID id = player.getUniqueId();
        List<PlannedCell> printable = quote.printable();
        int need = printable.size();

        // Bind this wave to the exact held item only after every refusal check has passed.
        // The token is rotated here so cloned kit templates cannot share a permanent identity.
        wandItems.ensureFirstWielder(wand, player);
        String wandToken = wandItems.rotateActiveToken(wand);
        player.getInventory().setItemInOffHand(wand);
        wandUses = wandItems.uses(wand);

        int activationUses = quote.plan().options().activationUses();
        WandItems.UseReceipt activationReceipt = null;
        if (activationUses > 0) {
            Optional<WandItems.UseReceipt> spentActivation = wandItems.spendUses(
                    wand, wandToken, activationUses, !quote.usesBypass());
            if (spentActivation.isEmpty()) {
                String reason = "BLOCKED — The prefab activation Uses changed before placement. "
                        + "Nothing was placed.";
                player.sendMessage(red(reason));
                return CommitResult.blocked(reason);
            }
            activationReceipt = spentActivation.get();
            player.getInventory().setItemInOffHand(wand);
            wandUses = wandItems.uses(wand);
        }

        // 9. Start the wave: chunk tickets over the plan, register, schedule
        Set<Chunk> tickets = new HashSet<>();
        try {
            for (PlannedCell target : printable) {
                Location loc = target.location();
                Chunk chunk = loc.getChunk();
                if (tickets.add(chunk)) {
                    chunk.addPluginChunkTicket(plugin);
                }
            }
        } catch (RuntimeException error) {
            rollbackFailedStart(player, wand, wandToken, activationReceipt, tickets);
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not start a Builders Wand wave", error);
            String reason = "BLOCKED — The print could not start safely. Nothing was placed.";
            player.sendMessage(red(reason));
            return CommitResult.blocked(reason);
        }
        int ticksPerCell = need <= config.smallPrintMaxCells
                ? config.smallPrintTicksPerCell
                : config.largePrintTicksPerCell;
        try {
            waves.put(id, new Wave(id, player.getName(), quote.plan().world(),
                    quote.plan().materialSelection(), quote.plan().options(), printable,
                    ticksPerCell, tickets, quote.creative(), quote.usesBypass(), wandToken,
                    activationReceipt, activationUses));
            ensureTask();
        } catch (RuntimeException error) {
            waves.remove(id);
            rollbackFailedStart(player, wand, wandToken, activationReceipt, tickets);
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not register a Builders Wand wave", error);
            String reason = "BLOCKED — The print could not start safely. Nothing was placed.";
            player.sendMessage(red(reason));
            return CommitResult.blocked(reason);
        }

        String usesText = quote.usesBypass() ? "Uses bypassed" : wandUses.remaining() + " Uses available";
        Component startMessage = Component.text("READY — Printing "
                + quote.plan().options().label() + ": " + need + " cells ("
                + quote.kept() + " kept); " + usesText + ".", NamedTextColor.GREEN);
        long waterCells = printable.stream().filter(target -> target.material().isWater()).count();
        if (waterCells > 0) {
            long plannedUses = PlacementUseCost.totalUses((int) waterCells, config.waterUsesPerSource);
            String notice = quote.usesBypass()
                    ? " Water placement normally costs " + config.waterUsesPerSource + " Uses per source ("
                            + "up to " + plannedUses + " Uses for this print); your Uses are bypassed."
                    : " Water placement costs " + config.waterUsesPerSource + " Uses per source ("
                            + "up to " + plannedUses + " Uses for this print).";
            startMessage = startMessage.append(Component.text(notice, NamedTextColor.YELLOW));
        }
        player.sendMessage(startMessage);
        return CommitResult.started();
    }

    private void rollbackFailedStart(Player player, ItemStack wand, String wandToken,
                                     WandItems.UseReceipt activationReceipt,
                                     Set<Chunk> tickets) {
        for (Chunk chunk : tickets) {
            try {
                chunk.removePluginChunkTicket(plugin);
            } catch (RuntimeException error) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Could not remove a chunk ticket after a failed wave start", error);
            }
        }
        if (activationReceipt == null) {
            return;
        }
        try {
            if (wandItems.restoreUse(wand, wandToken, activationReceipt)) {
                player.getInventory().setItemInOffHand(wand);
            } else {
                plugin.getLogger().severe("Could not restore prefab activation Uses after a failed "
                        + "wave start for " + player.getUniqueId());
            }
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not restore prefab activation Uses after a failed wave start for "
                            + player.getUniqueId(), error);
        }
    }

    /** Whether this player has a wave printing right now (ghosts are suppressed while so). */
    public boolean hasActiveWave(Player player) {
        return waves.containsKey(player.getUniqueId());
    }

    /** Read-only hard preflight shared by live ghost status and the final commit path. */
    public Optional<String> previewHardFailure(Player player, Plan plan) {
        if (!player.getWorld().equals(plan.world())) {
            return Optional.of("the plan belongs to another world");
        }
        Location anchor = new Location(plan.world(), plan.interactionAnchor().getBlockX() + 0.5,
                plan.interactionAnchor().getBlockY() + 0.5,
                plan.interactionAnchor().getBlockZ() + 0.5);
        if (player.getEyeLocation().distanceSquared(anchor)
                > (double) config.anchorReach * config.anchorReach) {
            return Optional.of("return within " + config.anchorReach + " blocks of the anchor");
        }
        if (plan.options().kind() == PlanOptions.Kind.ORDINARY) {
            List<org.bukkit.util.BlockVector> exact = plan.targets().stream()
                    .map(target -> new org.bukkit.util.BlockVector(target.location().getBlockX(),
                            target.location().getBlockY(), target.location().getBlockZ()))
                    .toList();
            PlanDecision decision = new PlanPolicy(config.formLimits).evaluateAbsoluteCells(
                    plan.form(), plan.dims(), plan.density(), exact);
            if (!decision.allowed()) {
                return Optional.of(decision.message());
            }
        }
        if (plan.options().requirePlacementLogging() && !placementLogger.available()) {
            return Optional.of("placement logging is unavailable: " + placementLogger.diagnostic());
        }
        for (PlanValidationCell validation : plan.options().validationCells()) {
            String current = plan.world().getBlockAt(validation.location().getBlockX(),
                    validation.location().getBlockY(), validation.location().getBlockZ())
                    .getBlockData().getAsString();
            if (!current.equals(validation.expectedBlockData())) {
                return Optional.of("the source surface changed; cancel and anchor it again");
            }
        }
        Set<Long> chunks = new HashSet<>();
        List<PlannedCell> printableTargets = new ArrayList<>();
        for (PlannedCell target : plan.targets()) {
            Location location = target.location();
            if (location.getBlockY() < plan.world().getMinHeight()
                    || location.getBlockY() >= plan.world().getMaxHeight()) {
                return Optional.of("the plan crosses build height at " + coords(location));
            }
            if (!plan.world().getWorldBorder().isInside(location)) {
                return Optional.of("the plan crosses the world border at " + coords(location));
            }
            chunks.add(chunkKey(location));
            Block block = location.getBlock();
            boolean alreadyBuilt = PlacementRules.isAlreadyBuilt(block, target.blockData());
            if (plan.options().strictExistingStates()
                    && !alreadyBuilt && !block.isReplaceable()) {
                return Optional.of("a different block conflicts at " + coords(location));
            }
            boolean isPrintable = !alreadyBuilt && block.isReplaceable();
            if (!isPrintable) {
                continue;
            }
            printableTargets.add(target);
        }
        if (plan.options().kind() == PlanOptions.Kind.ORDINARY) {
            PlanDecision placementDecision = new PlanPolicy(config.formLimits)
                    .evaluatePlacementCount(plan.form(), printableTargets.size());
            if (!placementDecision.allowed()) {
                return Optional.of(placementDecision.message());
            }
        }
        // Protection and entity queries are intentionally after the cheap mutation cap. An
        // oversized ordinary plan is already inadmissible and must not fan out into thousands of
        // external region checks every time its preview refreshes.
        for (PlannedCell target : printableTargets) {
            Location location = target.location();
            Optional<String> denier = protection.deniedBy(player, location);
            if (denier.isPresent()) {
                return Optional.of(denier.get() + " denies " + coords(location));
            }
            if (plan.options().refuseLivingEntitiesAtStart()
                    && LivingBodyCollision.blocks(target.material(), plan.world(), location)) {
                return Optional.of("a living entity occupies " + coords(location));
            }
        }
        for (org.bukkit.util.BlockVector cell : plan.options().clearanceCells()) {
            Location location = new Location(plan.world(), cell.getBlockX(), cell.getBlockY(),
                    cell.getBlockZ());
            if (location.getBlockY() < plan.world().getMinHeight()
                    || location.getBlockY() >= plan.world().getMaxHeight()
                    || !plan.world().getWorldBorder().isInside(location)) {
                return Optional.of("required clearance leaves safe world bounds at "
                        + coords(location));
            }
            Optional<String> denier = protection.deniedBy(player, location);
            if (denier.isPresent()) {
                return Optional.of(denier.get() + " denies required clearance at "
                        + coords(location));
            }
            if (!location.getBlock().getType().isAir()) {
                return Optional.of("required clearance is obstructed at " + coords(location));
            }
            chunks.add(chunkKey(location));
        }
        if (plan.options().prefab() && chunks.size() > config.prefabSettings.maxChunks()) {
            return Optional.of("the prefab touches " + chunks.size() + " chunks; max "
                    + config.prefabSettings.maxChunks());
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- tick (design §10.3)

    private void tick() {
        for (Wave wave : new ArrayList<>(waves.values())) {
            if (waves.get(wave.owner) != wave) {
                continue; // already settled/stopped this tick
            }
            if (--wave.ticksUntilNext > 0) {
                continue;
            }
            wave.ticksUntilNext = wave.ticksPerCell;
            try {
                step(wave);
            } catch (RuntimeException error) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Unhandled Builders Wand wave failure for " + wave.owner, error);
                if (waves.get(wave.owner) == wave) {
                    stop(wave, Bukkit.getPlayer(wave.owner), StopReason.INTERNAL_ERROR, null);
                }
            }
        }
        stopTaskIfIdle();
    }

    private void step(Wave wave) {
        Player player = Bukkit.getPlayer(wave.owner);
        if (player == null) {
            // Quit should have stopped the wave already; be defensive.
            stopFor(wave.owner, StopReason.PLAYER_QUIT, null);
            return;
        }

        PlannedCell target = wave.placements.current();
        Location loc = target.location();
        Block block = loc.getBlock();
        int usesPerCell = PlacementUseCost.perCell(target.material(), config.waterUsesPerSource);

        // 1. Protection re-check (stops before spending — nothing is lost)
        if (!player.hasPermission(WandItems.PERMISSION_USE)) {
            stop(wave, player, StopReason.PERMISSION_LOST, loc);
            return;
        }
        if (wave.options.requirePlacementLogging() && !placementLogger.available()) {
            plugin.getLogger().severe("Required placement logging became unavailable before "
                    + coords(loc) + ": " + placementLogger.diagnostic());
            stop(wave, player, StopReason.INTERNAL_ERROR, null);
            return;
        }
        if (!protection.canBuild(player, loc)) {
            stop(wave, player, StopReason.PERMISSION_LOST, loc);
            return;
        }
        ItemStack activeWand = player.getInventory().getItemInOffHand();
        if (!wandItems.hasActiveToken(activeWand, wave.wandToken)) {
            stop(wave, player, StopReason.WAND_REMOVED, null);
            return;
        }
        if (wave.options.livePaletteRequired() && wandItems.materialSelection(player).snapshot()
                .filter(wave.materialSelection::equals).isEmpty()) {
            stop(wave, player, StopReason.PALETTE_CHANGED, null);
            return;
        }
        if (!wave.usesBypass && wandItems.uses(activeWand).remaining() < usesPerCell) {
            stop(wave, player, StopReason.OUT_OF_USES, null);
            return;
        }
        if (target.material().reusable()
                && Feedstock.countAll(player.getInventory(), target.material().sourceItem(), wandItems) == 0) {
            stop(wave, player, StopReason.WATER_BUCKET_REMOVED, null);
            return;
        }
        // A prior placement can turn a later planned water cell into a source through vanilla
        // infinite-source physics. It completed as part of the quoted plan, so retain the planned
        // Use charge while avoiding a duplicate placement, log entry, or sound.
        if (target.material().isWater() && PlacementRules.isAlreadyBuilt(block, target.blockData())) {
            Optional<WandItems.UseReceipt> use = spendUses(wave, player, activeWand, usesPerCell);
            if (use.isEmpty()) {
                stop(wave, player, StopReason.OUT_OF_USES, null);
                return;
            }
            completeCell(wave, player, target, usesPerCell, false);
            return;
        }
        // 2. No longer replaceable
        if (!block.isReplaceable()) {
            stop(wave, player, StopReason.BLOCK_IN_WAY, loc);
            return;
        }
        // 3. A living body gets one deferred retry after the primary pass. If it still occupies
        //    the cell then, leave a gap. Neither outcome spends a resource or moves the entity.
        if (LivingBodyCollision.blocks(target.material(), wave.world, loc)) {
            DeferredPlacementQueue.ObstructionResult result = wave.placements.obstructCurrent();
            if (result == DeferredPlacementQueue.ObstructionResult.DEFERRED
                    && wave.placements.deferred() == 1) {
                player.sendMessage(Component.text(
                        "A living body blocked a cell. Occupied cells will be retried once at the end.",
                        NamedTextColor.YELLOW));
            }
            settleIfResolved(wave, player);
            return;
        }
        // Capture the world rollback point before either resource is spent.
        BlockState before = block.getState();

        // 4. Precheck both resources, then mutate them synchronously. Uses are written first
        //    because it has an exact-token rollback; an ordinary material is only removed after
        //    the precheck makes that removal deterministic on the server thread.
        boolean spendsFeedstock = !wave.creative && !target.material().reusable();
        if (spendsFeedstock
                && Feedstock.countConsumable(
                        player.getInventory(), target.material().sourceItem(), wandItems) == 0) {
            stop(wave, player, StopReason.OUT_OF_MATERIAL, null);
            return;
        }
        Optional<WandItems.UseReceipt> spentUse = spendUses(wave, player, activeWand, usesPerCell);
        if (spentUse.isEmpty()) {
            stop(wave, player, StopReason.OUT_OF_USES, null);
            return;
        }
        Feedstock.Receipt feedstockReceipt = null;
        if (spendsFeedstock) {
            Optional<Feedstock.Receipt> spent = Feedstock.spendOne(
                    player.getInventory(), target.material().sourceItem(), wandItems);
            if (spent.isEmpty()) {
                restoreUse(wave, player, spentUse.get());
                stop(wave, player, StopReason.OUT_OF_MATERIAL, null);
                return;
            }
            feedstockReceipt = spent.get();
        }

        // 5. Place with physics, then require the exact authored state to have survived immediate
        //    physics. A thrown mutation may still have completed, so both paths share the same
        //    postcondition-aware rollback handler.
        try {
            block.setBlockData(target.blockData(), true);
            if (!PlacementRules.isAlreadyBuilt(block, target.blockData())) {
                throw new IllegalStateException(
                        "The server did not retain the exact requested block state");
            }
        } catch (RuntimeException error) {
            handlePlacementFailure(wave, player, target, block, before, usesPerCell,
                    spentUse.get(), feedstockReceipt, error);
            return;
        }
        try {
            if (target.material().isWater()) {
                wave.world.playSound(loc, Sound.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 1.0f, 1.0f);
            } else {
                wave.world.playSound(loc, block.getBlockSoundGroup().getPlaceSound(), 1.0f, 1.0f);
            }
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Builders Wand placed a block but could not play its sound at " + coords(loc), error);
        }
        try {
            placementLogger.logPlacement(player, before, block.getState());
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Builders Wand placed a block but could not log it at " + coords(loc), error);
            completeCell(wave, player, target, usesPerCell, true);
            if (waves.get(wave.owner) == wave) {
                stop(wave, player, StopReason.INTERNAL_ERROR, null);
            }
            return;
        }
        // 6. Account
        completeCell(wave, player, target, usesPerCell, true);
    }

    private void handlePlacementFailure(Wave wave, Player player, PlannedCell target,
                                        Block block, BlockState before, int usesPerCell,
                                        WandItems.UseReceipt useReceipt,
                                        Feedstock.Receipt feedstockReceipt,
                                        RuntimeException error) {
        boolean targetPresent = false;
        try {
            targetPresent = PlacementRules.isAlreadyBuilt(block, target.blockData());
        } catch (RuntimeException inspectionError) {
            error.addSuppressed(inspectionError);
        }
        plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "Builders Wand placement failed at " + coords(block.getLocation()), error);
        if (targetPresent) {
            try {
                placementLogger.logPlacement(player, before, block.getState());
            } catch (RuntimeException logError) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Builders Wand could not log a placement that completed while throwing at "
                                + coords(block.getLocation()), logError);
            }
            completeCell(wave, player, target, usesPerCell, true);
            if (waves.get(wave.owner) == wave) {
                stop(wave, player, StopReason.INTERNAL_ERROR, null);
            }
            return;
        }
        try {
            if (!before.update(true, false)) {
                plugin.getLogger().severe("Could not restore the prior block state at "
                        + coords(block.getLocation()));
            }
        } catch (RuntimeException restoreError) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Exception restoring the prior block state at " + coords(block.getLocation()), restoreError);
        }
        restoreUse(wave, player, useReceipt);
        if (feedstockReceipt != null) {
            ItemStack leftover = Feedstock.restoreOne(player.getInventory(), feedstockReceipt);
            if (leftover != null) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                plugin.getLogger().warning("Restored a failed Builders Wand placement by dropping feedstock for "
                        + player.getUniqueId());
            }
        }
        stop(wave, player, StopReason.INTERNAL_ERROR, null);
    }

    private Optional<WandItems.UseReceipt> spendUses(Wave wave, Player player, ItemStack activeWand,
                                                     int usesPerCell) {
        Optional<WandItems.UseReceipt> receipt = wandItems.spendUses(
                activeWand, wave.wandToken, usesPerCell, !wave.usesBypass);
        if (receipt.isEmpty()) {
            return Optional.empty();
        }
        player.getInventory().setItemInOffHand(activeWand);
        return receipt;
    }

    private void restoreUse(Wave wave, Player player, WandItems.UseReceipt receipt) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (wandItems.restoreUse(offhand, wave.wandToken, receipt)) {
            player.getInventory().setItemInOffHand(offhand);
            return;
        }
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            if (wandItems.restoreUse(storage[slot], wave.wandToken, receipt)) {
                player.getInventory().setStorageContents(storage);
                return;
            }
        }
        plugin.getLogger().severe("Could not restore wand uses after a failed resource or placement update for "
                + player.getUniqueId());
    }

    private void completeCell(Wave wave, Player player, PlannedCell target, int usesPerCell,
                              boolean actuallyPlaced) {
        wave.usesSpent += usesPerCell;
        if (actuallyPlaced) {
            if (target.material().isWater()) {
                wave.actualWaterCells++;
            } else {
                wave.actualBlocksPlaced++;
            }
        }
        wave.placements.completeCurrent();
        settleIfResolved(wave, player);
    }

    private void settleIfResolved(Wave wave, Player player) {
        if (wave.placements.isComplete()) {
            settle(wave, player);
        }
    }

    // ---------------------------------------------------------------- stop / settle

    /** Stop this player's wave (design §10.3); unspent feedstock stays in the inventory. */
    public void stopFor(Player player, StopReason reason) {
        Wave wave = waves.get(player.getUniqueId());
        if (wave != null) {
            stop(wave, player, reason, null);
        }
    }

    /** Stop every wave (plugin disable / server stop, design §10.5). */
    public void stopAll(StopReason reason) {
        for (Wave wave : new ArrayList<>(waves.values())) {
            stop(wave, Bukkit.getPlayer(wave.owner), reason, null);
        }
    }

    private void stopFor(UUID owner, StopReason reason, Location at) {
        Wave wave = waves.get(owner);
        if (wave != null) {
            stop(wave, Bukkit.getPlayer(owner), reason, at);
        }
    }

    private void stop(Wave wave, Player player, StopReason reason, Location at) {
        // No refund: feedstock is spent per cell, so unspent items are still in the inventory.
        if (reason == StopReason.INTERNAL_ERROR && wave.successfulCells() == 0
                && wave.activationReceipt != null && player != null) {
            restoreUse(wave, player, wave.activationReceipt);
            wave.usesSpent = Math.max(0L, wave.usesSpent - wave.activationUses);
        }
        recordStatistics(wave, false, player != null
                && reason != StopReason.SERVER_STOPPING && reason != StopReason.PLAYER_QUIT);
        releaseTickets(wave);
        waves.remove(wave.owner);
        if (player != null) {
            player.sendMessage(Component.text("BLOCKED — " + wave.successfulCells() + "/" + wave.total()
                    + " planned cells completed; " + reason.phrase(at) + ".", NamedTextColor.RED));
        }
        stopTaskIfIdle();
    }

    private void settle(Wave wave, Player player) {
        int skipped = wave.skippedOccupied();
        boolean fullyCompleted = skipped == 0;
        recordStatistics(wave, fullyCompleted, player != null);
        releaseTickets(wave);
        waves.remove(wave.owner);
        if (player != null) {
            if (!fullyCompleted) {
                String noun = skipped == 1 ? "cell was" : "cells were";
                player.sendMessage(Component.text("Completed " + wave.successfulCells() + "/" + wave.total()
                        + " cells; " + skipped + " still-occupied " + noun
                        + " skipped. Reprint after the area clears to fill the gaps.", NamedTextColor.YELLOW));
            } else {
                String message;
                if (wave.options.prefab()) {
                    message = "Printed " + wave.options.label() + " (" + wave.total() + " cells).";
                } else if (wave.materialSelection.singleMaterial()) {
                    message = "Printed " + wave.total() + " "
                            + WandItems.materialDisplayName(
                                    wave.materialSelection.entries().getFirst().placedBlock())
                            + ".";
                } else {
                    message = "Printed " + wave.total() + " palette blocks.";
                }
                player.sendActionBar(Component.text("COMPLETE — " + message, NamedTextColor.GREEN));
            }
        }
        stopTaskIfIdle();
    }

    private void recordStatistics(Wave wave, boolean completedPrint, boolean mayAnnounce) {
        if (wave.statisticsRecorded) {
            return;
        }
        wave.statisticsRecorded = true;
        boolean persisted;
        try {
            persisted = buildStats.recordWave(wave.owner, wave.ownerName, new WaveStatsDelta(
                    wave.usesSpent,
                    wave.actualBlocksPlaced,
                    wave.actualWaterCells,
                    completedPrint,
                    wave.total()));
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Unexpected failure recording Builders Wand build statistics for " + wave.owner, error);
            return;
        }
        if (!persisted || !mayAnnounce || !config.recognitionAnnouncements) {
            return;
        }
        for (long milestone : config.totalBlockMilestones) {
            boolean newlyReached;
            try {
                newlyReached = buildStats.claimRecognitionIfReached(
                        wave.owner, BuildStatistic.TOTAL_BLOCKS_PLACED, milestone);
            } catch (RuntimeException error) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Unexpected failure checking Builders Wand recognition for " + wave.owner, error);
                break;
            }
            if (newlyReached) {
                Bukkit.broadcast(Component.text("✦ Master Builder ✦ ", NamedTextColor.GOLD)
                        .append(Component.text(wave.ownerName, NamedTextColor.LIGHT_PURPLE))
                        .append(Component.text(" has materialized "
                                + String.format(Locale.US, "%,d", milestone)
                                + " blocks with the Builders Wand!", NamedTextColor.GOLD)));
            }
        }
    }

    private void releaseTickets(Wave wave) {
        for (Chunk chunk : wave.tickets) {
            chunk.removePluginChunkTicket(plugin);
        }
    }

    // ---------------------------------------------------------------- task lifecycle

    private void ensureTask() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    private void stopTaskIfIdle() {
        if (waves.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    // ---------------------------------------------------------------- helpers

    private ResourceEvaluation evaluateResources(Player player, Plan plan,
                                                 List<PlannedCell> printable,
                                                 UseCounter.State wandUses,
                                                 boolean creative, boolean usesBypass) {
        Map<Material, Integer> available = availableMaterials(player, printable);
        List<PlacementBudget.Cost> costs = printable.stream().map(this::costOf).toList();
        int activationUses = plan.options().activationUses();
        int cellUseBalance = remainingUsesForCells(
                wandUses.remaining(), activationUses, usesBypass);
        PlacementBudget.Result budget = PlacementBudget.evaluate(costs, available,
                cellUseBalance, creative, usesBypass);
        return new ResourceEvaluation(available, budget);
    }

    private PlacementQuote quote(Plan plan, List<PlannedCell> printable, int kept,
                                 ItemStack wand, UseCounter.State wandUses,
                                 boolean creative, boolean usesBypass,
                                 ResourceEvaluation resources) {
        List<String> worldStates = new ArrayList<>(
                plan.targets().size() + plan.options().clearanceCells().size());
        plan.targets().stream()
                .map(target -> target.location().getBlock().getBlockData().getAsString())
                .forEach(worldStates::add);
        plan.options().clearanceCells().stream()
                .map(cell -> plan.world().getBlockAt(cell.getBlockX(), cell.getBlockY(), cell.getBlockZ())
                        .getBlockData().getAsString())
                .forEach(worldStates::add);
        plan.options().validationCells().stream()
                .map(cell -> plan.world().getBlockAt(cell.location().getBlockX(),
                        cell.location().getBlockY(), cell.location().getBlockZ())
                        .getBlockData().getAsString())
                .forEach(worldStates::add);
        return new PlacementQuote(plan, printable, kept, resources.available(), resources.budget(),
                wandUses.remaining(), wandUses.maximum(), creative, usesBypass,
                wandItems.identity(wand).wandId(), wandItems.getRotation(wand), worldStates,
                System.nanoTime());
    }

    /**
     * Reserves a prefab's fixed invocation cost before the stable per-cell budget is evaluated.
     * The Uses bypass keeps the displayed PDC balance intact while allowing every cell through
     * the budget, matching the later no-debit activation receipt.
     */
    public static int remainingUsesForCells(int remainingUses, int activationUses,
                                            boolean usesBypass) {
        if (remainingUses < 0 || activationUses < 0) {
            throw new IllegalArgumentException("Uses cannot be negative");
        }
        return usesBypass ? remainingUses : Math.max(0, remainingUses - activationUses);
    }

    private static String blockedShortage(Plan plan, PlacementBudget.Result budget,
                                          int remainingUses, boolean usesBypass) {
        long requiredUses = Math.addExact(budget.requiredUses(),
                plan.options().activationUses());
        long missingUses = usesBypass ? 0L : Math.max(0L, requiredUses - remainingUses);
        return "BLOCKED — The complete print is not in stock: "
                + shortageSummary(budget.missingMaterials(), missingUses)
                + ". Nothing changed or spent.";
    }

    private Map<Material, Integer> availableMaterials(Player player, List<PlannedCell> targets) {
        Map<Material, Integer> available = new LinkedHashMap<>();
        for (PlannedCell target : targets) {
            available.computeIfAbsent(target.material().sourceItem(), material ->
                    Feedstock.available(player.getInventory(), target.material(), wandItems));
        }
        return available;
    }

    private PlacementBudget.Cost costOf(PlannedCell target) {
        return new PlacementBudget.Cost(target.material().sourceItem(),
                !target.material().reusable(),
                PlacementUseCost.perCell(target.material(), config.waterUsesPerSource));
    }

    private static String missingMaterialsSummary(Map<Material, Integer> missing) {
        StringJoiner summary = new StringJoiner(", ");
        missing.forEach((material, count) -> summary.add(count + " "
                + WandItems.materialDisplayName(material)));
        return summary.toString();
    }

    private static String shortageSummary(Map<Material, Integer> missing, long missingUses) {
        StringJoiner summary = new StringJoiner(" and ");
        if (!missing.isEmpty()) {
            summary.add("restock " + missingMaterialsSummary(missing));
        }
        if (missingUses > 0L) {
            summary.add("restore " + missingUses + " Uses");
        }
        return summary.length() == 0 ? "restock or restore Uses" : summary.toString();
    }

    private static String coords(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    private static long chunkKey(Location location) {
        int chunkX = Math.floorDiv(location.getBlockX(), 16);
        int chunkZ = Math.floorDiv(location.getBlockZ(), 16);
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static Component red(String text) {
        return Component.text(text, NamedTextColor.RED);
    }

}
