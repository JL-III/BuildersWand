# Builders Wand Prefabs — Administrator Guide

This guide covers authoring WorldEdit schematics, loading the server-owned prefab catalog, granting
access, and diagnosing refused placements. For ordinary player controls, see
[`PLAYER-GUIDE.md`](../PLAYER-GUIDE.md).

## Runtime contract

A prefab unlock is reusable and belongs to a player UUID, not to a wand. The external shop owns any
Denarii transaction. BuildersWand records only the durable unlock in
`plugins/BuildersWand/prefab-entitlements.sqlite`.

Every confirmed placement consumes exact inventory feedstock, one normal Use per completed block,
and the prefab's activation Uses. A later partial continuation pays the activation Uses again.
There is no free block materialization and no Denarii withdrawal in the prefab placement path.

By default, prefab placement also requires a working player-attributed LogBlock connection. The
plugin has no `/wand undo`; LogBlock is the administrative attribution and rollback path.

## Files and directory

With the default configuration, the catalog is:

```text
plugins/BuildersWand/prefabs/
├── starter_house.schem
└── starter_house.yml
```

The plugin creates the directory when prefabs are enabled. Each design consists of:

- a gzip-compressed **Sponge Schematic v3** `.schem` file, normally written by WorldEdit 7; and
- one flat `.yml` or `.yaml` metadata sidecar in the same directory.

Subdirectories and schematic paths are not supported. The catalog rejects schematic symlinks that
resolve outside this directory.

## Authoring with WorldEdit

1. Build the design in a staging world. Prefer to make its front face **south**.
2. Select a tight cuboid around the structure. Include intentional interior air, but avoid unused
   exterior space.
3. Run `//copy` from any convenient position. Do not include entities or biomes.
4. Save it as Sponge v3, for example `//schem save builderswand/starter_house` on a WorldEdit setup
   whose default schematic format is Sponge v3.
5. Copy the resulting `.schem` into `plugins/BuildersWand/prefabs/`.
6. Add the sidecar described below.
7. Run `/wand prefab validate starter_house` before reloading the live catalog.

BuildersWand deliberately ignores WorldEdit's clipboard origin and stored offset. The author's
position during `//copy` therefore does not control placement.

At import, the plugin applies `source-rotation`, trims all-air exterior planes, and infers the
anchor as the **lower-left corner of the front face**. At canonical rotation zero, the design grows
right/east, up, and inward/north from that anchor. The player's first placement click maps this
inferred anchor to the clicked destination cell.

### Source direction

`source-rotation` rotates the imported design clockwise as viewed from above so that its authored
front becomes canonical south:

| Authored front | `source-rotation` |
|---|---:|
| South | `0` |
| East | `90` |
| North | `180` |
| West | `270` |

This import-time correction is separate from the player's placement-time rotation. Set
`allow-rotation: false` when the design must retain its canonical direction in the world.

## Metadata sidecar

Example `starter_house.yml`:

```yaml
id: starter_house
name: Starter House
version: 1
schematic: starter_house.schem
source-rotation: 0
activation-uses: 20
allow-rotation: true
clearance: schematic-air
```

The parser intentionally accepts only flat `key: value` metadata. Nested values, tabs, collections,
anchors, tags, duplicate keys, and unknown fields are rejected.

| Field | Required | Rules |
|---|---|---|
| `id` | Yes | Stable entitlement key: 1–64 lowercase letters, digits, `_`, or `-`; first character must be a letter or digit |
| `name` | Yes | Player-facing name, 1–80 characters |
| `version` | Yes | Positive integer; increment deliberately when publishing a changed design |
| `schematic` | Yes | Local `.schem` filename in the same catalog directory |
| `source-rotation` | No | `0`, `90`, `180`, or `270`; default `0` |
| `activation-uses` | No | Non-negative integer; default is `prefabs.default-activation-uses` |
| `allow-rotation` | No | Exactly `true` or `false`; default `true` |
| `clearance` | No | Currently only `schematic-air`; this is the default |

The stable `id` is what shops grant and what the entitlement database stores. Replacing the
contents or increasing `version` does not make existing owners repurchase the design. A content or
version change does invalidate old placement confirmations.

Catalog validation rejects a metadata `activation-uses` value above `wand.max-uses`, because an
ordinary player could never fund that activation from one wand.

## Air, clearance, and ignored cells

The importer first trims every complete exterior plane that contains no placeable blocks. Inside
the resulting tight bounds:

- a normal air cell becomes **required clearance** and must be actual air at placement time;
- a `minecraft:structure_void` cell is ignored; and
- a non-air cell becomes an exact authored placement target.

This means exterior all-air padding does not reserve space. Prefab v1 cannot express clearance
beyond the outermost placeable blocks. Interior rooms, doorways, and gaps can be protected as
clearance because they are enclosed by the trimmed bounds. Use `structure_void` for cells inside
those bounds that should neither place a block nor require air.

The wand never deletes terrain to satisfy clearance. A blocked clearance cell refuses the entire
start.

## Safe block subset

