# Implementation spec — BuildersWand Materializer v1

> **Historical frozen spec:** Later owner-approved additions add water printing, PDC
> Uses/provenance, Denarii Use restoration, durable recognition stats, and left-click-first controls. See
> [`README.md`](../../README.md) for current behavior; those additions supersede conflicting names
> controls, and resource rules below without silently rewriting this frozen implementation record.

**Date:** 2026-08-23 · **Branch:** `feature/materializer-v1` (based on `develop2` @ 9a4a7bd)
**Worktree:** `/Users/jesse/Development/BuildersWand-worktrees/materializer-v1`
**Design contract:** [`docs/materializer-v1-design.md`](../materializer-v1-design.md) — in this
same repo. Where this spec says "design §N", open that file and implement that section
**exactly as written**. The design doc is frozen; this spec is frozen. Neither asks you to
make a design choice. If they ever appear to conflict, the design doc wins and the conflict
must be reported, not resolved silently.

---

## 1. Outcome sentence

When a player holding the Builders Wand right-clicks a surface, they anchor one of five
parametric forms (Single, Diagonal, Box, Cylinder, Sphere/Capsule), stretch it to size with
staged right-clicks while a per-player display-entity ghost shows exactly what will be
built, and the final right-click prints it cell-by-cell using blocks consumed from their
inventory — or is precisely refused with a message naming why, having changed and spent
nothing.

## 2. Recorded authority

- **Design contract:** `docs/materializer-v1-design.md` (this repo), frozen 2026-08-23.
  Its §2 decision ledger D1–D8 records the owner's rulings verbatim (display-entity
  ghosts; Materializer 1:1 scale/catalog; no capture; WorldGuard + Lands + perms;
  `/wand form` command only; offhand material; no undo; 1-item-per-cell feedstock).
