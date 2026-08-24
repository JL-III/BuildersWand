# BuildersWand — Materializer

A Paper plugin. Hold the Builders Wand, aim at a surface, and right-click to anchor one of
four parametric forms; stretch it to size with staged right-clicks while a per-player glowing
ghost shows exactly what will be built; the final right-click prints it cell-by-cell using
blocks consumed from your inventory — or is precisely refused with a message naming why,
having changed and spent nothing.

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

1. `/wand give` to get a wand, then `/wand form <name>` to choose a form (or **shift +
   right-click** to cycle forms). The current form shows in the wand's name.
2. Hold a **placeable solid block in your off hand** — that block is the print material
   (doors, beds, and shulker boxes are not allowed). Cost is one item per placed cell.
3. **Right-click a surface** to anchor. A purple, glowing, shrunken-block ghost tracks your
   aim; the action bar shows the size, cell counts, material, and the next step. The ghost is
   drawn only on cells that will actually be placed — cells already occupied by blocks (the
   ground, an existing wall) are shown as `kept` in the action bar, not ghosted.
4. **Right-click** to lock each stage (length / width / height, or radius), then a final
   right-click **prints**.
5. **Shift + left-click** rotates how oriented blocks (stairs, logs, …) are placed — it cycles
   the four facings and the upside-down half, starting from the block's own default
   orientation. The ghost previews the real oriented block.
6. **Left-click cancels.** The gesture also drops if you switch hotbar slots away from the
   wand, change the wand's form, die, or change worlds.

Blocks appear one per tick (one per two ticks for small prints ≤ 16 cells) with place sounds.
Cells that are already built are **kept and never charged**; a print completes a partial
shape. A living body inside the print is lifted on top. Creative mode prints free. There is
no undo — mine printed blocks to reclaim them (vanilla drops). Mid-print interruptions (a
block appears, permission is lost, you log out) stop the wave and refund the unspent items.

## Commands

- `/wand give [player]` — give a wand (self if no player). Requires `builderswand.give`.
- `/wand form <diagonal|box|cylinder|sphere>` — set the held wand's form. Requires
  `builderswand.use`.
- `/wand` — show the held wand's form, the current off-hand material, and usage.

## Permissions

- `builderswand.use` — use the wand and `/wand form`. Default **true**.
- `builderswand.give` — `/wand give`. Default **op**.

## Configuration (`config.yml`)

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

The 512-cell cap and the per-form dimension bounds are constants, not config.

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

Compiles the plugin and runs the geometry unit tests (the golden form counts and emission
order). To launch a throwaway dev server for playtesting:

```bash
./gradlew runServer
```

The first run downloads the Paper 26.1.2 dev server. Accept the Mojang EULA in `run/eula.txt`,
`op` yourself in the console, and connect a Minecraft 26.1.2 client to `localhost:25565`. To
exercise the protection checks, drop the WorldGuard and Lands jars into `run/plugins/`.

## Design & specification

- Frozen design contract: [`docs/materializer-v1-design.md`](docs/materializer-v1-design.md)
- Implementation spec: [`docs/specs/2026-08-23-materializer-v1.md`](docs/specs/2026-08-23-materializer-v1.md)
