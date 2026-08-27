# BuildersWand — Materializer

**Player-facing instructions:** [Builders Wand Player Guide](PLAYER-GUIDE.md)

A Paper plugin. Hold the Builders Wand, aim at a surface, and left-click to anchor one of
four parametric forms; stretch it to size with staged left-clicks while a per-player glowing
ghost shows exactly what will be built; the final left-click prints it cell-by-cell using
blocks consumed from your inventory, or source water selected by a reusable water bucket —
and spends one Use per completed solid cell or, by default, three Uses per completed water
source. Refused and uncompleted cells spend nothing.

## The four forms

| Form | Shape | Dimensions | Max cells |
|---|---|---|---|
| **Diagonal** | a solid stair run | run 1–8 × tread width 1–5 | 40 |
| **Box** | a hollow cuboid shell (an extent of 1–2 collapses to a solid plate) | 1–8 per axis | 296 |
| **Cylinder** | an open tube | size 1–9 × courses 1–8 | 384 |
| **Sphere / Capsule** | a one-cell-thick shell (length > 1 makes a capsule) | size 1–7 × length 1–8 | 490 |

Volumetric forms are **hollow by construction**. Every plan is capped at **512 expanded
cells** (kept cells included); an over-cap request is refused, never clamped — except the
capsule, whose live gesture walks its length down instead of vanishing. Dimension bounds and
the 512-cap are fixed invariants, not config.

## Using the wand

1. Buy a wand from the server's priced **Essentials kit sign** (recommended default:
   **1,000,000 Denarii**), or have an administrator issue one with `/wand give`. Then use
   `/wand form <name>` to choose a form (or **shift + left-click** to cycle forms). The current
   form shows in the wand's name.
2. Hold a **placeable solid block or water bucket in your off hand**. Solid blocks cost one
   item per placed cell (doors, beds, and shulker boxes are not allowed). A water bucket is
   retained and acts as a reusable source for the whole water print; each source costs three
   Uses by default.
3. **Left-click a surface** to anchor. A purple, glowing, shrunken-block ghost tracks your
   aim; the action bar shows the size, cell counts, material, and the next step. The ghost is
   drawn only on cells that will actually be placed — cells already occupied by blocks (the
   ground, an existing wall) are shown as `kept` in the action bar, not ghosted.
4. **Left-click** to lock each stage (length / width / height, or radius), then a final
   left-click **prints**.
5. **Shift + right-click** rotates how oriented blocks (stairs, logs, …) are placed — it cycles
   the four facings and the upside-down half, starting from the block's own default
   orientation. The ghost previews the real oriented block.
6. **Right-click cancels.** The gesture also drops if you switch hotbar slots away from the
   wand, change the wand's form, die, or change worlds.

Blocks appear one per tick (one per two ticks for small prints ≤ 16 cells) with place sounds.
Cells that are already built are **kept and never consume a use**; a print completes a partial
shape. A living body inside the print is lifted on top. There is no undo — mine printed blocks
to reclaim them (vanilla drops). Materials and wand uses are spent one cell at a time as the
wave runs (never taken up front), so mid-print interruptions (a block appears, permission is
lost, you run out of materials or uses, you move the wand, you log out) just stop the wave.
Creative mode waives block feedstock, while only `builderswand.uses.bypass` waives wand-use
consumption.

Water prints place level-0 source blocks in open cells. Existing source water is kept, flowing
water is upgraded to a source, and swimmers are not pushed out of the print. Water still flows
with vanilla physics and is refused in ultra-warm dimensions where a bucket would evaporate.
The selecting water bucket must remain in the off hand but is never consumed, so players do not
need to replenish it between cells or prints. Each committed water print explicitly reports its
configured per-source and total Use cost in chat. Lava buckets are not supported.

If you don't have enough material or wand uses, the cells you can't afford glow **red** in
the preview and the action bar names each shortfall. Printing builds as many cells as both
resources allow — in placement order — and leaves the rest. The reusable bucket has no per-cell
inventory cost, but its planned source cells spend `wand.water-uses-per-source` Uses apiece
(default **3**). Preview affordability divides the wand's remaining Uses by that complete
per-source cost, so a source is never partially charged.

