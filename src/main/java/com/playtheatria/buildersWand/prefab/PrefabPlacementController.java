package com.playtheatria.buildersWand.prefab;

import com.playtheatria.buildersWand.command.PrefabCommandHandler;
import com.playtheatria.buildersWand.config.PluginConfig;
import com.playtheatria.buildersWand.gesture.LivePlan;
import com.playtheatria.buildersWand.wand.WandItems;
import com.playtheatria.buildersWand.wave.PlacementQuote;
import com.playtheatria.buildersWand.wave.Plan;
import com.playtheatria.buildersWand.wave.WaveRunner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockVector;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Selected → anchored → awaiting confirmation → placing state machine for reusable prefabs.
 * It owns no entitlement or economy state and delegates every actual cell to {@link WaveRunner}.
 */
public final class PrefabPlacementController
        implements PrefabCommandHandler.PlacementCommands, Listener {

    public record OriginIndicator(Location location, BlockData blockData) {
    }

    public record Preview(PrefabDefinition definition, Optional<Plan> plan,
                          PlacementQuote quote, String blockedReason, int quarterTurns) {
        public Preview {
            plan = plan == null ? Optional.empty() : plan;
            blockedReason = blockedReason == null ? "" : blockedReason;
        }

        public boolean anchored() {
            return plan.isPresent();
        }
    }

    private static final class Session {
        private final String prefabId;
        private final String contentHash;
        private final String wandId;
        private final UUID worldId;
        private final BlockVector anchor;
        private int quarterTurns;
        private Plan plan;
        private PlacementQuote quote;
        private String token;
        private String blockedReason;

        private Session(PrefabDefinition definition, String wandId, World world, BlockVector anchor,
                        int quarterTurns, Plan plan) {
            this.prefabId = definition.id();
            this.contentHash = definition.contentHash();
            this.wandId = wandId;
            this.worldId = world.getUID();
            this.anchor = anchor;
            this.quarterTurns = quarterTurns;
            this.plan = plan;
        }

        private void clearQuote() {
            quote = null;
            token = null;
        }
    }

    private final JavaPlugin plugin;
    private final PrefabService prefabs;
    private final PluginConfig config;
    private final PrefabPlanFactory planFactory;
    private final WandItems wandItems;
    private final WaveRunner waveRunner;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public PrefabPlacementController(JavaPlugin plugin, PrefabService prefabs,
                                     PluginConfig config,
                                     WandItems wandItems, WaveRunner waveRunner) {
        this.plugin = plugin;
        this.prefabs = prefabs;
        this.config = config;
        this.wandItems = wandItems;
        this.waveRunner = waveRunner;
        this.planFactory = new PrefabPlanFactory();
    }

    public boolean active(Player player) {
        return prefabs.selectedDefinition(player.getUniqueId()).isPresent();
    }

    public Optional<Preview> preview(Player player) {
        Optional<PrefabDefinition> selected = prefabs.selectedDefinition(player.getUniqueId());
        if (selected.isEmpty()) {
            sessions.remove(player.getUniqueId());
            return Optional.empty();
        }
        PrefabDefinition definition = selected.get();
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return Optional.of(new Preview(definition, Optional.empty(), null, "", 0));
        }
        if (!session.prefabId.equals(definition.id())
                || !session.contentHash.equals(definition.contentHash())
                || !session.worldId.equals(player.getWorld().getUID())
                || !sessionMatchesWand(player, session)) {
            sessions.remove(player.getUniqueId());
            return Optional.of(new Preview(definition, Optional.empty(), null, "", 0));
        }
        if (session.quote != null && session.quote.expiredAt(System.nanoTime(),
                session.plan.options().confirmationSeconds())) {
            session.clearQuote();
            session.blockedReason = "Confirmation expired; inspect the anchored preview and request a new quote";
        }
        return Optional.of(new Preview(definition, Optional.of(session.plan), session.quote,
                session.blockedReason, session.quarterTurns));
    }

    /** One inexpensive would-be origin cell while the full prefab is not anchored. */
    public Optional<OriginIndicator> originIndicator(Player player, PrefabDefinition definition) {
        Optional<BlockVector> anchor = LivePlan.wouldBeAnchor(player, config);
        if (anchor.isEmpty()) {
            return Optional.empty();
        }
        try {
            String state = definition.transformedPlacementCells(0).getFirst().blockState();
            return Optional.of(new OriginIndicator(
                    anchor.get().toLocation(player.getWorld()), Bukkit.createBlockData(state)));
        } catch (RuntimeException error) {
            return Optional.empty();
        }
    }

    /** Ordinary right-click while a prefab is selected. */
    public void rightClick(Player player) {
        Optional<PrefabDefinition> selected = prefabs.selectedDefinition(player.getUniqueId());
        if (selected.isEmpty()) {
            player.sendMessage(Component.text("That prefab selection is no longer available.",
                    NamedTextColor.RED));
            clearRuntime(player);
            return;
        }
        if (prefabs.access(player, selected.get().id()) != PrefabService.Access.ACCESSIBLE) {
            player.sendMessage(Component.text(
                    "BLOCKED — Prefab ownership is unavailable or access was removed.",
                    NamedTextColor.RED));
            clearRuntime(player);
            return;
        }
        if (!wandReady(player)) {
            player.sendMessage(Component.text(
                    "Put one Builders Wand in your offhand before placing a prefab.",
                    NamedTextColor.RED));
            clearRuntime(player);
            return;
        }
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            anchor(player, selected.get());
            return;
        }
        if (!sessionMatchesWand(player, session)) {
            clearRuntime(player);
            player.sendMessage(Component.text(
                    "The offhand wand changed, so the prefab anchor was cleared. Right-click to anchor it again.",
                    NamedTextColor.YELLOW));
            return;
        }
        requestQuote(player, selected.get(), session);
    }

    /** Ordinary left-click removes only the anchor; Shift + left-click rotates in place. */
    public void leftClick(Player player, boolean sneaking) {
        if (sneaking) {
            rotate(player);
            return;
        }
        if (sessions.remove(player.getUniqueId()) != null) {
            player.sendActionBar(Component.text(
                    "Prefab anchor removed; the design remains selected.", NamedTextColor.GRAY));
        }
    }

    public void rotate(Player player) {
        Optional<PrefabDefinition> selected = prefabs.selectedDefinition(player.getUniqueId());
        Session session = sessions.get(player.getUniqueId());
        if (selected.isEmpty() || session == null) {
            player.sendMessage(Component.text(
                    "Anchor the selected prefab before rotating it.", NamedTextColor.YELLOW));
            return;
        }
        if (!sessionMatchesWand(player, session)) {
            clearRuntime(player);
            player.sendMessage(Component.text(
                    "The offhand wand changed, so the prefab anchor was cleared.",
                    NamedTextColor.YELLOW));
            return;
        }
        if (!selected.get().metadata().allowRotation()) {
            player.sendMessage(Component.text(
                    "This prefab has a fixed authored rotation.", NamedTextColor.YELLOW));
            return;
        }
        // A rotation attempt invalidates the old PLACE token even if planning the new rotation
        // fails. A stale chat button must never place the previous orientation afterward.
        session.clearQuote();
        session.blockedReason = null;
        int next = Math.floorMod(session.quarterTurns + 1, 4);
        try {
            session.plan = createPlan(selected.get(), player.getWorld(), session.anchor, next);
            session.quarterTurns = next;
            player.sendActionBar(Component.text(
                    "Prefab rotation: " + (next * 90) + "° · inspect, then right-click for a quote.",
                    NamedTextColor.YELLOW));
        } catch (RuntimeException error) {
            session.blockedReason = "Rotation could not be planned safely";
            plugin.getLogger().log(Level.WARNING, "Could not rotate prefab " + session.prefabId, error);
            player.sendMessage(Component.text(
                    "BLOCKED — This prefab could not be rotated safely: " + safeMessage(error),
                    NamedTextColor.RED));
        }
    }

    /** Exit prefab mode before the normal form cycle continues. */
    public void exitToForms(Player player) {
        clearRuntime(player);
        prefabs.clearSelection(player);
    }

    /** Clears anchor/quote only; PrefabCommandHandler owns clearing the selected entitlement. */
    @Override
    public void cancel(Player player) {
        clearRuntime(player);
    }

    @Override
    public void confirm(Player player, String opaqueToken) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.quote == null || session.token == null
                || opaqueToken == null || !constantTimeEquals(session.token, opaqueToken)) {
            player.sendMessage(Component.text(
                    "BLOCKED — There is no matching prefab confirmation. Nothing was spent.",
                    NamedTextColor.RED));
            return;
        }
        if (!sessionMatchesWand(player, session)) {
            clearRuntime(player);
            player.sendMessage(Component.text(
                    "BLOCKED — The offhand wand changed, so the prefab anchor and confirmation were cleared.",
                    NamedTextColor.RED));
            return;
        }
        if (session.quote.expiredAt(System.nanoTime(),
                session.plan.options().confirmationSeconds())) {
            session.clearQuote();
            session.blockedReason = "Confirmation expired; request a new quote";
            player.sendMessage(Component.text(
                    "BLOCKED — That confirmation expired. The anchor remains; right-click for a new quote.",
                    NamedTextColor.RED));
            return;
        }
        Optional<PrefabDefinition> selected = prefabs.selectedDefinition(player.getUniqueId());
        if (selected.isEmpty() || !selected.get().id().equals(session.prefabId)
                || !selected.get().contentHash().equals(session.contentHash)
                || prefabs.access(player, session.prefabId) != PrefabService.Access.ACCESSIBLE) {
            session.clearQuote();
            player.sendMessage(Component.text(
                    "BLOCKED — The prefab catalog or your access changed. Nothing was spent.",
                    NamedTextColor.RED));
            return;
        }

        PlacementQuote prior = session.quote;
        WaveRunner.CommitResult result = waveRunner.commit(player, session.plan, prior);
        switch (result.status()) {
            case STARTED -> {
                session.clearQuote();
                session.blockedReason = null;
                plugin.getLogger().info("Prefab placement confirmed player=" + player.getUniqueId()
                        + " prefab=" + session.prefabId + " hash=" + session.contentHash
                        + " anchor=" + session.anchor + " rotation=" + session.quarterTurns
                        + " cells=" + prior.printable().size()
                        + " activation_uses=" + session.plan.options().activationUses());
            }
            case QUOTED -> {
                setPendingQuote(session, result.quote());
                sendConfirmation(player, selected.get(), session, true);
            }
            case BLOCKED -> {
                session.clearQuote();
                // Resource stock is recomputed live; never retain a shortage as a frozen job.
                session.blockedReason = result.adjustableRefusal() ? null : result.reason();
            }
        }
    }

    public void clearRuntime(Player player) {
        sessions.remove(player.getUniqueId());
    }

    private void anchor(Player player, PrefabDefinition definition) {
        RayTraceResult hit = LivePlan.rayTrace(player, config);
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) {
            player.sendActionBar(Component.text(
                    "Aim at a block face; right-click sets the prefab anchor.",
                    NamedTextColor.YELLOW));
            return;
        }
        Block block = hit.getHitBlock();
        BlockFace face = hit.getHitBlockFace();
        BlockVector anchor = new BlockVector(block.getX() + face.getModX(),
                block.getY() + face.getModY(), block.getZ() + face.getModZ());
        int quarterTurns = initialRotation(player, definition, anchor);
        try {
            Plan plan = createPlan(definition, player.getWorld(), anchor, quarterTurns);
            String wandId = wandItems.identity(player.getInventory().getItemInOffHand()).wandId();
            if (wandId == null) {
                throw new IllegalStateException("The offhand Builders Wand has no identity");
            }
            sessions.put(player.getUniqueId(), new Session(
                    definition, wandId, player.getWorld(), anchor, quarterTurns, plan));
            player.sendMessage(Component.text("ANCHORED — " + definition.metadata().name()
                    + " at " + anchor.getBlockX() + ", " + anchor.getBlockY() + ", "
                    + anchor.getBlockZ() + ".", NamedTextColor.GREEN));
            player.sendMessage(Component.text(
                    "Walk around the preview. Shift-left rotates; left-click re-anchors; "
                            + "right-click requests the required placement confirmation.",
                    NamedTextColor.GRAY));
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not anchor prefab " + definition.id() + " for "
                            + player.getUniqueId(), error);
            player.sendMessage(Component.text("BLOCKED — The prefab could not be planned safely: "
                    + safeMessage(error), NamedTextColor.RED));
        }
    }

    private void requestQuote(Player player, PrefabDefinition definition, Session session) {
        WaveRunner.CommitResult result = waveRunner.commit(player, session.plan, null);
        switch (result.status()) {
            case QUOTED -> {
                setPendingQuote(session, result.quote());
                session.blockedReason = null;
                sendConfirmation(player, definition, session, false);
            }
            case BLOCKED -> {
                session.clearQuote();
                session.blockedReason = result.adjustableRefusal() ? null : result.reason();
            }
            case STARTED -> {
                plugin.getLogger().severe("Prefab plan bypassed mandatory confirmation for "
                        + player.getUniqueId());
                session.blockedReason = "Internal confirmation policy failure";
            }
        }
    }

    private Plan createPlan(PrefabDefinition definition, World world, BlockVector anchor,
                            int quarterTurns) {
        return planFactory.create(definition, world, anchor, quarterTurns,
                prefabs.settings().requireLogBlock(), prefabs.settings().confirmationSeconds());
    }

    private void setPendingQuote(Session session, PlacementQuote quote) {
        session.quote = quote;
        session.token = UUID.randomUUID().toString().replace("-", "");
    }

    private void sendConfirmation(Player player, PrefabDefinition definition,
                                  Session session, boolean refreshed) {
        PlacementQuote quote = session.quote;
        String prefix = refreshed ? "UPDATED " : "";
        player.sendMessage(Component.text(prefix + "READY — "
                + definition.metadata().name() + " — " + quote.plan().dims().primary() + " × "
                + quote.plan().dims().secondary() + " × " + quote.plan().dims().tertiary(),
                NamedTextColor.GREEN));
        player.sendMessage(Component.text("Place " + quote.printable().size()
                + " blocks; keep "
                + quote.kept() + " exact matches; 0 conflicts.", NamedTextColor.GRAY));
        player.sendMessage(Component.text("Uses: " + quote.budget().requiredUses()
                + " placement + " + quote.plan().options().activationUses() + " prefab = "
                + quote.requiredTotalUses()
                + (quote.usesBypass() ? " (bypassed)" : ""), NamedTextColor.GREEN));
        player.sendMessage(Component.text("Materials: "
                + materialSummary(quote.budget().requiredMaterials()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(
                "Make sure the preview and cleared area are correct. There is no wand undo.",
                NamedTextColor.GRAY));
        Component place = Component.text("[PLACE]", NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/wand prefab confirm " + session.token));
        Component cancel = Component.text(" [CANCEL]", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/wand prefab cancel"));
        player.sendMessage(place.append(cancel).append(Component.text("  Expires in "
                + quote.plan().options().confirmationSeconds() + "s", NamedTextColor.GRAY)));
    }

    private static String materialSummary(Map<Material, Integer> materials) {
        if (materials.isEmpty()) {
            return "none";
        }
        List<Map.Entry<Material, Integer>> sorted = new ArrayList<>(materials.entrySet());
        sorted.sort(Comparator.comparing(entry -> entry.getKey().name()));
        StringJoiner out = new StringJoiner(", ");
        for (Map.Entry<Material, Integer> entry : sorted) {
            out.add(WandItems.materialDisplayName(entry.getKey()) + " ×"
                    + String.format(Locale.US, "%,d", entry.getValue()));
        }
        return out.toString();
    }

    private int initialRotation(Player player, PrefabDefinition definition, BlockVector anchor) {
        if (!definition.metadata().allowRotation()) {
            return 0;
        }
        double dx = player.getLocation().getX() - (anchor.getBlockX() + 0.5);
        double dz = player.getLocation().getZ() - (anchor.getBlockZ() + 0.5);
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx >= 0 ? 3 : 1; // front east / west
        }
        return dz >= 0 ? 0 : 2; // front south / north
    }

    private boolean wandReady(Player player) {
        ItemStack wand = player.getInventory().getItemInOffHand();
        return player.hasPermission(WandItems.PERMISSION_USE)
                && wandItems.isWand(wand) && wand.getAmount() == 1;
    }

    private boolean sessionMatchesWand(Player player, Session session) {
        if (!wandReady(player)) {
            return false;
        }
        return java.util.Objects.equals(session.wandId,
                wandItems.identity(player.getInventory().getItemInOffHand()).wandId());
    }

    private static boolean constantTimeEquals(String expected, String supplied) {
        if (expected.length() != supplied.length()) {
            return false;
        }
        int difference = 0;
        for (int index = 0; index < expected.length(); index++) {
            difference |= expected.charAt(index) ^ supplied.charAt(index);
        }
        return difference == 0;
    }

    private static String safeMessage(RuntimeException error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearRuntime(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        clearRuntime(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        clearRuntime(event.getEntity());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (wandItems.isWand(event.getItemDrop().getItemStack())) {
            clearRuntime(event.getPlayer());
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (wandItems.isWand(event.getMainHandItem()) || wandItems.isWand(event.getOffHandItem())) {
            clearRuntime(event.getPlayer());
        }
    }
}