Catalog validation accepts ordinary item-backed, one-item-per-cell blocks and preserves their
authored BlockData when it can rotate that state safely. It rejects unsafe or ambiguous content,
including:

- entities and all block entities;
- containers, shulker boxes, signs, skulls/heads, lecterns, spawners, and other content-bearing
  blocks;
- doors, beds, tall two-block plants, and other unsupported multi-cell placements;
- fluids, waterlogged states, portals, fire, administrative blocks, and moving piston states;
- double slabs or counted states that require more than one inventory item in one cell; and
- pre-grown age states that cannot be supplied faithfully by one ordinary item.

Do not rely on this summary as an allowlist. `/wand prefab validate <id>` is authoritative for the
running Minecraft/Paper version and reports every rejected state with its local position.

## Limits

Default prefab limits are:

| Guard | Default |
|---|---:|
| Placeable non-air cells | 512 |
| Schematic volume, including air/clearance | 8,192 |
| Span on any axis | 32 |
| Touched chunks at placement | 8 |
| Clearance cells | 8,192 |
| Confirmation lifetime | 30 seconds |
| Activation Uses when omitted from metadata | 20 |

All are configurable under `prefabs` in `config.yml`. `default-activation-uses` cannot exceed
`wand.max-uses`, and all size/confirmation limits must be positive. `directory` must remain inside
the BuildersWand data directory.

These import limits are intentionally conservative because every visible target uses the normal
ghost, protection, quote, LogBlock, and paced placement pipeline. Split a larger design into
foundation, shell, roof, or interior prefabs instead of simply raising limits without profiling.

## Validation and atomic reload

`/wand prefab validate <id>` reads one sidecar and schematic without changing the live catalog. A
successful report includes the name, version, dimensions, block count, clearance count, inferred
source anchor, and activation Uses.

`/wand prefab reload` validates the complete directory into a new catalog generation. If any
sidecar or schematic fails, the reload fails and the previous catalog remains active. A successful
reload invalidates any selected design and old confirmation token whose content no longer matches.

Recommended release sequence:

1. Validate the new or changed ID.
2. Validate every related design that will ship together.
3. Reload the catalog.
4. Grant yourself the design in a staging world.
5. Test all allowed rotations, an empty placement, an exact rerun, a conflict, an interior
   obstruction, a resource-short partial placement, and its continuation.
6. Check LogBlock lookup/rollback attribution under the placing player's name before selling the
   design.

## Access and shop integration

Administrative commands require `builderswand.prefab.admin`:

```text
/wand prefab grant <player> <id>
/wand prefab revoke <player> <id>
/wand prefab voucher <online-player> <id>
/wand prefab validate <id>
/wand prefab reload
```

The preferred shop handoff is a console command after payment:

```text
/wand prefab grant <player> starter_house
```

Use a voucher when the Essentials kit/sign workflow must deliver a physical item instead. The
target must be online when staff runs `/wand prefab voucher <player> <id>`. The player holds that
PDC-backed paper voucher in the main hand and runs `/wand prefab redeem`. A voucher is consumed only
after the durable grant succeeds; a duplicate unlock leaves it untouched.

Additional permission access is available through:

- `builderswand.prefab.all` — every currently loaded design; default `op`
- `builderswand.prefab.<id>` — one loaded design

The entitlement database must still be available even for permission-based access. Catalog and
ownership failures are fail-closed: list, selection, grant, redemption, and placement refuse rather
than risk losing or bypassing paid access.

## Placement semantics administrators should expect

- A prefab selection is player-global for the session, but the unlock is durable across restarts
  and replacement wands.
- The first right-click anchors the inferred lower-left-front cell. The initial rotatable front
  faces the player.
- Every placement requires an exact, expiring chat confirmation. No confirmation means no charge.
- Exact authored BlockData already present is kept for free. Replaceable cells can receive the
  authored block; a different non-replaceable block or BlockData is a hard conflict.
- Required schematic-air cells must be actual air. Nothing is cleared automatically.
- The prefab uses exact authored materials from the player's inventory, not the live hotbar
  palette.
- Resource shortages can place only an explicitly confirmed subset. Blocks are ordered
  bottom-to-top and then outward from the anchor. Each continuation pays activation Uses again.
- Protection, world border, build height, chunk, entity, wand, inventory, Uses, catalog hash, and
  access checks run again at final confirmation.
- A hard refusal, stale quote, or zero affordable cells spends nothing.
- There is no wand-level undo or refund for completed blocks.

## LogBlock requirement

With the default `prefabs.require-logblock: true`, prefab admission fails before spending when
LogBlock is missing, disabled, or API-incompatible. Confirm the startup log contains:

```text
BuildersWand placement logging connected to LogBlock.
```

Each successful cell is queued as a player-attributed place or replace. If logging throws during a
wave, placement stops rather than continuing invisibly.

Setting `require-logblock: false` is an explicit owner policy choice. It permits prefab placement
without guaranteed rollback attribution and should not be used accidentally. Ordinary wand shapes
retain their historical optional-logging behavior.

## Configuration reference

```yaml
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
```
