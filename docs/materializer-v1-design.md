# BuildersWand Materializer — v1 Design

**Status:** Frozen for implementation · 2026-08-23
**Owner:** Jesse (JL-III) · **Target:** Paper 26.1.2, Java 21
**Source of truth for ported rules:** the voxels-slim Materializer as shipped
(`crates/voxel-core/src/materializer_blueprint.rs`, `crates/voxel-core/src/materializer.rs`,
`crates/voxel-game/src/state/materializer.rs`). This document is **self-contained**:
every ported rule is restated here in full so an implementer needs no access to voxels-slim.

---

## 1. Outcome statement

> When a player holding the Builders Wand right-clicks a surface, they anchor one of five
> parametric forms (Single, Diagonal, Box, Cylinder, Sphere/Capsule), stretch it to size with
> staged right-clicks while a per-player display-entity ghost shows exactly what will be built,
> and the final right-click prints it cell-by-cell using blocks consumed from their inventory —
> or is precisely refused with a message naming why, having changed and spent nothing.

## 2. Decision ledger (settled with the owner, 2026-08-23)

| # | Decision | Ruling |
|---|---|---|
| D1 | Preview | **Display-entity ghosts**: shrunken, glowing, full-bright `BlockDisplay`s. No resource pack. |
| D2 | Scale & catalog | **Materializer 1:1**: the five parametric forms, same dims bounds, 512-cell cap. |
| D3 | Blueprint sharing | **N/A** — the catalog is the five built-in forms. No schematic capture in v1. |
| D4 | Protection | **WorldGuard + Lands checks + permission nodes**, both plugins as soft-depends. |
| D5 | Form selection | **Command only**: `/wand form <name>` with tab-complete. No GUI, no gesture. |
| D6 | Material selection | **Offhand block** is the material; cost drawn from the whole inventory of that type. |
| D7 | Undo | **None** (1:1). Mining printed blocks reclaims them via vanilla drops. Mid-wave cancel/stop refunds unspent items. |
| D8 | Feedstock | Inventory items, **1 item per placed cell**, single material per print, all-or-nothing reserve at commit. |

## 3. Port disposition

### Ported 1:1 (same semantics, same numbers)

- The five-form catalog, hollow-by-construction geometry, and per-form dims bounds (§6).
- The 512-cell cap on **expanded plan cells** — refused exactly, never clamped (capsule
  excepted: the live gesture walks its length down, §7.6).
- Staged stretch gesture: right-click anchors → intermediate right-clicks lock →
  final right-click prints exactly what the ghost shows (§7).
- Anchor = the empty cell adjacent to the clicked face, like ordinary block placement.
- Skip-and-keep: occupied cells are kept as they stand, never charged; a blueprint
  *completes* a partial shape (§9). All-cells-already-built is a refusal.
- One wave per player at a time; a busy materializer refuses.
- Reveal cadence: ≤16 printable cells → 1 cell per 2 ticks; 17+ → 1 cell per tick (§10.2).
- Mid-wave: a cell that became occupied stops the wave *before* that cell and refunds the
  unspent remainder; lost permission likewise; completed cells always stay (§10.3).
- Creative mode prints free ("conjured feedstock"), through the identical code path (§11).
- Insufficient-materials refusal shape: nothing reserved, nothing placed, exact message (§13).
- No ghost for the Single form; the un-anchored ghost is a single block marking where the
  first click will anchor; the anchored ghost is every planned cell, re-derived from live aim.
- Material change (offhand swap) does **not** drop the anchor — the ghost re-forms.
  Form change **does** drop the anchor.
- No undo.

### Adapted (Minecraft equivalents, pinned here)

