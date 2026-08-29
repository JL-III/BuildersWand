package com.playtheatria.buildersWand.ghost;

import com.playtheatria.buildersWand.config.PluginConfig;
import com.playtheatria.buildersWand.form.Dims;
import com.playtheatria.buildersWand.form.Form;
import com.playtheatria.buildersWand.form.LimitKind;
import com.playtheatria.buildersWand.form.PlanDecision;
import com.playtheatria.buildersWand.form.PlanPolicy;
import com.playtheatria.buildersWand.gesture.GestureListener;
import com.playtheatria.buildersWand.gesture.GestureSession;
import com.playtheatria.buildersWand.gesture.LivePlan;
import com.playtheatria.buildersWand.prefab.PrefabPlacementController;
import com.playtheatria.buildersWand.wand.WandItems;
import com.playtheatria.buildersWand.wand.PlacementUseCost;
import com.playtheatria.buildersWand.wand.UseCounter;
import com.playtheatria.buildersWand.wave.Feedstock;
import com.playtheatria.buildersWand.wave.PlacementBudget;
import com.playtheatria.buildersWand.wave.PlacementRules;
import com.playtheatria.buildersWand.wave.Plan;
import com.playtheatria.buildersWand.wave.PlannedCell;
import com.playtheatria.buildersWand.wave.WaveRunner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Per-player display-entity ghost preview (design §8) and the §8.4 action bar. One repeating
 * sync task diffs each wand-holder's plan against their live ghosts, spawning/removing/
 * re-blocking only the difference. Ghosts are owner-only and suppressed during that player's
 * wave. All geometry comes from {@link LivePlan} — no second implementation.
 */
public final class GhostService {

    private static final String NO_MATERIAL = WandItems.MATERIAL_HINT;
    private static final String AIM_HINT = "Aim at a surface; RIGHT-CLICK anchors there.";
    // The idle "how to use" hint shows for PULSE_SHOW of every PULSE_PERIOD ghost ticks, then
    // nothing at all for the rest — so it never holds the action bar hostage: other plugins'
    // messages can take the bar during the (long) gap, and Minecraft fades the hint out on its
    // own. At the default 2-tick update this is ~1.2s shown out of a ~6s cycle.
    private static final int PULSE_PERIOD = 60;
    private static final int PULSE_SHOW = 12;
    private static final int PREFAB_PREFLIGHT_CACHE_TICKS = 10;
    private static final TextColor HINT_COLOR = TextColor.color(0x55, 0xFF, 0xFF); // aqua
    private static final Color ANCHOR_GLOW = Color.fromRGB(0x55FFFF);
    private static final Color PREFAB_ORIGIN_GLOW = Color.fromRGB(0xFFFF55);
    private static final float ANCHOR_SCALE = 0.24f;

    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final WandItems wandItems;
    private final WaveRunner waveRunner;
    private final GestureListener gestureListener;
    private final Map<UUID, Map<BlockVector, BlockDisplay>> ghosts = new HashMap<>();
    /** Lightweight red boundary used instead of a misleading unplaceable full-cell preview. */
    private final Map<UUID, Map<BlockVector, BlockDisplay>> limitOutlines = new HashMap<>();
    /** Independent marker for the immutable clicked anchor; never filtered with plan cells. */
    private final Map<UUID, BlockDisplay> anchorGhosts = new HashMap<>();
    private final Map<UUID, PrefabPreflight> prefabPreflights = new HashMap<>();
    private PrefabPlacementController prefabPlacement;
    private int pulseTick;

    private record PrefabPreflight(Plan plan, int checkedAtTick, Optional<String> failure) {
    }

