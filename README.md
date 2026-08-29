# BuildersWand — Materializer

A Paper survival-building plugin with live previews, inventory-backed textures, custom Uses,
partial-build confirmation, and reusable WorldEdit-authored prefabs.

- **Player guide:** [`PLAYER-GUIDE.md`](PLAYER-GUIDE.md)
- **Prefab administrator guide:** [`docs/prefabs-admin.md`](docs/prefabs-admin.md)
- **Shapes, limits, and prefab design contract:**
  [`docs/2026-08-28-shapes-limits-and-prefabs-design.md`](docs/2026-08-28-shapes-limits-and-prefabs-design.md)

## Runtime model

The player holds one Builders Wand in the offhand and uses the nine hotbar slots as a live material
palette. Right-click anchors and advances a shape; the per-player ghost shows the exact plan before
the paced wave begins. Shift-right cycles forms, left-click cancels, and shift-left rotates supported
BlockData.

The palette is a deterministic multiset of supported hotbar materials. Duplicate occupied slots add
weight; stack size and slot order do not. One sample in every participating hotbar slot is protected,
while surplus and matching inventory stacks supply placement. Recreating the same recipe at the
same world coordinates recreates the same texture.

Customized `ItemStack`s are ignored during palette capture and feedstock accounting. Names, lore,
enchantments, custom model data, PDC, or other item metadata therefore cannot make a crate key or
another plugin item enter the palette or be consumed as an ordinary block.

Doors, beds, and block-entity materials such as chests, barrels, furnaces, hoppers, signs, and
decorated pots are rejected from the ordinary palette. This prevents content-bearing inventory
items from being consumed and recreated as empty default blocks.

Solid cells consume one matching item and one custom wand Use. A water-only palette retains its
bucket and spends `wand.water-uses-per-source` Uses per completed source (default `3`). Water and
solid palettes cannot mix.

## Forms and density

| Form | Interaction | Density |
|---|---|---|
| **Diagonal** | Rising run plus tread width | Intrinsically solid |
| **Box** | Three-dimensional cuboid | Shell or Solid |
| **Cylinder** | Round cross-section plus length | Shell or Solid |
| **Sphere / Capsule** | Round size plus optional length | Shell or Solid |
| **Wall** | One vertical opposite-corner aim | Intrinsically solid |
| **Line** | Dominant X, Y, or Z endpoint | Intrinsically solid |
| **Floor** | One horizontal opposite-corner aim | Intrinsically solid |
| **Extend Surface** | Connected exact-BlockData face | Intrinsically solid; Free, Row, or Column restriction |

`/wand density <shell|solid>` persists a preference on the wand for Box, Cylinder, and Sphere.
`/wand surface <free|row|column>` persists the Extend Surface restriction. Both commands update an
active compatible preview without discarding its anchor.

Preview and commit share a centralized policy for candidate cells, actual world changes, per-form
spans, and touched chunks. An ordinary plan may inspect up to 4,096 candidate cells, but kept or
occupied cells are removed before the default 1,024-change print limit is applied. Ordinary plans
may touch up to 9 chunks. Prefabs keep their separate 512-cell and 8-chunk limits.

Anchored ordinary previews keep a thin gold marker with an aqua glow on the original clicked
anchor. Signed sizing remains intentional: for example, aiming below a Box anchor can grow the Box
downward while the marker continues to identify the original corner. If a complete operation
crosses a scan, change, span, or chunk limit, its block ghosts are suppressed and the action bar
uses a lightweight red boundary instead. It shows only the refusal and resize hint—never a
misleading material or Uses quote. Right-clicking a
refused preview changes nothing and leaves it anchored so the player can aim closer and resize it.

## Status and partial admission

Every ordinary form and prefab uses the same status vocabulary:

- **green / READY** — the complete operation is affordable and legal;
- **yellow / CAUTION or PARTIAL BUILD** — a condition needs attention or the player must explicitly
  approve an affordable subset; and
- **red / BLOCKED** — no placement may begin.

Material assignment occurs before budgeting. If resources are short, the complete plan freezes,
the exact admitted cells stay green, the remainder turns yellow, and the first commit request
places nothing. A second bound confirmation within
`placement.partial-confirmation-seconds` places only that quoted subset. Changed inventory, Uses,
palette, wand, geometry, world, protection, or target state produces a fresh quote rather than a
different silent subset.