| voxels-slim | This plugin |
|---|---|
| Feedstock ledger (available/reserved) | Inventory items; reserve = remove at commit, refund = return on stop |
| `core:block.edit` permission per cell | WorldGuard `testBuild` + Lands block-place check + `builderswand.use` |
| Protected cuboid mantle | WorldGuard/Lands regions |
| Body pushed clear frame-up mid-wave | Living entities teleported up to the nearest safe spot; failure stops the wave (§10.4) |
| HUD plan card / stage hints | Action bar line (§8.4); refusals to chat |
| Ghost: 10% fill + purple edge frame | `BlockDisplay` at 0.8 scale, purple glow override, full-bright (§8) |
| B-wheel blueprint select | `/wand form <name>` |
| X-palette material select | Offhand block |
| Escape cancels | Left-click with the wand cancels; slot-switch away drops the gesture |
| Per-cell reach 16 + 14 overhang | Dropped: anchor reach 16 already bounds every cell to ≤ ~25 blocks (max extent 9) |
| Wave stop on unloaded chunk | Prevented instead: chunk tickets held for the wave's volume (§10.5) |

### Not ported (no Minecraft analog, or out of scope)

Planets/gravity frames and cell-frame honesty checks; the intake beam; recycle (`R`);
the tutorial/introduction gate; the per-cell "construction primitive" concept (cells are
just the material block — the form/primitive distinction collapses); plane flip (`Z`) —
no spare input on a vanilla client, so wall anchors always make walls and floor/ceiling
anchors always make floors (revisit as a `/wand` toggle if missed); the form wheel and
palette GUIs; `CancelMaterializerWave` player control (no input for it; quit/stop paths
cover the semantics); schematic capture; undo; Folia (Paper main-thread scheduler only).

---

## 4. The wand item

- `Material.STICK`, item name "Builders Wand" in gold, one lore line "A mystical wand!" —
  **display only, never parsed**.
- Identity and state live in the item's `PersistentDataContainer`:
  - `builderswand:wand` → `BYTE = 1` (identity flag; the only wand test anywhere).
  - `builderswand:form` → `STRING`, one of `single|diagonal|box|cylinder|sphere` (default `single`).
- The lore-regex identity system is deleted entirely. Anvil renames must not affect behavior.
- Wand detection: main hand only. Off-hand wand does nothing.

## 5. Selections

### 5.1 Form — `/wand form <single|diagonal|box|cylinder|sphere>`

- Tab-completed. Requires `builderswand.use` and a wand in the main hand
  (else: refusal message, §13). Writes the form to the held wand's PDC.
- Changing form drops any live anchor and re-forms the ghost (1:1 with the B-wheel).

### 5.2 Material — the offhand block

- The print material is the `Material` of the ItemStack in the player's off hand, if it is
  an **allowed material**: `isBlock() && isItem() && isSolid()`, minus the denylist
  `Tag.DOORS`, `Tag.BEDS`, `Tag.SHULKER_BOXES` (multi-block or content-carrying).
- The placed state is `material.createBlockData()` — the default block state, unrotated,
  never waterlogged. Stairs/logs/slabs print in their default orientation (documented
  limitation, accepted for v1; slabs are bottom slabs, 1 item = 1 slab block).
- Counting and consuming go by `Material` equality across the whole inventory, ignoring
  item meta (a renamed stone block is still stone — accepted v1 simplification), and
  skipping any item carrying this plugin's PDC keys.
- No valid offhand material → anchoring refuses with the hold-a-block message (§13).
  If the offhand becomes invalid mid-gesture, the gesture stays alive; the print click
  refuses with the same message until a valid block returns.

### 5.3 Orientation — from the clicked face (1:1)

Let the clicked face's outward unit vector be **N** (this is also the direction the form's
tertiary/height axis grows — away from the clicked surface).

- **Side face** (N horizontal) → **Wall** orientation. In-plane axes: **V** = up (0,1,0)
  and **L** (lateral) = screen-right as seen from outside the wall: `L = (N.z, 0, −N.x)`.
- **Top/bottom face** (N = ±Y) → **Floor** orientation. **S** (away) = the player's facing
  cardinal (yaw snapped to N/E/S/W) at anchor time; **L** = S rotated clockwise viewed
  from above: `L = (−S.z, 0, S.x)`. Height grows along N (up from a floor, down from a ceiling).
- Circles (cylinder rims, sphere shells) **center on the anchor cell**; rectangles
  (box, diagonal tread) grow **corner-anchored** from the anchor toward positive L/S/V,
  with negative drags handled by shifting the effective anchor (§7.5).