    public GhostService(JavaPlugin plugin, PluginConfig config, WandItems wandItems,
                        WaveRunner waveRunner, GestureListener gestureListener) {
        this.plugin = plugin;
        this.config = config;
        this.wandItems = wandItems;
        this.waveRunner = waveRunner;
        this.gestureListener = gestureListener;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, config.ghostUpdateTicks, config.ghostUpdateTicks);
    }

    public void setPrefabPlacement(PrefabPlacementController prefabPlacement) {
        this.prefabPlacement = prefabPlacement;
    }

    private void tick() {
        pulseTick++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    private void update(Player player) {
        ItemStack wand = player.getInventory().getItemInOffHand();
        if (!wandItems.isWand(wand) || !player.hasPermission(WandItems.PERMISSION_USE)) {
            // The gesture belongs to one exact offhand wand. Inventory moves do not emit the
            // swap/drop events below, so invalidate here as soon as the wand disappears.
            if (gestureListener.sessionOf(player) != null) {
                gestureListener.clearSession(player);
            } else {
                clearFor(player);
            }
            if (prefabPlacement != null) {
                prefabPlacement.clearRuntime(player);
            }
            return;
        }
        if (waveRunner.hasActiveWave(player)) {
            clearFor(player); // suppress ghosts during the player's own wave
            return;
        }
        if (wand.getAmount() != 1) {
            gestureListener.clearSession(player);
            if (prefabPlacement != null) {
                prefabPlacement.clearRuntime(player);
            }
            sendPulsedHint(player, "Builders Wands cannot be used while stacked.");
            return;
        }
        if (prefabPlacement != null && prefabPlacement.active(player)) {
            updatePrefab(player, wand);
            return;
        }
        boolean usesBypass = player.hasPermission(WandItems.PERMISSION_USES_BYPASS);
        UseCounter.State wandUses = wandItems.uses(wand);
        Form form = wandItems.getForm(wand);
        GestureSession session = gestureListener.sessionOf(player);
        if (session != null && !java.util.Objects.equals(
                session.wandId, wandItems.identity(wand).wandId())) {
            // Replacing an offhand wand via an inventory click must never resume an old print.
            gestureListener.clearSession(player);
            session = null;
        }
        if (session != null) {
            gestureListener.refreshPreferences(player, wand);
        }
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        if (session == null) {
            clearAnchorFor(player);
            // Un-anchored: breathe the "how to use" hint (and leave silent gaps for other
            // plugins' action-bar messages); ghost the would-be anchor cell every tick.
            GestureSession preview = new GestureSession();
            preview.form = form;
            preview.density = wandItems.getDensity(wand);
            preview.surfaceRestriction = wandItems.getSurfaceRestriction(wand);
            WandItems.MaterialSelection selection = wandItems.materialSelection(player);
            Optional<Plan> plan = LivePlan.derive(player, preview, config, wandItems);
            if (plan.isEmpty()) {
                clearFor(player);
                if (selection.snapshot().isEmpty()) {
                    sendPulsedStatus(player, selection.problem().isBlank()
                            ? NO_MATERIAL : selection.problem(), NamedTextColor.RED);
                } else {
                    String uses = usesBypass ? "Uses bypassed" : "Uses "
                            + wandUses.remaining() + "/" + wandUses.maximum();
                    sendPulsedHint(player, form.label() + " · " + uses + " · " + AIM_HINT);
                }
                return;
            }
            if (waterEvaporates(plan)) {
                Affordability affordable = blocked(affordability(player, plan, creative,
                        usesBypass, wandUses, null), "Water evaporates in this world");
                syncGhosts(player, plan, affordable);
                sendPulsedStatus(player, "BLOCKED — Water evaporates in this world.",
                        NamedTextColor.RED);
                return;
            }
            String uses = usesBypass ? "Uses bypassed" : "Uses " + wandUses.remaining()
                    + "/" + wandUses.maximum();
            Affordability affordable = affordability(player, plan, creative, usesBypass, wandUses,
                    null);
            syncGhosts(player, plan, affordable);
            if (affordable.status() == PreviewStatus.BLOCKED) {
                String reason = affordable.blockedReason() == null
                        || affordable.blockedReason().isBlank()
                        ? "no cell can be placed" : affordable.blockedReason();
                sendPulsedStatus(player, "BLOCKED — " + reason + " · " + uses,
                        NamedTextColor.RED);
            } else if (containsWater(plan.get())) {
                sendPulsedStatus(player, Component.text("READY — ", NamedTextColor.GREEN)
                        .append(Component.text("water costs " + config.waterUsesPerSource
                                + " Uses/source", NamedTextColor.YELLOW))
                        .append(Component.text(" · " + uses, NamedTextColor.GRAY)));
            } else {
                String mode = form.label() + (form.supportsDensity()
                        ? " · " + preview.density.name() : "")
                        + (form == Form.EXTEND_SURFACE
                        ? " · " + preview.surfaceRestriction.name() : "");
                sendPulsedHint(player, mode + " · " + uses
                        + " · " + AIM_HINT);
            }
            return;
        }

        Optional<Plan> plan = LivePlan.derive(player, session, config, wandItems);
        if (plan.isEmpty()) {
            WandItems.MaterialSelection selection = wandItems.materialSelection(player);
            player.sendActionBar(Component.text(anchoredPlanFailure(session.form,
                    selection.snapshot().isPresent(), selection.problem()), NamedTextColor.RED));
            clearFor(player);
            return;
        }
        syncAnchorGhost(player, plan.get());
        if (waterEvaporates(plan)) {
            Affordability affordable = affordability(player, plan, creative,
                    usesBypass, wandUses, null);
            // A limit refusal is the more actionable problem and needs the bounded red outline.
            // Do not relabel it as evaporation while retaining a contradictory TOO LARGE status.
            if (affordable.limitKind() == null) {
                affordable = blocked(affordable, "Water evaporates in this world");
            }
            syncGhosts(player, plan, affordable);
            player.sendActionBar(actionBar(session, plan.get(), affordable, creative));
            return;
        }
        Affordability affordable = affordability(player, plan, creative, usesBypass, wandUses,
                null);
        syncGhosts(player, plan, affordable);
        player.sendActionBar(actionBar(session, plan.get(), affordable, creative));
    }

    private void updatePrefab(Player player, ItemStack wand) {
        clearAnchorFor(player);
        clearLimitOutlineFor(player);
        Optional<PrefabPlacementController.Preview> preview = prefabPlacement.preview(player);
        if (preview.isEmpty()) {
            clearFor(player);
            return;
        }
        PrefabPlacementController.Preview state = preview.get();
        if (!state.anchored()) {
            syncOriginGhost(player,
                    prefabPlacement.originIndicator(player, state.definition()));
            sendPulsedStatus(player, "PREFAB SELECTED — " + state.definition().metadata().name()
                    + " · RIGHT-CLICK a block face to anchor", NamedTextColor.YELLOW);
            return;
        }
        Plan plan = state.plan().orElseThrow();
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        boolean usesBypass = player.hasPermission(WandItems.PERMISSION_USES_BYPASS);
        UseCounter.State uses = wandItems.uses(wand);
        Optional<String> hardFailure = state.blockedReason().isBlank()
                ? cachedPrefabHardFailure(player, plan)
                : Optional.empty();
        String retained = state.blockedReason().isBlank()
                ? hardFailure.orElse(null) : state.blockedReason();
        Affordability affordable = affordability(player, Optional.of(plan), creative, usesBypass,
                uses, retained, true);
        syncGhosts(player, Optional.of(plan), affordable);
        player.sendActionBar(prefabActionBar(state, affordable));
    }

    private Component prefabActionBar(PrefabPlacementController.Preview preview,
                                      Affordability affordable) {
        NamedTextColor color = statusColor(affordable.status());
        String status = statusLabel(affordable.status());
        int printable = affordable.printable().size();
        Component line = Component.text(status + " — "
                + preview.definition().metadata().name() + " · "
                + (preview.quarterTurns() * 90) + "° · ", color);
        if (affordable.status() == PreviewStatus.BLOCKED) {
            line = line.append(Component.text(affordable.blockedReason() == null
                    ? "nothing can begin" : affordable.blockedReason().replace("BLOCKED — ", ""),
                    NamedTextColor.RED));
        } else {
            line = line.append(Component.text(printable + " cells ready", NamedTextColor.GREEN));
        }
        String uses = affordable.status() == PreviewStatus.BLOCKED
                ? "nothing spent"
                : affordable.usesBypass()
                ? "Uses bypassed"
                : Math.addExact(affordable.budget().affordableUses(),
                        preview.plan().orElseThrow().options().activationUses()) + " Uses";
        String hint = affordable.resourceShortage()
                ? "Restock every red cell before requesting confirmation · LEFT re-anchors"
                : preview.quote() == null
                ? "RIGHT requests confirmation · SHIFT-LEFT rotates · LEFT re-anchors"
                : "Use PLACE in chat · /wand prefab cancel cancels";
        return line.append(Component.text(" · " + uses + " · " + hint,
                NamedTextColor.AQUA));
    }

    private Optional<String> cachedPrefabHardFailure(Player player, Plan plan) {
        int now = Bukkit.getCurrentTick();
        PrefabPreflight cached = prefabPreflights.get(player.getUniqueId());
        if (cached != null && cached.plan().equals(plan)) {
            int age = now - cached.checkedAtTick();
            if (age >= 0 && age < PREFAB_PREFLIGHT_CACHE_TICKS) {
                return cached.failure();
            }
        }
        Optional<String> failure = waveRunner.previewHardFailure(player, plan);
        prefabPreflights.put(player.getUniqueId(), new PrefabPreflight(plan, now, failure));
        return failure;
    }

    private static boolean waterEvaporates(Optional<Plan> plan) {
        return plan.isPresent() && plan.get().targets().stream()
                .anyMatch(target -> target.material().isWater())
                && plan.get().world().isUltraWarm();
    }

    enum PreviewStatus {
        READY,
        BLOCKED
    }

    /** Premium costs are informational; any actual shortage blocks the complete operation. */
    static PreviewStatus previewStatus(boolean hardBlocked, boolean fullyAffordable,
                                       boolean ignoredInformationalCostNotice) {
        return hardBlocked || !fullyAffordable ? PreviewStatus.BLOCKED : PreviewStatus.READY;
    }

    /** A shortage colors only unavailable cells red; hard failures color the entire plan red. */
    static boolean previewCellBlocked(PreviewStatus status, boolean resourceShortage,
                                      PlacementBudget.Decision decision) {
        return status == PreviewStatus.BLOCKED
                && (!resourceShortage || !decision.affordable());
    }

    /** Explain an anchored plan failure without confusing valid palettes with missing material. */
    static String anchoredPlanFailure(Form form, boolean hasMaterial, String materialProblem) {
        if (!hasMaterial) {
            return materialProblem == null || materialProblem.isBlank()
                    ? NO_MATERIAL : materialProblem;
        }
        if (form == Form.EXTEND_SURFACE) {
            return "The source surface changed. Cancel and anchor it again.";
        }
        return "The current print could not be planned. Cancel and anchor it again.";
    }

    private record CellAffordability(PlannedCell target, PlacementBudget.Decision decision) {
    }

    private record Affordability(List<CellAffordability> printable, int kept,
                                 List<PlannedCell> conflicts,
                                 Map<org.bukkit.Material, Integer> availableMaterials,
                                 PlacementBudget.Result budget, int uses, boolean usesBypass,
                                 PreviewStatus status, String blockedReason,
                                 boolean resourceShortage, LimitKind limitKind) {
    }

    /** Simulate each assigned material and variable Use cost in exact emission order. */
    private Affordability affordability(Player player, Optional<Plan> plan, boolean creative,
                                        boolean usesBypass, UseCounter.State wandUses,
                                        String blockedReason) {
        return affordability(player, plan, creative, usesBypass, wandUses, blockedReason, false);
    }

    private Affordability affordability(Player player, Optional<Plan> plan, boolean creative,
                                        boolean usesBypass, UseCounter.State wandUses,
                                        String blockedReason,
                                        boolean hardPreflightAlreadyChecked) {
        if (plan.isEmpty()) {
            PlacementBudget.Result empty = PlacementBudget.evaluate(List.of(), Map.of(),
                    wandUses.remaining(), creative, usesBypass);
            return new Affordability(List.of(), 0, List.of(), Map.of(), empty,
                    usesBypass ? Integer.MAX_VALUE : wandUses.remaining(), usesBypass,
                    PreviewStatus.BLOCKED, "No valid plan", false, null);
        }
        if (plan.get().options().kind()
                == com.playtheatria.buildersWand.wave.PlanOptions.Kind.ORDINARY) {
            List<BlockVector> exactCells = plan.get().targets().stream()
                    .map(target -> new BlockVector(target.location().getBlockX(),
                            target.location().getBlockY(), target.location().getBlockZ()))
                    .toList();
            PlanDecision legality = new PlanPolicy(config.formLimits).evaluateAbsoluteCells(
                    plan.get().form(), plan.get().dims(), plan.get().density(), exactCells);
            if (!legality.allowed()) {
                return limitBlocked(wandUses, creative, usesBypass, legality);
            }
        }
        List<PlannedCell> printable = new java.util.ArrayList<>();
        List<PlannedCell> conflicts = new java.util.ArrayList<>();
        int kept = 0;
        for (PlannedCell target : plan.get().targets()) {
            var block = target.location().getBlock();
            if (PlacementRules.isAlreadyBuilt(block, target.blockData())) {
                kept++;
            } else if (block.isReplaceable()) {
                printable.add(target);
            } else if (plan.get().options().strictExistingStates()) {
                conflicts.add(target);
            } else {
                kept++;
            }
        }
        if (plan.get().options().kind()
                == com.playtheatria.buildersWand.wave.PlanOptions.Kind.ORDINARY) {
            PlanDecision placement = new PlanPolicy(config.formLimits)
                    .evaluatePlacementCount(plan.get().form(), printable.size());
            if (!placement.allowed()) {
                return limitBlocked(wandUses, creative, usesBypass, placement);
            }
        }
        // The cheap placement cap must win before region/entity preflight. Otherwise a plan that
        // is already too large can perform thousands of protection lookups on every ghost tick.
        if (!hardPreflightAlreadyChecked
                && (blockedReason == null || blockedReason.isBlank())) {
            blockedReason = waveRunner.previewHardFailure(player, plan.get()).orElse(null);
        }
        Map<org.bukkit.Material, Integer> available = new LinkedHashMap<>();
        for (PlannedCell target : printable) {
            available.computeIfAbsent(target.material().sourceItem(), material ->
                    Feedstock.available(player.getInventory(), target.material(), wandItems));
        }
        List<PlacementBudget.Cost> costs = printable.stream().map(this::costOf).toList();
        int cellUseBalance = WaveRunner.remainingUsesForCells(wandUses.remaining(),
                plan.get().options().activationUses(), usesBypass);
        PlacementBudget.Result budget = PlacementBudget.evaluate(costs, available,
                cellUseBalance, creative, usesBypass);
        List<CellAffordability> cells = new java.util.ArrayList<>(printable.size());
        for (int i = 0; i < printable.size(); i++) {
            cells.add(new CellAffordability(printable.get(i), budget.decisions().get(i)));
        }
        PreviewStatus status;
        String reason = blockedReason;
        boolean resourceShortage = false;
        if (reason != null && !reason.isBlank() || !conflicts.isEmpty()
                || printable.isEmpty()) {
            status = previewStatus(true, true, containsWater(plan.get()));
            if (reason == null || reason.isBlank()) {
                if (!conflicts.isEmpty()) {
                    reason = conflicts.size() + " conflicting cell"
                            + (conflicts.size() == 1 ? " must" : "s must") + " be cleared";
                } else if (printable.isEmpty()) {
                    reason = plan.get().options().prefab()
                            ? "this prefab is already complete at that anchor"
                            : "every target is already built or occupied";
                }
            }
        } else if (!budget.fullyAffordable()) {
            status = previewStatus(false, false, containsWater(plan.get()));
            resourceShortage = true;
            reason = shortageReason(budget, wandUses.remaining(),
                    plan.get().options().activationUses(), usesBypass);
        } else {
            status = previewStatus(false, true, containsWater(plan.get()));
        }
        return new Affordability(List.copyOf(cells), kept, List.copyOf(conflicts),
                Map.copyOf(available), budget,
                usesBypass ? Integer.MAX_VALUE : wandUses.remaining(), usesBypass, status, reason,
                resourceShortage, null);
    }

    private Affordability limitBlocked(UseCounter.State wandUses, boolean creative,
                                       boolean usesBypass, PlanDecision refusal) {
        PlacementBudget.Result empty = PlacementBudget.evaluate(List.of(), Map.of(),
                wandUses.remaining(), creative, usesBypass);
        return new Affordability(List.of(), 0, List.of(), Map.of(), empty,
                usesBypass ? Integer.MAX_VALUE : wandUses.remaining(), usesBypass,
                PreviewStatus.BLOCKED, refusal.message(),
                false, refusal.violation().orElseThrow().kind());
    }

    private PlacementBudget.Cost costOf(PlannedCell target) {
        return new PlacementBudget.Cost(target.material().sourceItem(),
                !target.material().reusable(),
                PlacementUseCost.perCell(target.material(), config.waterUsesPerSource));
    }

    private static String shortageReason(PlacementBudget.Result budget, int remainingUses,
                                         int activationUses, boolean usesBypass) {
        long totalUses = Math.addExact(budget.requiredUses(), activationUses);
        long missingUses = usesBypass ? 0L : Math.max(0L, totalUses - remainingUses);
        StringJoiner missing = new StringJoiner(" and ");
        if (!budget.missingMaterials().isEmpty()) {
            StringJoiner materials = new StringJoiner(", ");
            budget.missingMaterials().forEach((material, count) -> materials.add(count + " "
                    + WandItems.materialDisplayName(material)));
            missing.add("restock " + materials);
        }
        if (missingUses > 0L) {
            missing.add("restore " + missingUses + " Uses");
        }
        return "complete print requires "
                + (missing.length() == 0 ? "more resources" : missing)
                + "; nothing will be placed";
    }

    private static Affordability blocked(Affordability affordability, String reason) {
        return new Affordability(affordability.printable(), affordability.kept(),
                affordability.conflicts(), affordability.availableMaterials(),
                affordability.budget(), affordability.uses(), affordability.usesBypass(),
                PreviewStatus.BLOCKED, reason, false, null);
    }

    /**
     * Show the idle hint during the show window, and send nothing during the gap — so the hint
     * pulses (Minecraft fades it out on its own) without ever holding the action bar hostage,
     * leaving the gap free for other plugins' messages.
     */
    private void sendPulsedHint(Player player, String text) {
        sendPulsedStatus(player, text, HINT_COLOR);
    }

    private void sendPulsedStatus(Player player, String text, TextColor color) {
        sendPulsedStatus(player, Component.text(text, color));
    }

    private void sendPulsedStatus(Player player, Component message) {
        if (pulseTick % PULSE_PERIOD < PULSE_SHOW) {
            player.sendActionBar(message);
        }
    }

    // ---------------------------------------------------------------- ghost diff (design §8.3)

    /**
     * The clicked anchor is interaction state, not a printable cell. Keep it visible even when
     * that cell is already built, lies outside an over-limit preview sample, or the box grows in
     * a negative direction and its effective geometry origin moves.
     */
    private void syncAnchorGhost(Player player, Plan plan) {
        BlockVector anchor = plan.interactionAnchor();
        BlockDisplay current = anchorGhosts.get(player.getUniqueId());
        if (current != null && current.isValid()
                && current.getWorld().equals(plan.world())
                && current.getLocation().getBlockX() == anchor.getBlockX()
                && current.getLocation().getBlockY() == anchor.getBlockY()
                && current.getLocation().getBlockZ() == anchor.getBlockZ()) {
            return;
        }
        clearAnchorFor(player);
        Location location = new Location(plan.world(), anchor.getBlockX(), anchor.getBlockY(),
                anchor.getBlockZ());
        BlockDisplay marker = plan.world().spawn(location, BlockDisplay.class, entity -> {
            entity.setBlock(Material.GOLD_BLOCK.createBlockData());
            // A thin post rises just above the target cell, remaining legible through a normal
            // full-cell ghost without resembling another block that will be placed.
            entity.setTransformation(new Transformation(
                    new Vector3f((1f - ANCHOR_SCALE) / 2f, 0.05f,
                            (1f - ANCHOR_SCALE) / 2f),
                    new Quaternionf(), new Vector3f(ANCHOR_SCALE, 1.25f, ANCHOR_SCALE),
                    new Quaternionf()));
            entity.setGlowing(true);
            entity.setGlowColorOverride(ANCHOR_GLOW);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
        });
        player.showEntity(plugin, marker);
        anchorGhosts.put(player.getUniqueId(), marker);
    }

    private void clearAnchorFor(Player player) {
        BlockDisplay marker = anchorGhosts.remove(player.getUniqueId());
        if (marker != null) {
            marker.remove();
        }
    }

    private void syncOriginGhost(Player player,
                                 Optional<PrefabPlacementController.OriginIndicator> origin) {
        if (origin.isEmpty()) {
            clearFor(player);
            return;
        }
        Location location = origin.get().location();
        BlockVector key = new BlockVector(location.getBlockX(), location.getBlockY(),
                location.getBlockZ());
        Map<BlockVector, BlockDisplay> current = ghosts.computeIfAbsent(
                player.getUniqueId(), ignored -> new HashMap<>());
        BlockDisplay display = current.get(key);
        if (display == null || !display.isValid()) {
            current.put(key, spawn(player, location, origin.get().blockData(),
                    PREFAB_ORIGIN_GLOW));
        } else {
            if (!display.getBlock().getAsString().equals(
                    origin.get().blockData().getAsString())) {
                display.setBlock(origin.get().blockData());
            }
            if (!PREFAB_ORIGIN_GLOW.equals(display.getGlowColorOverride())) {
                display.setGlowColorOverride(PREFAB_ORIGIN_GLOW);
            }
        }
        current.entrySet().removeIf(entry -> {
            if (!entry.getKey().equals(key)) {
                entry.getValue().remove();
                return true;
            }
            return false;
        });
    }

    private void syncGhosts(Player player, Optional<Plan> planOpt, Affordability affordable) {
        if (planOpt.isEmpty()) {
            clearFor(player);
            return;
        }
        if (affordable.limitKind() != null) {
            clearRegularGhostsFor(player);
            syncLimitOutline(player, planOpt.get());
            return;
        }
        clearLimitOutlineFor(player);
        Map<BlockVector, BlockDisplay> current = ghosts.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        Set<BlockVector> desired = new HashSet<>();

        for (CellAffordability cell : affordable.printable()) {
            PlannedCell target = cell.target();
            Location loc = target.location();
            BlockVector key = new BlockVector(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            if (!desired.add(key)) {
                continue;
            }
            BlockData previewData = target.material().isWater()
                    ? target.material().previewBlock().createBlockData()
                    : target.blockData();
            Color glow = previewCellBlocked(affordable.status(), affordable.resourceShortage(),
                    cell.decision()) ? config.ghostBlockedGlow : config.ghostReadyGlow;
            BlockDisplay existing = current.get(key);
            if (existing == null || !existing.isValid()) {
                current.put(key, spawn(player, loc, previewData, glow));
            } else {
                if (!existing.getBlock().getAsString().equals(previewData.getAsString())) {
                    existing.setBlock(previewData); // material or rotation changed
                }
                if (!glow.equals(existing.getGlowColorOverride())) {
                    existing.setGlowColorOverride(glow); // affordability changed
                }
            }
        }
        for (PlannedCell target : affordable.conflicts()) {
            Location loc = target.location();
            BlockVector key = new BlockVector(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            if (!desired.add(key)) {
                continue;
            }
            BlockData previewData = target.material().isWater()
                    ? target.material().previewBlock().createBlockData()
                    : target.blockData();
            BlockDisplay existing = current.get(key);
            if (existing == null || !existing.isValid()) {
                current.put(key, spawn(player, loc, previewData, config.ghostBlockedGlow));
            } else {
                if (!existing.getBlock().getAsString().equals(previewData.getAsString())) {
                    existing.setBlock(previewData);
                }
                if (!config.ghostBlockedGlow.equals(existing.getGlowColorOverride())) {
                    existing.setGlowColorOverride(config.ghostBlockedGlow);
                }
            }
        }
        current.entrySet().removeIf(entry -> {
            if (!desired.contains(entry.getKey())) {
                entry.getValue().remove();
                return true;
            }
            return false;
        });
    }

    private BlockDisplay spawn(Player player, Location cell, BlockData data, Color glow) {
        World world = cell.getWorld();
        Location origin = new Location(world, cell.getBlockX(), cell.getBlockY(), cell.getBlockZ());
        float s = config.ghostScale;
        float t = (1f - s) / 2f;
        BlockDisplay display = world.spawn(origin, BlockDisplay.class, entity -> {
            entity.setBlock(data);
            entity.setTransformation(new Transformation(
                    new Vector3f(t, t, t), new Quaternionf(),
                    new Vector3f(s, s, s), new Quaternionf()));
            entity.setGlowing(true);
            entity.setGlowColorOverride(glow);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setTeleportDuration(2);
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
        });
        player.showEntity(plugin, display);
        return display;
    }

    private void syncLimitOutline(Player player, Plan plan) {
        List<BlockVector> targets = plan.targets().stream()
                .map(target -> new BlockVector(target.location().getBlockX(),
                        target.location().getBlockY(), target.location().getBlockZ()))
                .toList();
        Set<BlockVector> desired = boundingOutline(targets, plan.interactionAnchor());
        Map<BlockVector, BlockDisplay> current = limitOutlines.computeIfAbsent(
                player.getUniqueId(), ignored -> new HashMap<>());
        BlockData redGlass = Material.RED_STAINED_GLASS.createBlockData();
        for (BlockVector key : desired) {
            BlockDisplay display = current.get(key);
            if (display == null || !display.isValid()) {
                Location location = new Location(plan.world(), key.getBlockX(), key.getBlockY(),
                        key.getBlockZ());
                current.put(key, spawnSmallMarker(player, location, redGlass,
                        config.ghostBlockedGlow));
            }
        }
        current.entrySet().removeIf(entry -> {
            if (!desired.contains(entry.getKey())) {
                entry.getValue().remove();
                return true;
            }
            return false;
        });
    }

    /** Up to 152 boundary points: all corners plus twelve evenly sampled AABB edges. */
    static Set<BlockVector> boundingOutline(List<BlockVector> targets, BlockVector anchor) {
        int minX = anchor.getBlockX();
        int minY = anchor.getBlockY();
        int minZ = anchor.getBlockZ();
        int maxX = minX;
        int maxY = minY;
        int maxZ = minZ;
        for (BlockVector target : targets) {
            minX = Math.min(minX, target.getBlockX());
            minY = Math.min(minY, target.getBlockY());
            minZ = Math.min(minZ, target.getBlockZ());
            maxX = Math.max(maxX, target.getBlockX());
            maxY = Math.max(maxY, target.getBlockY());
            maxZ = Math.max(maxZ, target.getBlockZ());
        }
        LinkedHashSet<BlockVector> outline = new LinkedHashSet<>();
        for (int x : new int[]{minX, maxX}) {
            for (int y : new int[]{minY, maxY}) {
                for (int z : new int[]{minZ, maxZ}) {
                    outline.add(new BlockVector(x, y, z));
                }
            }
        }
        for (int y : new int[]{minY, maxY}) {
            for (int z : new int[]{minZ, maxZ}) {
                addSampledEdge(outline, 0, minX, maxX, minX, y, z);
            }
        }
        for (int x : new int[]{minX, maxX}) {
            for (int z : new int[]{minZ, maxZ}) {
                addSampledEdge(outline, 1, minY, maxY, x, minY, z);
            }
        }
        for (int x : new int[]{minX, maxX}) {
            for (int y : new int[]{minY, maxY}) {
                addSampledEdge(outline, 2, minZ, maxZ, x, y, minZ);
            }
        }
        return Set.copyOf(outline);
    }

    private static void addSampledEdge(Set<BlockVector> out, int axis, int min, int max,
                                       int x, int y, int z) {
        int interior = Math.max(0, max - min - 1);
        int samples = Math.min(12, interior);
        for (int index = 1; index <= samples; index++) {
            int value = min + (int) Math.round((double) index * (max - min) / (samples + 1));
            out.add(switch (axis) {
                case 0 -> new BlockVector(value, y, z);
                case 1 -> new BlockVector(x, value, z);
                case 2 -> new BlockVector(x, y, value);
                default -> throw new IllegalArgumentException("axis must be 0, 1, or 2");
            });
        }
    }

    private BlockDisplay spawnSmallMarker(Player player, Location location, BlockData data,
                                          Color glow) {
        float scale = 0.22f;
        float inset = (1f - scale) / 2f;
        BlockDisplay display = location.getWorld().spawn(location, BlockDisplay.class, entity -> {
            entity.setBlock(data);
            entity.setTransformation(new Transformation(new Vector3f(inset, inset, inset),
                    new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
            entity.setGlowing(true);
            entity.setGlowColorOverride(glow);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
        });
        player.showEntity(plugin, display);
        return display;
    }

    private void clearRegularGhostsFor(Player player) {
        Map<BlockVector, BlockDisplay> current = ghosts.remove(player.getUniqueId());
        if (current != null) {
            current.values().forEach(BlockDisplay::remove);
        }
    }

    private void clearLimitOutlineFor(Player player) {
        Map<BlockVector, BlockDisplay> current = limitOutlines.remove(player.getUniqueId());
        if (current != null) {
            current.values().forEach(BlockDisplay::remove);
        }
    }

    /** Remove one player's ghosts (the gesture-drop clearer). */
    public void clearFor(Player player) {
        prefabPreflights.remove(player.getUniqueId());
        clearAnchorFor(player);
        clearRegularGhostsFor(player);
        clearLimitOutlineFor(player);
    }

    /** Remove every tracked ghost (plugin disable). */
    public void clearAll() {
        ghosts.values().forEach(map -> map.values().forEach(BlockDisplay::remove));
        limitOutlines.values().forEach(map -> map.values().forEach(BlockDisplay::remove));
        anchorGhosts.values().forEach(BlockDisplay::remove);
        ghosts.clear();
        limitOutlines.clear();
        anchorGhosts.clear();
        prefabPreflights.clear();
    }

    // ---------------------------------------------------------------- action bar (design §8.4)

    private static NamedTextColor statusColor(PreviewStatus status) {
        return switch (status) {
            case READY -> NamedTextColor.GREEN;
            case BLOCKED -> NamedTextColor.RED;
        };
    }

    private static String statusLabel(PreviewStatus status) {
        return switch (status) {
            case READY -> "READY";
            case BLOCKED -> "BLOCKED";
        };
    }

    private Component actionBar(GestureSession session, Plan plan, Affordability affordable, boolean creative) {
        Dims dims = plan.dims();
        if (affordable.limitKind() != null) {
            boolean sizeLimit = affordable.limitKind() == LimitKind.CELLS
                    || affordable.limitKind() == LimitKind.SCANNED_CELLS;
            String label = sizeLimit ? "TOO LARGE" : "BLOCKED";
            String hint = switch (affordable.limitKind()) {
                case CELLS, SCANNED_CELLS, CHUNKS, DIMENSION ->
                        "Aim closer to reduce the shape · LEFT cancels";
                case GEOMETRY -> "Cancel and anchor again";
            };
            return Component.text(label + " — ", NamedTextColor.RED)
                    .append(Component.text(session.form.label()
                            + (session.form.supportsDensity() ? " " + plan.density().name() : "")
                            + " " + dims.primary() + "×" + dims.secondary() + "×"
                            + dims.tertiary() + " · ", NamedTextColor.GRAY))
                    .append(Component.text(affordable.blockedReason(), NamedTextColor.RED))
                    .append(Component.text(" · " + hint, NamedTextColor.AQUA));
        }
        int printable = affordable.printable().size();
        NamedTextColor statusColor = switch (affordable.status()) {
            case READY -> NamedTextColor.GREEN;
            case BLOCKED -> NamedTextColor.RED;
        };
        String label = switch (affordable.status()) {
            case READY -> "READY";
            case BLOCKED -> "BLOCKED";
        };
        Component result = Component.text(label + " — ", statusColor)
                .append(Component.text(session.form.label()
                        + (session.form.supportsDensity() ? " " + plan.density().name() : "")
                        + (session.form == Form.EXTEND_SURFACE
                        ? " " + session.surfaceRestriction.name() : "")
                        + " " + dims.primary() + "×"
                        + dims.secondary() + "×" + dims.tertiary() + " · ", NamedTextColor.GRAY));
        if (affordable.status() == PreviewStatus.READY) {
            result = result.append(Component.text(printable + " cells ready", NamedTextColor.GREEN));
        } else {
            String reason = affordable.blockedReason() == null
                    ? "nothing can be placed" : affordable.blockedReason().replace("BLOCKED — ", "");
            result = result.append(Component.text(reason, NamedTextColor.RED));
        }
        if (affordable.status() == PreviewStatus.READY && containsWater(plan)) {
            result = result.append(Component.text(" · water costs "
                    + config.waterUsesPerSource + " Uses/source", NamedTextColor.YELLOW));
        }
        String uses = affordable.usesBypass()
                ? "Uses bypassed"
                : "Uses " + affordable.uses() + " / " + affordable.budget().requiredUses() + " needed";
        result = result.append(Component.text(" · " + uses, NamedTextColor.GRAY));
        String actionHint;
        if (affordable.status() == PreviewStatus.BLOCKED) {
            actionHint = affordable.resourceShortage()
                    ? "Restock every red cell or resize · LEFT cancels"
                    : "Fix the problem or LEFT cancels";
        } else {
            actionHint = hint(session);
        }
        return result.append(Component.text(" · " + actionHint, NamedTextColor.AQUA));
    }

    private static boolean containsWater(Plan plan) {
        return plan.targets().stream().anyMatch(target -> target.material().isWater());
    }

    private static String hint(GestureSession session) {
        if (session.stage() >= session.form.lockStages()) {
            return "RIGHT requests print · LEFT cancels";
        }
        String locks = switch (session.form) {
            case CYLINDER, SPHERE -> "RIGHT locks radius";
            case DIAGONAL -> "RIGHT locks width";
            case BOX -> session.orientation.wall()
                    ? (session.stage() == 0 ? "RIGHT locks width" : "RIGHT locks height")
                    : (session.stage() == 0 ? "RIGHT locks length" : "RIGHT locks width");
            default -> "RIGHT locks dimensions";
        };
        return locks + " · LEFT cancels";
    }
}