Water's higher per-source Use cost is always shown as a separate yellow detail. It does not
downgrade a fully affordable, legal water plan from green **READY** to **CAUTION**.

Ordinary admission is anchor-out. Prefab admission is bottom-to-top, then anchor-out. Already-built
cells are kept without cost. Materials and Uses are spent only for cells that complete.

Living entities are never pushed or teleported. Ordinary solid cells defer and retry once, then
remain as uncharged gaps. Prefabs refuse their initial confirmation when a living entity occupies a
solid target; an entity entering a paced wave uses the same defer/retry behavior.

There is no wand-level undo. Printed cells use the normal player-attributed LogBlock hook when it
is connected.

## Wand Uses and economy

A new non-stackable wand starts at `wand.max-uses` (default `5,000`). Remaining and maximum Uses
are item PDC, not vanilla durability, so Essentials `/fix` does not alter them. The item also has a
lazy-minted serial and immutable first-wielder provenance.

The initial wand purchase belongs to the server's priced Essentials kit sign; there is no
`/wand buy` command. Use restoration is the recurring Denarii sink:

```text
/wand restore
/wand restore <uses>
/wand restore confirm
/wand restore cancel
```

Quotes use `wand.refill.denarii-per-use` (default `50`), enforce
`wand.refill.minimum-uses` (default `100`), and require confirmation before Vault withdraws money.
The legacy `/wand refill`, `/wand recharge`, and `wand.recharge` configuration remain compatibility
aliases, but player-facing help uses **restore** and **Uses**.

Player lifetime build and restoration totals are keyed by player UUID in
`plugins/BuildersWand/build-stats.sqlite`; they are not duplicated on tradable wand PDC.
`/wand stats` reads those all-wand totals. `/wand setuses <uses>` changes only the administrator's
held offhand wand and does not edit player history.

## Reusable prefabs

Prefabs are reusable player-global design unlocks loaded from Sponge v3 `.schem` files and strict
flat YAML sidecars under `plugins/BuildersWand/prefabs/` by default. WorldEdit's copy offset is
ignored. Import applies optional source rotation, trims exterior air, preserves interior air as
clearance, and infers the lower-left-front anchor.

Prefab v1 uses exact authored BlockData and exact matching inventory feedstock, not the live hotbar
palette. It rejects unsafe content, entities, block entities, fluids, containers, and unsupported
multi-item or multi-cell states at catalog validation.

The placement state machine is:

```text
SELECTED → ANCHORED → AWAITING CONFIRMATION → PLACING
```

The first right-click fixes the anchor and an initial rotation facing the player. Shift-left or
`/wand prefab rotate` rotates an allowed design; ordinary left-click removes the anchor while
keeping the selection. A second right-click requests an exact quote. Every prefab—complete or
partial—requires the opaque clickable confirmation before a wave begins.

Each placement consumes exact block feedstock, normal per-cell Uses, and the metadata activation
Uses (or `prefabs.default-activation-uses`). The unlock is never consumed. Each separately
confirmed partial continuation pays activation Uses again. Exact authored states are kept for free;
different non-replaceable targets and obstructed authored-air clearance fail closed.

Durable unlocks live in `plugins/BuildersWand/prefab-entitlements.sqlite`. An external shop can run
`/wand prefab grant <player> <id>`, or staff can issue a PDC Blueprint Voucher that is consumed only
after `/wand prefab redeem` durably records the unlock. Catalog or entitlement-store failures deny
access rather than bypassing ownership.

Catalog reload is atomic: every sidecar and schematic must validate before the live generation is
replaced. See [`docs/prefabs-admin.md`](docs/prefabs-admin.md) for the authoring format and rollout
checklist.

## Commands

Player commands:

- `/wand` — inspect the offhand wand and live palette
- `/wand stats` — inspect player-global lifetime totals
- `/wand form <diagonal|box|cylinder|sphere|wall|line|floor|extend_surface>`
- `/wand density [shell|solid]`
- `/wand surface [free|row|column]`
- `/wand restore [all|uses|confirm|cancel]`
- `/wand prefab` — list accessible designs
- `/wand prefab <id>` — select a design
- `/wand prefab rotate`
- `/wand prefab confirm <opaque-token>`
- `/wand prefab cancel`
- `/wand prefab redeem`