---

## 6. Form catalog (complete geometry)

Universal rules: every form's minimum is exactly one cell — the anchor. Volumetric shapes
are **hollow by construction**: a box is its boundary shell, a cylinder an open tube, a
sphere its surface. One material per print. Illegal dims are **refused naming the violated
bound, never clamped** (the live gesture keeps dims legal by clamping *measurement*, §7.4).

**Cap:** `MAX_WAVE_CELLS = 512` — a cap on **expanded plan cells** (kept cells included),
not bounding-box volume. Not configurable.

Cell coordinates below are offsets from the **effective anchor** in the (L, S-or-V, N) basis
of §5.3. Emission order (= placement order = ghost order): **N-major, then S/V, then L**.

### 6.1 Single
One cell: the anchor. Dims fixed (1,1,1). No gesture stages, no ghost — the first
right-click prints immediately (it is the tool's plain hand).

### 6.2 Diagonal (stair run)
Dims: `run` 1..8 (primary), `width` 1..5 (tread), tertiary fixed 1. Cells:
```
for step in 0..run-1:  for col in 0..width-1:
    cell = anchor + col·L + step·S + step·N        # floor: away-and-up
    (wall anchor: cell = anchor + col·L + step·UP + step·N)   # out-and-up
```
Solid tread, **not hollow**. Count = `run × width` (max 40). A ceiling anchor climbs
away-and-**down** (N = −Y), by the same formula.

### 6.3 Box (hollow cuboid shell)
Dims: p, s, t each 1..8 (L-span, S/V-span, N-span). Cells: all `(a,b,c)` with
`0 ≤ a < p, 0 ≤ b < s, 0 ≤ c < t` where
```
a==0 || a==p-1 || b==0 || b==s-1 || c==0 || c==t-1
```
Count = `p·s·t − max(p−2,0)·max(s−2,0)·max(t−2,0)`. Any extent of 1 or 2 leaves no
interior, so the box **is** the wall/floor/ceiling plate (subsumes a "panel" form).
Max: 8×8×8 = 296 cells.

### 6.4 Cylinder (open tube)
Dims: `step` 1..9 (size), `courses` 1..8 (length along N), tertiary fixed 1.
Radius `r = step − 1`. Per course `c in 0..courses-1`, the **rim** cells `(a,b)` in the
anchored plane (L × S for floor, L × V for wall):
```
step == 1  →  {(0,0)}                              # single column
step  > 1  →  { (a,b) : (2r−1)² ≤ 4(a²+b²) < (2r+1)² }
```
centered on the anchor; cell = anchor + a·L + b·(S or V) + c·N. Both ends and the bore
stay open. Rim sizes by step: 1→1, 2→8, 3→12, 4→16, 5→32, 6→28, 7→40, 8→40, 9→48.
Max: step 9 × 8 courses = 384 cells. Floor anchor → vertical tube; wall anchor →
horizontal tube growing out of the wall.

### 6.5 Sphere / Capsule (one-cell-thick shell)
Dims: `step` 1..7 (size), `length` 1..8 (axis cells along N), tertiary fixed 1.
Radius `r = step − 1`. The axis segment is the set of cells `{ anchor + k·N : 0 ≤ k < length }`.
A cell at integer offset **q** belongs to the shell iff
```
step == 1  →  q on the axis segment                # 1×length column
step  > 1  →  (2r−1)² ≤ 4·D²(q) < (2r+1)²
```
where `D²(q)` = squared Euclidean distance (in cells) from q to the nearest point of the
segment (clamp q's N-component into `[0, length−1]`, distance to that clamped point —
always an integer). `length == 1` is a sphere centered on the anchor; longer is a capsule
(two hemisphere caps + tube). The center/axis stays hollow. Counts: sphere by step
1→1, 2→18, 3→62, 4→98, 5→210, 6→350, 7→450; capsule adds `rim(r) × (length−1)` using the
§6.4 rim table (radius r = step−1). Largest legal print in the catalog: **step-7, length-2
capsule = 490 cells**. Sphere(7, 3) = 530 → over the cap (the gesture prevents it, §7.6;
the validator refuses it).

Cells inside terrain are kept and skipped at commit, so a ground-anchored dome simply
completes against the surface.

---

## 7. The stretch gesture

Per-player session state (in-memory only, never persisted):
`{ form, anchorCell, orientation (basis L/S/V/N + heading), locks[≤2], stage }`.

### 7.1 Stage counts (1:1)
Single → 0 locks (first click prints). Diagonal / Cylinder / Sphere → 1 lock.
Box → 2 locks. The right-click after the last lock **prints**.

### 7.2 Click routing (main hand holding wand)
`PlayerInteractEvent`, **guarded by `event.getHand() == EquipmentSlot.HAND`** (the
current code double-fires without this), event cancelled when consumed:

- **Right-click** (block or air):
  1. No anchor → if `getTargetBlockExact(16, FluidCollisionMode.NEVER)` hits a block:
     anchor at `hit + clickedFaceNormal`; freeze orientation per §5.3. Single form:
     skip straight to commit (§9). No target / no valid material → hint message, no anchor.
  2. Anchored, locks remain → lock the currently measured stage (§7.3).
  3. Anchored, no locks remain → **commit** exactly the currently measured plan (§9).
- **Left-click**: cancel — drop anchor and locks, remove ghost. (Escape equivalent.)
- Gesture also drops on: held-slot change away from the wand, wand leaves main hand,
  form change, death, world change, quit.

### 7.3 What each stage measures
After anchoring, the plan's un-locked extents **live-track the aim** every ghost update;
each lock freezes one axis at its currently measured value:

| Form | Lock 1 | Lock 2 | Final stretch (prints) |
|---|---|---|---|
| Box (floor) | S-span ("length") | L-span ("width") | height along N |
| Box (wall) | L-span ("width") | V-span ("height") | depth along N |
| Cylinder | size step (radius) | — | courses along N |
| Sphere | size step (radius) | — | axis length along N |
| Diagonal | tread width along L | — | run |

In-plane extents (box spans, diagonal width, circle radius) are measured in the anchored
plane. The **final N-axis stretch** is measured in the plane spanned by N and L, positioned
at the effective anchor. (voxels-slim derives stage order from a generic
horizontal-first/vertical-last rule; this table pins the same outcomes explicitly.)

### 7.4 Aim → measurement (1:1 priority order)
To read the aim in a measurement plane:
1. If the crosshair rests on a block within reach 16, project that block center into the plane.
2. Else intersect the eye ray with the plane.
3. Else (ray parallel/behind) project the aim direction into the plane and stretch to 64 blocks.

Extent from a signed in-plane offset: `extent = offset ≥ 0 ? offset + 1 : offset − 1`
(signed inclusive count, never 0). **Clamp each extent to its axis's legal bound**
(±8 spans, tread ±5) so live dims are always legal. Circle sizing:
`step = round(hypot(a, b)) + 1`, clamped 1..9 (cylinder) / 1..7 (sphere), where (a,b) is
the in-plane aim offset from the anchor.

### 7.5 Negative drags (1:1)
A negative extent shifts the **effective anchor** by `extent + 1` along that axis so the
clicked cell stays the near corner, and the span becomes `|extent|`. Circles are centered
and have no negative case. Diagonal: the first stage picks the tread axis from the dominant
horizontal drag; while the tread is a single column the climb may pivot to any of the four
cardinals; dragging backward with a multi-column tread half-turns the heading and mirrors
the width. Wall-anchored diagonals climb away-only.

### 7.6 Capsule walk-down (1:1)
Only the capsule can exceed 512 cells inside its bounds. When the measured length would,
walk `length` down (`length −= 1` while illegal, min 1) so the ghost shrinks instead of
vanishing. Any other measurement failure falls back to the 1×1×1 representative.

---

## 8. Ghost preview (display entities)

### 8.1 What shows when (1:1)
- **Single**: never any ghost.
- Multi-cell form, wand held, **no anchor**: one ghost block marking exactly the cell the
  first click would anchor (only while aiming at a block within reach 16).
- **Anchored**: every planned cell, re-derived from live aim every update — the exact same
  expansion function the commit submits (one shared pure implementation, §16).
- **Hidden** while the player's own wave is printing, and removed on every gesture-drop
  event in §7.2.

### 8.2 Entity recipe (per cell)
`BlockDisplay`, spawned `persistent(false)`, `visibleByDefault(false)` +
`player.showEntity(plugin, e)` (owner-only):
- `block` = the selected material's `createBlockData()`
- transformation: uniform **scale 0.8**, translation (0.1, 0.1, 0.1) (centered in the cell)
- `glowing(true)`, `glowColorOverride = Color.fromRGB(0x9E, 0x3D, 0xFF)` (the Materializer's
  beam-shell purple), `brightness(15, 15)` (full-bright hologram)
- `teleportDuration(2)` so cell moves interpolate over 2 ticks

### 8.3 Update loop
One repeating task, every **2 ticks** (`ghost.update-ticks`), iterates players with a
wand main-hand: compute the plan cell set, diff against that player's live ghost entities
by cell position, spawn/remove/re-block only the difference. Budget is structural:
≤ 490 entities per player by the catalog cap. Ghosts include kept cells (1:1 — the plan
shows every cell; keeping happens at commit).

### 8.4 Action bar (every ghost update while anchored)
```
{Form} {p}×{s}×{t} · {cells} cells, {kept} kept · {Material} ×{printable} (have {n}) · {hint}
```
`{hint}` ∈ `RIGHT locks length` / `RIGHT locks width` / `RIGHT locks height` /
`RIGHT locks radius` / `RIGHT prints · LEFT cancels`. Un-anchored with wand held:
`Aim at a surface; RIGHT-CLICK anchors there.` (or the no-material message, §13).
`{kept}` may be approximate pre-commit (computed from current world state); the commit
recomputes it authoritatively.

## 9. Commit pipeline (validation order, adapted 1:1)

On the printing right-click, in this exact order — the first failure refuses the whole
print, and **refusal changes and spends nothing**:

1. **Busy**: this player already has an active wave → refusal.
2. **Material**: offhand is an allowed material (§5.2) → else refusal.
3. **Dims legal**: bounds + ≤512 expanded cells (defense in depth; the gesture already
   guarantees it).
4. **Expand cells** (§6) in emission order.
5. **Protection, per cell**: WorldGuard `testBuild` (with bypass), then Lands block-place
   check (§12), on every cell → first denial refuses, naming the position and plugin.
6. **Classify**: printable = the cell's block `isReplaceable()` (vanilla replaceable:
   air, fluids, short grass, snow layers, …— printed over); kept = anything else,
   including block-entity holders. Kept cells are never charged.