## Wand uses and Denarii

Every newly issued Builders Wand is non-stackable and starts with **5,000 / 5,000 uses**. Remaining
uses are stored in the wand's persistent custom data and displayed in its lore; vanilla durability
and Essentials `/fix` do not alter them. Legacy wands without use data migrate to a full 5,000 uses
the first time they are used or restored. A depleted wand remains at zero so its Uses can be restored.

The lore also shows the immutable **first wielder**. On its first real interaction, form change,
or Use restoration, an unclaimed kit-template wand receives its own serial, first-wielder
UUID/name snapshot, and timestamp. Minting that identity lazily prevents every copy issued by an
Essentials kit sign from inheriting the template's serial. These fields are item provenance, not
gameplay statistics or anti-duplication security: copying an item's NBT also copies its PDC.

Hold the single wand in your main hand and run `/wand restore` to quote all missing Uses, or
`/wand restore <uses>` for an exact partial restoration. The default rate is **50 Denarii per Use**,
with a **100-use minimum**. The quote shows the before/after uses and exact total; no money is
taken until the player clicks **CONFIRM** (or runs `/wand restore confirm`) within 30 seconds.
Moving to a different wand, starting a print, changing its remaining uses, or letting the quote
expire invalidates the purchase. `/wand restore cancel` cancels it. At the defaults, empty-to-full is
250,000 Denarii.

The old `/wand refill` and `/wand recharge` variants remain accepted as hidden compatibility
aliases; player-facing help, buttons, and tab completion use `restore`.

The initial purchase deliberately belongs to Essentials, not this plugin: there is no
`/wand buy` command. Set the Essentials kit sign's own price (recommended default:
**1,000,000 Denarii**) and have the kit issue a new wand through the namespaced administrator
command `/builderswand:wand give {player}`. Players then need the normal Essentials kit-sign/kit
permissions plus the default-on Builders Wand use and restoration permissions. Vault and a Vault
economy provider must be present for restoring Uses; if either is unavailable, restoration fails closed
without taking money.

## Build history and recognition

Player and server build totals use `plugins/BuildersWand/build-stats.sqlite`, not copyable wand
PDC. This player-UUID record is the **only cumulative history**: the wand itself does not track
lifetime Uses, restoration count, or Denarii spent. The database records completed wand Uses,
actual non-water block mutations, actual water sources materialized, naturally completed prints,
each player's largest completed print, and successful paid-restoration count/Uses/Denarii for
economic tuning across every wand that player uses.
Water that vanilla infinite-source physics fills before its scheduled cell still consumes and
records the configured per-source Uses (three by default), but it is not falsely counted as a
block placed by the plugin.

`/wand stats` shows the player's durable all-wand totals. Per-player recognition claims have a
unique database key, so a milestone can fire only once per player even across restarts. By default
the server announces when a player reaches **1,000,000 actual blocks materialized**; both
announcements and the list of block milestones are configurable. These durable player totals—not
a tradable wand—should be used for future titles, cosmetics, leaderboard entries, or rewards.

Replacing a lost or damaged wand does not reset or migrate any of those totals. An administrator
can hold the replacement in their own main hand, run `/wand setuses <uses>` to set its remaining
balance, and then give it to the player. This command changes only the held wand's current Uses; it
does not change maximum Uses or anyone's lifetime statistics. It refuses to run during an active
print, cancels the administrator's pending restoration quote, and writes an audit entry to the
server log.
If the old wand is unavailable, its exact remaining balance cannot be derived from cumulative
player history, so staff deliberately chooses the replacement value.

## Titan integration boundary

BuildersWand is intentionally standalone. Its PDC-backed **Uses** are not Titan tool Charge, and
Power Crystals are not accepted. If those systems are unified later, the Titan Wand/item identity,
crystal handling, and charging rules should be owned by TitanEnchants after Titan tools expose a
stable PDC-backed service; BuildersWand should not parse or duplicate Titan's current lore rules.

## Commands

- `/wand give [player]` — give a wand (self if no player). Requires `builderswand.give`.
- `/wand form <diagonal|box|cylinder|sphere>` — set the held wand's form. Requires
  `builderswand.use`.