Administrative commands:

- `/wand give [player]`
- `/wand setuses <uses>` — changes the wand held in the administrator's offhand
- `/wand prefab grant <player> <id>`
- `/wand prefab revoke <player> <id>`
- `/wand prefab voucher <online-player> <id>`
- `/wand prefab validate <id>`
- `/wand prefab reload`

## Permissions

- `builderswand.use` — use the wand and ordinary form commands; default `true`
- `builderswand.refill` — restore Uses through Vault; default `true`
- `builderswand.give` — issue a wand; default `op`
- `builderswand.uses.bypass` — waive wand Use consumption; default `op`
- `builderswand.admin.setuses` — set Uses on the administrator's held wand; default `op`
- `builderswand.prefab.all` — access every loaded prefab; default `op`
- `builderswand.prefab.<id>` — access one loaded prefab
- `builderswand.prefab.admin` — grant, revoke, voucher, validate, and reload; default `op`

## Configuration

The core shipped defaults relevant to placement and prefabs are:

```yaml
cadence:
  small-print-max-cells: 16
  small-print-ticks-per-cell: 2
  large-print-ticks-per-cell: 1

ghost:
  update-ticks: 2
  scale: 0.8
  ready-glow-rgb: "55FF55"
  partial-glow-rgb: "FFFF55"
  blocked-glow-rgb: "FF5555"

wand:
  max-uses: 5000
  water-uses-per-source: 3
  refill:
    denarii-per-use: 50
    minimum-uses: 100
    confirmation-seconds: 30

placement:
  partial-confirmation-seconds: 10

limits:
  max-cells-per-print: 1024
  max-scanned-cells-per-plan: 4096
  max-chunks-per-print: 9
  max-span:
    line: 64
    wall: 32
    floor: 32
    box: 16
    diagonal: { primary: 8, secondary: 5, tertiary: 1 }
    cylinder: { primary: 9, secondary: 8, tertiary: 1 }
    sphere: { primary: 7, secondary: 8, tertiary: 1 }
    extend-surface: { primary: 64, secondary: 64, tertiary: 1 }

prefabs:
  enabled: true
  directory: prefabs
  confirmation-seconds: 30
  default-activation-uses: 20
  max-cells: 512
  max-volume: 8192
  max-axis-span: 32
  max-chunks: 8
  max-clearance-cells: 8192
  require-logblock: true

anchor-reach: 16
```

Changing `wand.max-uses` affects new and not-yet-migrated legacy wands; initialized wands retain
the maximum stored on the item. All runtime configuration is loaded at plugin enable.

The initial wand price remains on the Essentials `[Kit]` sign. Prefab shop prices remain in the
external shop; only per-placement activation Uses belong in prefab metadata/configuration.

## Protection and logging

WorldGuard and Lands are optional soft dependencies. When installed, target, source-validation,
and prefab-clearance cells are checked before admission. World border, build height, chunk count,
and entity safety are also rechecked at final confirmation.

LogBlock is an optional soft dependency for ordinary forms. When connected, every successful wand
mutation is queued as the placing player's block place or replace. With the default
`prefabs.require-logblock: true`, prefab placement is blocked before spending if that connection is
missing or incompatible.

## Building and running

Requires **Java 25** and targets **Paper 26.1.2**. The Gradle wrapper is 9.7.1.

```bash
make build
```

This compiles the plugin and runs the unit suite. The jar is written under `build/libs/`.

To launch the Paper development server:

```bash
make run
```

The first run downloads the server. Accept the Mojang EULA in `run/eula.txt`, then add Vault and an
economy provider for restoration testing. Add WorldGuard, Lands, and LogBlock jars when testing
their integrations.

## Design and specification

- [`docs/materializer-v1-design.md`](docs/materializer-v1-design.md) — original frozen materializer contract
- [`docs/specs/2026-08-23-materializer-v1.md`](docs/specs/2026-08-23-materializer-v1.md) — original implementation spec
- [`docs/2026-08-28-shapes-limits-and-prefabs-design.md`](docs/2026-08-28-shapes-limits-and-prefabs-design.md) — implemented expansion contract