7. **All kept** → `AllCellsAlreadyBuilt` refusal.
8. **Single form only**: cell intersects a living entity's bounding box → `BodyInTheWay`
   refusal. (Multi-cell waves are not refused for bodies; they push, §10.4.)
9. **Feedstock**: `need = printable count`. Creative mode → skip. Else count the material
   across the inventory; `have < need` → insufficient refusal. Else **reserve**: remove
   exactly `need` items now (storage slots 0..35 in index order first, offhand last).
10. **Start the wave**: add plugin chunk tickets over the plan's chunks, schedule §10.

Chat on success: `Printing {form}: {printable} cells ({kept} kept).`

## 10. Wave execution

### 10.1 Structure
One wave object per player: ordered printable cells, material, `completed`, `reserved`
(item count still unspent), chunk tickets. All work on the main thread via the scheduler —
cells within ~25 blocks of a commit are cheap; the cadence is the pacing.

### 10.2 Cadence (1:1)
Printable count ≤ 16 → one cell every **2** ticks; ≥ 17 → one cell every tick.
Always at most one cell per tick. (Config keys in §14.3; defaults are the 1:1 values.)

### 10.3 Per-cell step (at its scheduled tick)
1. Re-check protection on this cell (§12) → denial **stops** the wave before it.
2. Cell no longer replaceable → **stop** before it (mid-wave is stop, not skip — 1:1).
3. Living entity intersects → push (§10.4); failure → **stop**.
4. Capture the cell's prior `BlockState`, then place `material.createBlockData()` with
   `applyPhysics = true` (sand may fall, water may flow — vanilla-honest, accepted), play
   the block's place sound at the cell.