- **Ported source of truth:** the voxels-slim Materializer as shipped (owner's other
  project). Key pins already extracted into the design doc: 512-cell cap
  (`voxel-core/src/materializer.rs:42`, owner ruling 2026-08-13 "let's raise to 512 and
  keep things hollow"); form catalog and bounds
  (`voxel-core/src/materializer_blueprint.rs:76-104`, `:209-274`); stage order (owner:
  "first click is x or z, second click is the other of x or z, and last click is y");
  skip-and-keep at confirm (`voxel-sim/src/sim/materializer.rs:1871-1880`); mid-wave
  stop-not-skip (`:2752-2777`); reveal cadence (`:20-37`). You never need to open
  voxels-slim: every rule is restated in the design doc with formulas and golden numbers.
- **Ghost scale:** pinned 0.8 (owner saw the 0.95 alternative and the rationale for 0.8;
  it is config-tunable via `ghost.scale`).
- **Placement logging (amendment 2026-08-25):** owner ruling "We are using logblock not
  core protect" — every printed cell is queued to LogBlock under the player's own Actor
  so lookups and rollbacks work exactly as for hand placement (design §10.3 step 5, §12).

## 3. Scope and non-goals

Scope: exactly design §1–§18. Non-goals: design §19 — no schematic capture, no resource
pack, no plane flip, no GUI, no undo, no oriented placement, no mixed materials, no Folia.
Do not add features, commands, config keys, permissions, or messages beyond those named.

## 4. Current-state map (branch `develop2` @ 9a4a7bd)

All paths under `src/main/java/com/playtheatria/buildersWand/`:

| File | Contents today | Fate |
|---|---|---|
| `BuildersWand.java` | Plugin main: wires ConfigManager, WandGive, VisualizationTask, WorkloadRunnable, DistributedFiller | **Rename** to `BuildersWandPlugin.java`, rewrite (§5.1) |
| `commands/WandGive.java` | `/wand give|debug|set|speed` over lore-encoded wands | **Delete**; replaced by `command/WandCommand.java` |
| `listeners/PlayerInteractListener.java` | Right-click → mode switch (CUBE/CUBE_HOLLOW/CUBE_WIRE) → fill; no `getHand()` guard | **Delete**; replaced by `gesture/GestureListener.java` |
| `tasks/VisualizationTask.java` | 20-tick broadcast particle box, math disagrees with filler | **Delete**; replaced by `ghost/GhostService.java` |
| `utils/ConfigManager.java` | One mutable field, no config.yml | **Delete**; replaced by `config/PluginConfig.java` |
| `utils/Cube.java` | Unused scaffold-shape calc | **Delete** |
| `utils/LocationObject.java` | List+bounds holder for the fillers | **Delete** |
| `utils/Result.java`, `utils/Ok.java`, `utils/Err.java` | Rust-style result types | **Keep unchanged** |
| `wand/Wand.java` | Lore-regex wand identity + `getTargetBlock` (UP-face-only raytrace) | **Delete**; replaced by `wand/WandItems.java` |
| `wand/WandData.java`, `wand/WandMode.java`, `wand/WandDimensions.java` | Lore-parsed state records | **Delete** |
| `workload/Workload.java`, `workload/WorkloadRunnable.java`, `workload/DistributedFiller.java` | Time-budgeted stone-filler queue | **Delete**; replaced by `wave/` package |

Root files: `build.gradle` (paper-api `1.21.4-R0.1-SNAPSHOT`, run-paper 2.3.1, Java 21),
`src/main/resources/plugin.yml` (main `com.playtheatria.buildersWand.BuildersWand`,
api-version `'1.21'`). No test sources exist. `settings.gradle` names the project; leave it.

## 5. The change, seam by seam

Every new file below is under `src/main/java/com/playtheatria/buildersWand/` unless noted.
Import Bukkit types from `org.bukkit.*` / `io.papermc.paper.*` as they exist in
paper-api `26.1.2.build.74-stable` (javadocs: https://jd.papermc.io/paper/26.1.2/).

### 5.1 `BuildersWandPlugin.java` (renamed main class)

`public final class BuildersWandPlugin extends JavaPlugin`.
`onEnable()` order: load `PluginConfig` (saves default `config.yml` via
`saveDefaultConfig()`); construct `WandItems` (needs plugin for `NamespacedKey`);
construct `ProtectionBridge.composite(this)`; construct
`PlacementLogger.composite(this)`; construct `WaveRunner`; construct
`GhostService`; register `GestureListener` and `WandCommand`
(`getCommand("wand").setExecutor(...)` + `setTabCompleter(...)`); start GhostService's
repeating task. `onDisable()`: `waveRunner.stopAll(StopReason.SERVER_STOPPING)` then
`ghostService.clearAll()`. No other logic in the main class.

### 5.2 `config/PluginConfig.java`

Immutable holder loaded once in `onEnable` from `config.yml` (design §14.3 keys, with
those defaults). Fields: `int smallPrintMaxCells, smallPrintTicksPerCell,
largePrintTicksPerCell, ghostUpdateTicks, anchorReach; float ghostScale; Color ghostGlow`
(parse `ghost.glow-rgb` hex string via `Color.fromRGB(Integer.parseInt(s, 16))`).
Ship `src/main/resources/config.yml` with exactly the design §14.3 content.

### 5.3 `wand/WandItems.java`

- `public static final String KEY_WAND = "wand"; KEY_FORM = "form";` — used as
  `new NamespacedKey(plugin, KEY_WAND)` etc. (construct keys once in the constructor).
- `ItemStack createWand(Form form)`: `Material.STICK`; `itemName` =
  `Component.text("Builders Wand").color(NamedTextColor.GOLD)`; lore = single line
  `Component.text("A mystical wand!")`; PDC: wand key → `PersistentDataType.BYTE` value 1,
  form key → `PersistentDataType.STRING` value `form.key()`.
- `boolean isWand(ItemStack)`: null-safe; true iff PDC has the wand key. **Never inspect
  lore or display name for behavior** (design §4).
- `Form getForm(ItemStack)`: PDC string → `Form.fromKey`, defaulting to `Form.SINGLE` if
  absent/unknown. `void setForm(ItemStack, Form)` writes the PDC string.

### 5.4 `form/` — the pure geometry package

No `org.bukkit.World`, `Block`, `Player`, or scheduler imports anywhere in this package —
only data classes (`org.bukkit.util.BlockVector`, `org.bukkit.block.BlockFace`). This is
what makes §7 unit tests run without a server.

**`Form.java`** — `public enum Form { SINGLE, DIAGONAL, BOX, CYLINDER, SPHERE }` with
`int lockStages()` (SINGLE→0, DIAGONAL/CYLINDER/SPHERE→1, BOX→2), `String key()`
(lower-case name), `static Optional<Form> fromKey(String)`.

**`Dims.java`** — `public record Dims(int primary, int secondary, int tertiary)` plus
`static Result<Dims, String> validated(Form form, int primary, int secondary, int tertiary)`
enforcing exactly the design §6 bounds table and the 512 expanded-cell cap
(via `Expansion.cellCount`). The error string names the violated bound and the offending
value (e.g. `"box primary 9 exceeds max 8"`, `"sphere(7,3) is 530 cells; max 512"`).
**Never clamp** (design: refused, never clamped).

**`Orientation.java`** — `public record Orientation(BlockVector l, BlockVector s,
BlockVector n, boolean wall, BlockFace heading)` where l/s/n are unit axis vectors
(design §5.3: for a wall, `s` is UP and `n` the clicked face's outward normal with
`l = (n.z, 0, −n.x)`; for floor/ceiling, `s` = player-yaw cardinal, `l = (−s.z, 0, s.x)`,
`n` = clicked face normal ±Y). `static Orientation fromClick(BlockFace clickedFace,
float playerYaw)`. Yaw→cardinal: Bukkit yaw 0=south, 90=west, 180=north, 270=east;
snap to nearest.

**`Expansion.java`** — all static, the **single geometry truth**:
- `public static final int MAX_WAVE_CELLS = 512;`
- `static List<BlockVector> cells(Form, Dims, Orientation)` — offsets from the effective
  anchor, exactly the design §6.1–§6.5 membership rules, emitted N-major → S/V → L.
- `static int cellCount(Form, Dims)` — closed-form for box/diagonal
  (design §6.3/§6.2), generated-and-counted for cylinder/sphere/capsule.
- `static List<int[]> rim(int step)` — the §6.4 integer band; package-visible for tests.
- Sphere membership per §6.5: `D²` to the clamped axis point, band
  `(2r−1)² ≤ 4·D² < (2r+1)²`, `step==1` special-cased to the axis column.

**`Measurement.java`** — all static, pure:
- `static int extentFromOffset(int cellOffset)` — `cellOffset >= 0 ? cellOffset + 1 :
  cellOffset - 1`; never returns 0. (Callers produce the integer cell offset per axis as
  `(int) Math.floor(aimCoordinate) − anchorCellCoordinate` in that axis's basis.)
- `static int clampExtent(int extent, int axisMax)` — magnitude-clamp preserving sign.
- `static int radiusStep(double a, double b, int max)` —
  `Math.min(max, (int)Math.round(Math.hypot(a, b)) + 1)`, min 1.
- `static int negativeAnchorShift(int extent)` — `extent < 0 ? extent + 1 : 0` (the
  effective-anchor shift of design §7.5).

### 5.5 `gesture/` — session + listener

**`GestureSession.java`** — mutable per-player state: `Form form; UUID worldId;
BlockVector anchor; Orientation orientation; Integer lock1; Integer lock2;` plus
`int stage()` derived from which locks are set. One instance per player kept in a
`Map<UUID, GestureSession>` inside `GestureListener`. No persistence.

**`GestureListener.java`** — `implements Listener`. Events:
- `PlayerInteractEvent`: first line `if (event.getHand() != EquipmentSlot.HAND) return;`
  then `if (!wandItems.isWand(event.getItem())) return;`. Route per design §7.2:
  right-click = anchor / lock / print; left-click = cancel. Call
  `event.setCancelled(true)` when the click was consumed by the wand. Anchor targeting:
  one call to `player.rayTraceBlocks(config.anchorReach, FluidCollisionMode.NEVER)` —
  null-check the result, `getHitBlock()`, and `getHitBlockFace()`; no hit → send the §13
  aim hint, do not anchor. Any clicked face is valid (the old `Wand.getTargetBlock`
  UP-only rule is gone). Anchor cell = hit block + face normal.
**`LivePlan.java`** (same package) — the one place live plans are derived, used by both
this listener (lock/print clicks) and `GhostService` (updates):
`static Optional<Plan> derive(Player player, GestureSession session, PluginConfig config)`.
Implements the aim math of design §7.3–§7.6 using `Player#getEyeLocation` and ray–plane
intersection with `org.bukkit.util.Vector`, `Measurement` for extents/radius/anchor-shift,
and the capsule walk-down loop. Returns empty when no valid material or (for SINGLE) when
un-anchored. `Plan` is `wave/Plan.java`: `public record Plan(World world, Form form,
Dims dims, BlockVector effectiveAnchor, List<Location> cells, Material material)` with
`cells` = `Expansion.cells(...)` offsets translated to absolute world locations, in
emission order.
- Print click → `LivePlan.derive(...)` → `waveRunner.commit(player, plan)`. Refusals from
  commit are already messaged by WaveRunner; the gesture survives a refused print
  (design: the gesture stays alive) except after a successful commit, which clears it.
- `PlayerItemHeldEvent`, `PlayerDropItemEvent` (wand), `PlayerDeathEvent`,
  `PlayerChangedWorldEvent`, `PlayerQuitEvent`: clear that player's session + ghosts.
  Quit additionally calls `waveRunner.stopFor(player, StopReason.PLAYER_QUIT)`.
- Material read: `player.getInventory().getItemInOffHand()` → allowed iff
  `type.isBlock() && type.isItem() && type.isSolid()` and not in
  `Tag.DOORS`/`Tag.BEDS`/`Tag.SHULKER_BOXES` (`Tag#isTagged(type)`), and the stack does
  not carry this plugin's PDC wand key.

### 5.6 `ghost/GhostService.java`

Repeating sync task every `config.ghostUpdateTicks`. Per player with a wand in the main
hand and form ≠ SINGLE: compute the preview cell set via `LivePlan.derive` (§5.5) —
un-anchored: the single would-be anchor cell if aiming at a block; anchored: the plan's
full cell list. Maintain `Map<UUID, Map<BlockVector, BlockDisplay>>`; diff and only
spawn/remove/re-block changed cells. Entity recipe exactly design §8.2:
`world.spawn(loc, BlockDisplay.class, e -> { ... })` with `setBlock(materialBlockData)`,
`setTransformation(new Transformation(new Vector3f(t, t, t), new Quaternionf(),
new Vector3f(s, s, s), new Quaternionf()))` where `s = config.ghostScale`,
`t = (1 − s) / 2f`; `setGlowing(true)`; `setGlowColorOverride(config.ghostGlow)`;
`setBrightness(new Display.Brightness(15, 15))`; `setTeleportDuration(2)`;
`setPersistent(false)`; `setVisibleByDefault(false)` then
`player.showEntity(plugin, e)`. Suppress (remove all) while that player's wave is active.
`clearAll()` removes every tracked entity. Also send the §8.4 action-bar line here via
`player.sendActionBar(Component.text(line))`.

### 5.7 `wave/` — feedstock, wave, runner

**`Feedstock.java`** — static over `PlayerInventory`:
`int count(PlayerInventory, Material)` (storage 0..35 + offhand, matching by
`ItemStack#getType()` only, skipping stacks whose PDC has the plugin wand key);
`void reserve(PlayerInventory, Material, int n)` removing n items — storage slots in
index order 0..35 first, offhand last; `void refund(Player, Material, int n)` via
`inventory.addItem(new ItemStack(material, n))` in max-stack chunks, dropping leftovers
at the player with `world.dropItemNaturally`.

**`Wave.java`** — plain class: `UUID owner; World world; Material material;
List<Location> printable; int completed; int reserved; int ticksPerCell;
Set<Chunk> tickets; boolean creative;`.

**`StopReason.java`** — enum `BLOCK_IN_WAY, PERMISSION_LOST, BODY_STUCK, PLAYER_QUIT,
SERVER_STOPPING` each carrying its §13 reason phrase.

**`WaveRunner.java`** — owns `Map<UUID, Wave>` and one repeating 1-tick task started
lazily while any wave exists.
- `commit(Player, Plan)` runs the design §9 validation order **exactly, in order**,
  sending the §13 refusal message at the first failure and returning. On success:
  reserve feedstock (skip when `player.getGameMode() == GameMode.CREATIVE`, and set
  `wave.creative`), add `chunk.addPluginChunkTicket(plugin)` for every chunk covering
  the plan, register the wave, message §13 success.
- Per scheduled cell: the design §10.3 steps in order (protection re-check → replaceable
  re-check → entity push → capture `BlockState before = block.getState()` → place
  `material.createBlockData()` with `block.setBlockData(data, true)` → place sound via
  `world.playSound(loc, block.getBlockSoundGroup().getPlaceSound(), 1.0f, 1.0f)` →
  `placementLogger.logPlacement(player, before, block.getState())`). Maintain the invariant `reserved == printable.size() − completed` for
  non-creative waves; if it ever fails, stop the wave with a refund of `reserved` and
  `getLogger().severe(...)` naming the counts (this is the invariant's runtime tooth).
- Body push exactly design §10.4 (scan up to +12 for two passable blocks not in the
  unplaced cell set; `entity.teleport` preserving fractional x/z and look).
- `stopFor(Player, StopReason)` / `stopAll(StopReason)`: refund `reserved` (non-creative),
  release tickets (`removePluginChunkTicket`), remove wave, send the §13 stop line.
- Settle (last cell): release tickets, remove wave, action-bar success line.

### 5.8 `protect/` — the bridge

**`ProtectionBridge.java`** — `public interface ProtectionBridge { boolean canBuild(
Player player, Location location); }` plus
`static ProtectionBridge composite(Plugin plugin)` which checks
`Bukkit.getPluginManager().getPlugin("WorldGuard") != null` / `...("Lands") != null` and
returns a composite that AND-s the present hooks (empty composite → always true).

**`WorldGuardHook.java`** —
```java
LocalPlayer lp = WorldGuardPlugin.inst().wrapPlayer(player);
if (WorldGuard.getInstance().getPlatform().getSessionManager()
        .hasBypass(lp, lp.getWorld())) return true;
return WorldGuard.getInstance().getPlatform().getRegionContainer()
        .createQuery().testBuild(BukkitAdapter.adapt(location), lp);
```
(`com.sk89q.worldguard.*`; `BukkitAdapter` is `com.sk89q.worldedit.bukkit.BukkitAdapter`,
resolved transitively from worldguard-bukkit; if the compiler cannot see it, add
`compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.9")` from the same enginehub repo.)

**`LandsHook.java`** —
```java
Area area = landsIntegration.getArea(location);   // me.angeschossen.lands.api.land.Area
if (area == null) return true;                    // wilderness
return area.hasRoleFlag(player.getUniqueId(), Flags.BLOCK_PLACE);
```
with `landsIntegration = LandsIntegration.of(plugin)` created once
(`me.angeschossen.lands.api.LandsIntegration`,
`me.angeschossen.lands.api.flags.type.Flags`). **Permitted adaptation point:** if one of
these three member names differs in LandsAPI 7.25.4, use that version's documented
equivalent of "may this player place a block at this location" — same semantics, confined
to this file.

**`PlacementLogger.java`** — `public interface PlacementLogger { void logPlacement(
Player player, BlockState before, BlockState after); }` plus
`static PlacementLogger composite(Plugin plugin)`: if
`Bukkit.getPluginManager().getPlugin("LogBlock") != null`, return `new LogBlockHook()`;
otherwise a no-op.

**`LogBlockHook.java`** —
```java
// de.diddiz.LogBlock.LogBlock, .Consumer, .Actor
private final Consumer consumer =
        ((LogBlock) Bukkit.getPluginManager().getPlugin("LogBlock")).getConsumer();

public void logPlacement(Player player, BlockState before, BlockState after) {
    Actor actor = Actor.actorFromEntity(player);
    if (before.getType().isAir()) {
        consumer.queueBlockPlace(actor, after);
    } else {
        consumer.queueBlockReplace(actor, before, after);
    }
}
```
**Permitted adaptation point (the only other one):** LogBlock has no current public
artifact, so this compiles against the production server's own jar (§5.10); if a member
name differs in that jar, use its documented equivalent of "queue a player-attributed
block place/replace" — same semantics, confined to this file. Always the player's own
`Actor`, never a `#builderswand`-style machine actor. Everything else in this spec is
exact.

### 5.9 `command/WandCommand.java`

`implements CommandExecutor, TabCompleter`, registered for `wand`. Behavior per design
§14.1: `give [player]` (perm `builderswand.give`), `form <key>` (perm `builderswand.use`,
requires `wandItems.isWand(mainHand)` else §13 message; sets PDC form; clears the
sender's gesture session), bare `/wand` prints form + material + usage lines.
Tab-complete: arg1 → `give`, `form`; arg2 after `form` → the five form keys.

### 5.10 Build files (verbatim)

**`build.gradle`** — keep the existing `java`/toolchain/`processResources` blocks and
group; change the rest to:
```gradle
version = 'v0.1.0-materializer'

repositories {
    mavenCentral()
    maven { name = "papermc-repo"; url = "https://repo.papermc.io/repository/maven-public/" }
    maven { name = "sonatype"; url = "https://oss.sonatype.org/content/groups/public/" }
    maven { name = "enginehub"; url = "https://maven.enginehub.org/repo/" }
    maven { name = "jitpack"; url = "https://jitpack.io" }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.17")
    compileOnly("com.github.Angeschossen:LandsAPI:7.25.4")
    compileOnly(files("libs/logblock.jar"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

test { useJUnitPlatform() }

tasks { runServer { minecraftVersion("26.1.2") } }
```

**`src/main/resources/plugin.yml`** — replace entirely:
```yaml
name: BuildersWand
version: '${version}'
main: com.playtheatria.buildersWand.BuildersWandPlugin
api-version: '26.1'
softdepend: [WorldGuard, Lands, LogBlock]
commands:
  wand:
    description: The Builders Wand materializer
    usage: /wand [give|form] [args]
permissions:
  builderswand.use:
    description: Use the Builders Wand and /wand form
    default: true
  builderswand.give:
    description: Give a Builders Wand
    default: op
```
(If the server boot log warns about `api-version: '26.1'`, change it to `'26.1.2'` —
that is the only permitted edit to this file.)

**`src/main/resources/config.yml`** — exactly the design §14.3 block.

**`libs/logblock.jar`** — the LogBlock jar from the production Theatria server, placed by
the owner before checkpoint 4; there is no current public LogBlock artifact (Maven and
GitHub releases are years stale — verified 2026-08-25), and downloading LogBlock from
anywhere else is not permitted. Add `libs/*.jar` to `.gitignore`. If the jar is absent,
`./gradlew build` fails compiling `LogBlockHook` — that failure means "ask the owner for
the jar", nothing else.

## 6. Invariants and their enforcement (same slice)

| Invariant | Tooth |
|---|---|
| One geometry truth: ghost and commit both call `Expansion.cells` | Structural: no other class may contain cell-membership logic; `ExpansionTest` is the only geometry test needed |
| Dims never clamped into legality | `DimsTest.sphereOverCapRefused` + `boxOverBoundRefused` assert `Err`, and `Expansion.cells` throws `IllegalArgumentException` if handed dims that `Dims.validated` would refuse |
| `reserved == printable − completed` (non-creative) | Runtime check in `WaveRunner` per-cell step: on violation, stop + refund + `severe` log |
| Refusal changes nothing | `commit` performs no world/inventory mutation before validation step 9 completes (code order enforces; acceptance test 2 verifies) |
| Golden geometry numbers | `ExpansionTest` vectors (§7) fail the build on drift |

## 7. Test plan (`src/test/java/com/playtheatria/buildersWand/form/`)

**`ExpansionTest.java`** — named methods, each asserting design §17's golden numbers:
`boxShellCounts` (3×3×3=26, 8×8×8=296, 2×5×4=40 solid), `cylinderRimCounts`
(steps 1..9 → 1, 8, 12, 16, 32, 28, 40, 40, 48), `largestCylinderIs384`,
`sphereShellCounts` (steps 1..7 → 1, 18, 62, 98, 210, 350, 450),
`capsuleFormula` (capsule(7,2)=490; capsule(3,4)=62+12·3=98),
`diagonalCounts` (run×width, 8×5=40), `emissionOrderIsNMajorDeterministic`
(3×3×3 box: assert the exact first and last five offsets twice, equal lists).

**`DimsTest.java`** — `sphereOverCapRefusedNamed530`, `boxPrimary9Refused`,
`diagonalWidth6Refused`, `neverClamped` (refused dims are not silently altered).

**`MeasurementTest.java`** — `extentSigning` (offset 3→4, −3→−4, 0→1),
`clampPreservesSign`, `radiusStepRounding` ((0,0)→1, (1,1)→2, (3.6,0)→5 capped by max),
`negativeAnchorShift` (extent −4 → shift −3).

**`OrientationTest.java`** — `wallLateralIsScreenRight` (all four faces enumerated
against design §5.3's formula), `floorLateralIsClockwiseOfHeading` (all four headings),
`yawSnapsToCardinal`.

Run with `./gradlew test` (part of `./gradlew build`). Manual/hostile paths are the
design §18 acceptance list — every refusal row there must be exercised in the playtest.

## 8. Acceptance conditions

Design §18, items 1–13, verbatim and frozen. Playtest door for the maintainer:

```
cd /Users/jesse/Development/BuildersWand-worktrees/materializer-v1 && ./gradlew runServer
```

(first run downloads the Paper 26.1.2 dev server; in the server console run
`op <username>`; connect a Minecraft 26.1.2 client to `localhost:25565`; WorldGuard/Lands
acceptance items need those plugin jars dropped into `run/plugins/` — absent jars mean
those two items are checked on the Theatria staging server instead, and everything else
must still pass).

## 9. Checkpoints (commit sequence on `feature/materializer-v1`)

Gate after every checkpoint: `./gradlew build` green (compiles + tests). Do not batch.

1. `gut: remove the shape-filler, retarget Paper 26.1.2` — apply §4's delete column,
   rename the main class, write §5.10's three build/resource files, leave the plugin
   compiling with an empty enable/disable.
2. `form: parametric catalog with golden-vector tests` — §5.4 + all §7 tests.
3. `wand: PDC identity, /wand command, config` — §5.3, §5.9, §5.2.
4. `wave: feedstock, protection bridge, placement logger, wave runner` — §5.7, §5.8
   (commit callable, no caller yet; requires `libs/logblock.jar` from the owner).
5. `gesture: anchor/lock/print wiring` — §5.5; Single and all forms print end-to-end.
   First `runServer` smoke here.
6. `ghost: display-entity preview and action bar` — §5.6.
7. `docs: README rewritten to describe the materializer` — replace README.md's TODO-era
   content with: what the tool does, the five forms, the gesture, the two permissions,
   config keys, and the design-doc/spec paths.

## 10. Forbidden moves

- **No lore or display-name parsing for behavior** — PDC only (design §4). The lore-regex
  system being deleted is the bug class this rule kills.
- **No second geometry implementation** — the ghost, the action-bar counts, and the commit
  all call `Expansion`. The old repo had preview math drift from fill math; that class of
  bug is banned structurally.
- **Never clamp dims into validity** in `Dims.validated` — refuse with the named bound.
  Only `Measurement` clamps (aim smoothing) and only the capsule walks down (design §7.6).
- **No broadcast anything**: no `Bukkit.getOnlinePlayers()` loops for messages, no
  `world.spawnParticle`, no `getConsoleSender().sendMessage` debug. Per-player
  `sendMessage`/`sendActionBar`/`showEntity` only.
- **`event.getHand() == EquipmentSlot.HAND` guard is mandatory** in every
  `PlayerInteractEvent` handler (the double-fire bug).
- **Main thread only**: no async schedulers touching world/inventory; the wave runner is
  a sync repeating task.
- **Do not make `MAX_WAVE_CELLS` or dims bounds configurable** — they are 1:1 invariants.
- **`applyPhysics = true`** on every placement (design §10.3); no floating-sand mode.
- **Do not swap the occupancy rules**: commit-time occupied = skip-and-keep;
  mid-wave occupied = stop-and-refund. They are deliberately different (design §9/§10.3).
- **Multi-cell waves never refuse for bodies** — push, and stop only when the push fails
  (design §10.4). Single-cell refuses (design §9 step 8).
- **No ProtocolLib, no packets, no resource packs** — Bukkit/Paper API only (owner
  ruling: zero packet dependencies).
- **Never fire synthetic `BlockPlaceEvent`s** for wave cells (side effects in every other
  listener) and **never log to LogBlock under a machine actor** — the player's own
  `Actor`, always, so their rollbacks include wand prints.
- **Do not touch `develop2` or `master`**; all commits on `feature/materializer-v1`; do
  not push without the owner's say-so.