- `/wand restore [all|uses]` — quote restoring all or an exact number of Uses; does not take money.
- `/wand restore confirm` — accept the current unexpired quote. The clickable confirmation
  uses a one-time opaque token so an old chat button cannot approve a newer quote.
- `/wand restore cancel` — discard the current quote.
- `/wand` — inspect only the held wand's current state and provenance.
- `/wand stats` — show the player's durable lifetime totals across all wands; no held wand required.
- `/wand setuses <uses>` — set the remaining Uses on the wand held by the administrator without
  changing global history. This is an in-game-only command requiring `builderswand.admin.setuses`.

## Permissions

- `builderswand.use` — use an owned wand and `/wand form`. Default **true**; ownership of the
  paid wand is the access gate.
- `builderswand.give` — `/wand give`. Default **op**.
- `builderswand.refill` — restore Uses with Denarii (stable permission name). Default **true**.
- `builderswand.uses.bypass` — print without spending Builders Wand uses. Default **op**.
- `builderswand.admin.setuses` — set a held wand's remaining Uses. Default **op**.

## Configuration (`config.yml`)

The two monetary controls have separate authoritative homes:

- **Initial wand:** the price on line four of the Essentials `[Kit]` sign; recommended default
  **1,000,000 Denarii**. Changing the sign changes the price immediately.
- **Use restoration:** `wand.refill.denarii-per-use` below; default **50 Denarii**. Full and partial
  quotes are calculated from that rate, so changing it changes every restoration price after the
  plugin is restarted. `minimum-uses` controls the smallest partial purchase.

`wand.water-uses-per-source` independently controls the Use cost of each source-water cell;
solid blocks always cost one Use. The default is **3**.

```yaml
cadence:
  small-print-max-cells: 16     # ≤ this many printable cells → slow cadence
  small-print-ticks-per-cell: 2
  large-print-ticks-per-cell: 1
ghost:
  update-ticks: 2
  scale: 0.8
  glow-rgb: "9E3DFF"
wand:
  # Initial purchase cost lives on the Essentials [Kit] sign.
  max-uses: 5000
  water-uses-per-source: 3
  refill:
    denarii-per-use: 50
    minimum-uses: 100
    confirmation-seconds: 30
recognition:
  announcements: true
  total-block-milestones:
    - 1000000
anchor-reach: 16
```

Changing `max-uses` affects newly issued and not-yet-migrated legacy wands; an existing initialized
wand keeps the maximum stored on the item. Changing `water-uses-per-source` takes effect after a
plugin restart and does not alter actual-block recognition totals. The 512-cell cap and per-form
dimension bounds are constants, not config.

The existing `wand.refill` configuration and `builderswand.refill` permission names remain stable
for server compatibility; they power the player-facing `/wand restore` flow. Legacy
`wand.recharge` configuration also remains supported.

## Protection

WorldGuard and Lands are optional soft-dependencies. When present, every cell is checked for
build permission (WorldGuard `testBuild` with bypass; the Lands block-place flag) and a print
is refused naming the position and plugin. When absent, the plugin builds without those checks.

## Building & running

Requires **Java 25** and targets **Paper 26.1.2** (the pinned `paper-api` artifact is a
Java 25 build). The Gradle wrapper is 9.7.1.

```bash
./gradlew build
```

Compiles the plugin and runs the geometry, material-policy, variable placement-use, custom-use,
and SQLite-statistics unit tests. To launch a throwaway dev server for playtesting:

```bash
./gradlew runServer
```

The first run downloads the Paper 26.1.2 dev server. Accept the Mojang EULA in `run/eula.txt`,
`op` yourself in the console, and connect a Minecraft 26.1.2 client to `localhost:25565`. To
exercise the protection checks, drop the WorldGuard and Lands jars into `run/plugins/`.

## Design & specification

- Frozen design contract: [`docs/materializer-v1-design.md`](docs/materializer-v1-design.md)
- Implementation spec: [`docs/specs/2026-08-23-materializer-v1.md`](docs/specs/2026-08-23-materializer-v1.md)