5. Queue the change to LogBlock under the **player's own actor** (place if the prior
   state was air, replace otherwise — so a rollback restores printed-over grass/water).
   Plugin placements fire no `BlockPlaceEvent`, so without this the print is invisible
   to grief investigation (§12).
6. `completed += 1`, `reserved −= 1`. Last cell → settle: release tickets, action bar
   `Printed {printable} {material}.`

**Stop** = release tickets + refund `reserved` items via `player.getInventory().addItem`
(leftovers drop at the player, standard behavior) + chat message (§13). Completed cells
always stay — mining an already-printed cell never stops a wave.

### 10.4 Body push (adapted from frame-up push)
Before placing a cell whose block-space intersects a living entity's bounding box:
scan y upward from the entity's feet, up to +12, for the lowest y where the two blocks at
(entityBlockX, y, entityBlockZ) and (…, y+1, …) are both passable **and** neither is an
unplaced cell of this wave; teleport the entity's feet there (keep x/z fractions, yaw,
pitch). No such y → stop the wave.

### 10.5 Lifecycle edges
- **Quit**: stop the wave during `PlayerQuitEvent` (refund lands in the inventory before
  it saves). Ghosts and gesture state discarded.
- **Plugin disable / server stop**: stop every wave synchronously, refunding each owner.
- Crash mid-wave loses the reserved remainder (accepted; noted, not mitigated in v1).

## 11. Feedstock rules

1. Cost = **1 item per printable cell**, of the offhand-selected material. Kept cells free.
2. Reserve at commit (all-or-nothing), spend 1 per placed cell, refund the unspent
   remainder on any stop. Invariant: `reserved == printable − completed` at all times.
3. Insufficient → the §13 message; nothing reserved, nothing placed.
4. **Creative**: `GameMode.CREATIVE` skips counting, reserving, spending, and refunding
   entirely — same pipeline otherwise. The action bar shows `(creative)` instead of
   `(have {n})`.
5. No provenance on printed blocks: they are plain world blocks; vanilla mining/drops are
   the reclaim story (a silk-less glass mine loses the glass — vanilla-honest, matches the
   Materializer's reclaim-≤-cost principle).

## 12. Protection bridge

One `ProtectionBridge` interface: `boolean canBuild(Player, Location)`. Composed of hooks
resolved at enable time, each behind a classpath/plugin-presence check:
- **WorldGuard** (softdepend): `WorldGuard.getInstance().getPlatform().getRegionContainer()
  .createQuery().testBuild(loc, wrappedPlayer)`, honoring WG bypass.
- **Lands** (softdepend): the LandsIntegration area lookup + block-place flag check for the
  player at that location (use the Lands API's documented block-place test).
- Absent plugin → its hook contributes `true`. All hooks must pass.

**Placement logging (LogBlock)** — a sibling `PlacementLogger` seam (softdepend,
no-op when LogBlock is absent): after every placed cell, queue the change to LogBlock's
`Consumer` attributed to the printing player's `Actor` — `queueBlockPlace` when the
prior state was air, `queueBlockReplace(before, after)` otherwise. The wand is the
player acting, so lookups and per-player rollbacks behave exactly as hand placement;
never log under a `#builderswand`-style machine actor. (Owner ruling 2026-08-25:
Theatria runs LogBlock, not CoreProtect.)

## 13. Messages (exact strings)

Chat, red, on refusal — every refusal ends with nothing changed:

| Condition | Message |
|---|---|
| Insufficient materials | `Need {need} {Material}; have {have}. Nothing changed or spent.` |
| No/invalid offhand material | `Hold a placeable block in your off hand to choose the material.` |
| Denylisted material | `{Material} can't be printed (multi-block or content-carrying).` |
| Busy | `A print is already running ({completed}/{total}).` |
| All cells built | `Every cell is already built. Nothing to print.` |
| Protection | `Blocked by {WorldGuard\|Lands} at {x}, {y}, {z}. Nothing changed or spent.` |
| Body in the way (Single) | `A body is in the way at {x}, {y}, {z}.` |
| No wand for /wand form | `Hold the Builders Wand to set its form.` |
| No permission | `You don't have permission to use the Builders Wand.` |

Wave stop, chat, gold:
`{completed}/{total} cells placed; {reason}. The unplaced blocks were refunded.`
with `{reason}` ∈ `a block appeared in the way at {x}, {y}, {z}` /
`build permission was lost at {x}, {y}, {z}` / `a body could not be moved clear` /
`you left the game` / `the server is stopping`.

Success: commit chat `Printing {form}: {printable} cells ({kept} kept).` (green);
settle action bar `Printed {printable} {material}.`
Material names: `Material.name()` lower-cased with spaces (`smooth_stone` → `smooth stone`).

## 14. Commands, permissions, config

### 14.1 Commands
- `/wand give [player]` — gives a wand (self if no arg). Perm `builderswand.give`.
- `/wand form <single|diagonal|box|cylinder|sphere>` — sets the held wand's form.
  Perm `builderswand.use`. Tab-completes the five names.
- `/wand` — shows held wand's form, current material read, and usage.
- **Removed**: `/wand set`, `/wand speed`, `/wand debug`.

### 14.2 Permissions (plugin.yml)
- `builderswand.use` — use the wand and `/wand form`. Default `true`.
- `builderswand.give` — `/wand give`. Default `op`.

### 14.3 config.yml (all keys, with defaults = the pinned values)
```yaml
cadence:
  small-print-max-cells: 16     # ≤ this many printable cells → slow cadence
  small-print-ticks-per-cell: 2
  large-print-ticks-per-cell: 1
ghost:
  update-ticks: 2
  scale: 0.8
  glow-rgb: "9E3DFF"
anchor-reach: 16
```
The 512-cell cap and dims bounds are **constants, not config** (invariants, 1:1).

### 14.4 plugin.yml
`api-version: '26.1'` (Paper 26.1.2 accepts up to `'26.2'`; if the server warns at boot,
use `'26.1.2'`). `softdepend: [WorldGuard, Lands, LogBlock]`.

## 15. Existing-code disposition

| File | Fate |
|---|---|
| `wand/Wand.java` | Rewritten: PDC identity + form read/write; lore-regex parsing deleted |
| `wand/WandMode.java`, `wand/WandDimensions.java`, `utils/Cube.java`, `utils/LocationObject.java` | **Deleted** (forms replace shapes; develop2's CUBE/CUBE_HOLLOW/CUBE_WIRE modes are superseded by the Box form, whose extent-1 degenerate is the solid plate) |
| `wand/WandData.java` | Deleted (replaced by gesture session state) |
| `commands/WandGive.java` | Rewritten per §14.1 |
| `listeners/PlayerInteractListener.java` | Rewritten: hand guard, right/left routing into the gesture machine |
| `tasks/VisualizationTask.java` | **Deleted**; replaced by the display-entity `GhostService` (its broadcast particles, broadcast chat spam, and mismatched box math do not carry forward) |
| `workload/Workload*.java`, `DistributedFiller.java` | Deleted; the wave runner supersedes the time-budget queue (cadence-paced, per-player, with refunds) |
| `utils/ConfigManager.java` | Rewritten: loads §14.3 config.yml |
| `utils/Result.java`, `Ok.java`, `Err.java` | Kept (used for refusal plumbing) |
| `build.gradle` | paper-api → `io.papermc.paper:paper-api:26.1.2.build.74-stable`, `runServer` → `26.1.2`; enginehub repo + `com.sk89q.worldguard:worldguard-bukkit:7.0.17` compileOnly; jitpack repo + `com.github.Angeschossen:LandsAPI:7.25.4` compileOnly; LogBlock via `compileOnly files("libs/logblock.jar")` — the production server's own jar, no public artifact is current (verified 2026-08-25); JUnit 5 (other coordinates resolved 2026-08-23 against live repos) |

## 16. Package layout

```
com.playtheatria.buildersWand
  BuildersWandPlugin              # enable/disable, wiring, disable-time wave stops
  wand/    WandItems              # item build, PDC identity, form read/write
  form/    Form (enum), Dims, Orientation, Expansion   # §5.3, §6 — PURE, no Bukkit World
  gesture/ GestureSession, GestureListener             # §7
  ghost/   GhostService                                # §8
  wave/    Wave, WaveRunner, Feedstock                 # §9–§11
  protect/ ProtectionBridge, WorldGuardHook, LandsHook # §12
  command/ WandCommand                                 # §14.1
  config/  PluginConfig                                # §14.3
```
`form/` must be pure functions over vectors (BlockVector in, cell list out) so the
geometry is unit-testable without a server. The ghost and the commit call the **same**
expansion — one truth, never two implementations.

## 17. Test vectors (golden, from the voxels-slim test suite — must pass)

Unit tests (JUnit 5, no server):
- Cylinder rim cells per course by step: `1→1, 2→8, 3→12, 4→16, 5→32, 6→28, 7→40, 8→40, 9→48`;
  largest cylinder step 9 × 8 courses = **384**.
- Sphere shell by step: `1→1, 2→18, 3→62, 4→98, 5→210, 6→350, 7→450`;
  capsule(step, L) = sphere(step) + rim(step−1)·(L−1); capsule(7, 2) = **490** (largest legal).
- Box: 3×3×3 → **26**; 8×8×8 → **296**; any extent ≤2 → solid (count = p·s·t).
- Diagonal: run×width, max 8×5 = **40**.
- Sphere(7, 3) = 530 → **refused** (`TooManyCells`-equivalent), never clamped;
  the live-gesture walk-down yields (7, 2).
- Dims outside per-axis bounds → refused naming the bound.
- Negative-extent effective-anchor shift: clicked cell remains the near corner.
- Emission order is N-major → S/V → L and deterministic.

## 18. Acceptance criteria (playtest)

1. `/wand give` + stone in offhand + `/wand form box`: right-click ground anchors; a purple
   glowing shrunken-block ghost tracks the aim as a rectangle; three more right-clicks lock
   length, lock width, stretch height, print. Blocks appear one per tick with place sounds.
2. Printing 20 cells with 18 stone: refused with `Need 20 stone; have 18. Nothing changed
   or spent.` — inventory untouched, no blocks placed.
3. A box printed over a partially built wall keeps the existing blocks, charges only the
   missing cells, and the action bar's `kept` count matches.
4. Left-click cancels; switching hotbar slots drops the ghost; `/wand form` mid-gesture
   drops the anchor; swapping the offhand block mid-gesture re-colors the ghost in place.
5. Sphere at max radius stretched long walks its length down instead of the ghost vanishing.
6. A second print during an active wave refuses with the busy message.
7. Standing inside the print volume: the wave lifts the player on top instead of
   entombing them.
8. In a WorldGuard region / Lands claim without build rights, commit refuses naming the
   position; with rights it prints.
9. Logging out mid-wave: unplaced blocks are back in the inventory on rejoin.
10. Creative mode prints with no items consumed.
11. Single form: right-click places exactly one offhand-material block, one item consumed,
    no ghost ever.
12. Vanilla client, no resource pack, no client mod — everything above holds.
13. With LogBlock running: a lookup on a printed cell attributes the placement to the
    printing player; printing over tall grass records a replace whose rollback restores
    the grass; rolling back the player reverts the whole print.

## 19. Out of scope / future (recorded, not designed)

Schematic capture and printing; resource-pack translucent ghost tier (server-pushed
stacked pack, baked ghost models); plane flip (shelf floors / standing walls); a form GUI;
undo; oriented placement (stairs facing the player); mixed-material prints; Folia support;
sounds/FX beyond per-cell place sounds.
