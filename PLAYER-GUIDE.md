# Builders Wand — Player Guide

The Builders Wand turns blocks from your inventory into walls, floors, shapes, textured builds,
and reusable prefab designs. You always see a preview before anything is placed.

## Getting a Builders Wand

Purchase your wand from the designated **Essentials kit sign** in-game. The price on that sign is
the purchase price; there is no `/wand buy` command.

A new wand starts with **5,000 Uses**. Its lore shows its remaining Uses and first wielder.

## Quick start

1. Put one unstacked **Builders Wand in your offhand**.
2. Arrange the blocks you want to build with in your hotbar. Your hotbar is the wand's live
   palette; there is nothing to save or select.
3. Choose a shape with **Shift + right-click**, or use `/wand form <shape>`.
4. Aim at a surface and **right-click** to set the anchor.
5. Move your aim until the preview has the size you want. Right-click to lock any sizing stages,
   then right-click when prompted to print.

After anchoring, a thin gold marker with an aqua glow always marks the original cell you clicked.
It is a guide, not another block that the wand will place.

The preview and messages use one status language:

- **Green — READY:** the complete build can be placed.
- **Yellow details:** supporting information such as water's higher Use cost; yellow never means a
  smaller subset will be placed.
- **Red — BLOCKED:** nothing can begin; this includes missing materials or Uses, and the message
  tells you what must change.

## Controls

| Control | Ordinary shapes | Prefabs |
|---|---|---|
| **Right-click** | Anchor, lock a size, or print | Anchor, then request the required placement confirmation |
| **Shift + right-click** | Cycle to the next shape | Leave prefab mode and cycle to the next ordinary shape |
| **Left-click** | Cancel the current preview | Remove the anchor but keep the design selected |
| **Shift + left-click** | Rotate stairs, logs, slabs, and other supported blocks | Rotate the entire anchored prefab 90 degrees |

Removing or replacing the offhand wand, swapping hands, dying, changing worlds, or leaving the
server cancels the active preview. Wand control clicks do not use a palette item or damage the
entity under your crosshair.

## Shapes

| Shape | What it makes |
|---|---|
| **Box** | A room-like shell or a completely filled cuboid |
| **Diagonal** | A rising diagonal run for stairs, roofs, and slopes |
| **Cylinder** | A hollow tube or a filled round column |
| **Sphere** | A sphere; stretching its length makes a capsule |
| **Wall** | A vertical rectangle; make its width 1 for a pillar or its height 1 for a row |
| **Line** | A straight horizontal beam, row, or vertical pillar along the strongest aim direction |
| **Floor** | A horizontal rectangle for floors, ceilings, roofs, and platforms |
| **Extend Surface** | A new layer outside a connected face of matching blocks |

Wall, Line, Floor, and Extend Surface need only an anchor and one final right-click. Diagonal,
Cylinder, and Sphere have one size-lock step. Box has two size-lock steps.

Server limits allow thin shapes such as lines to reach farther than filled shapes. For ordinary
shapes, the wand can inspect up to **4,096 candidate cells**, then removes cells that are already
built or occupied before applying the **1,024 cells actually changed** limit. A plan may touch up
to **9 chunks**, and each shape also has its own maximum dimensions. Prefabs use a separate
512-cell limit.

If a complete operation is too large, its normal block preview is replaced by a lightweight red
boundary. The message gives the exact limit reason plus an **Aim closer to reduce the shape**
hint. It does not show material or Uses costs because none of that oversized operation can begin.
Right-clicking does not cancel the anchor or start a smaller print; keep aiming to resize it, or
left-click to cancel.

Sizing can extend in either direction. In particular, aiming below a Box anchor deliberately grows
the Box downward. Its working geometry corner may move as it grows, but the gold/aqua marker stays
on the original clicked anchor so you can always see the reference point.

### Shell and Solid

Box, Cylinder, and Sphere can be **Shell** or **Solid**. Shell builds only the outside and is the
default. Solid fills the complete interior and can require many more blocks and Uses.

- `/wand density shell`
- `/wand density solid`
- `/wand density` to switch between them

Your choice is stored on the wand and appears in its name. It remains ready for the next supported
shape even while you are using a shape that is always solid.

### Extend Surface

Extend Surface reads the exact connected block face you clicked and previews one new layer outside
it. Choose how much of that face is followed with:

- `/wand surface free` — the connected face, up to the server limits
- `/wand surface row` — one row across the face
- `/wand surface column` — one vertical column
- `/wand surface` — cycle to the next option

The source face must stay unchanged until the print begins. If someone edits it after you anchor,
the wand asks you to cancel and anchor it again.

## Your live hotbar palette

Every supported solid block in your nine hotbar slots joins the palette. Empty slots, tools, food,
and other ordinary items are ignored. Customized items with names, lore, enchantments, model data,
PDC, or any other item metadata are also ignored and will never be consumed as building material.
There are no saved swatches.

Each occupied material slot has equal weight. Repeating a material in more than one slot makes it
more common in the texture; stack size does not change its weight. For example, stone in two slots
and andesite in one makes a 2:1 stone-to-andesite texture.

The same material combination always makes the same texture at the same world coordinates. You can
leave, return with the same combination and duplicate slots, and continue without changing the
pattern. Hotbar slot order does not matter.

The wand protects one sample block in every participating hotbar slot. It spends surplus blocks in
those stacks and matching blocks elsewhere in your inventory. The samples keep your palette from
changing just because a stack ran low.

Doors, beds, containers, lava buckets, and other unsuitable materials cannot join an ordinary
palette. The wand also respects protected claims and regions.

## Materials, Uses, and affordability

| Material | Inventory cost | Wand cost |
|---|---:|---:|
| Solid-block palette | 1 matching block per completed cell | 1 Use per completed cell |
| Water-only palette | Water bucket is retained | 3 Uses per completed source by default |

Cells that are already built are **kept** and cost no block or Use. A solid print also skips cells
occupied by a player or mob instead of moving or trapping that entity. Skipped cells cost nothing
and can be filled by printing again after the area is clear.

Every printable cell must be affordable before a print can start. If even one assigned material or
Use is missing, the unavailable cells glow red and the entire operation is **BLOCKED**. The wand
shows the exact shortage and places or spends nothing; it never silently makes a smaller build.

Restock the required blocks or restore enough Uses while the preview is active. The live preview
updates as your resources change and becomes green **READY** only when the complete printable plan
is affordable. Ordinary shapes can then print normally. There is no subset confirmation or
retained shortage continuation.

There is no wand undo. Mine placed blocks normally if you want to remove or reclaim them.
Essentials `/fix` does not restore Builders Wand Uses.

## Building with water

For water, put one or more water buckets in the hotbar and remove all supported solid blocks from
it. Water and solid blocks cannot be mixed in one palette. Extra water buckets do not change the
pattern or cost.

The bucket is a reusable source and is never emptied by the wand. Water normally costs **3 Uses per
completed source**, instead of the 1 Use charged for a solid block. A fully affordable water print
is still green and **READY**; its higher Use cost is called out separately before placement. Server
settings may change that number.

Water cannot be placed in a world where it immediately evaporates.

## Reusable prefab designs

A prefab is a server-made building design such as a house or decoration. Unlocking a design gives
your player account permanent access; it is not tied to one wand and it is not consumed when used.

Every placement still costs:

- the exact authored blocks from your inventory,
- the normal Uses for blocks actually placed, and
- the design's listed **activation Uses** each time a placement begins.

Prefab blocks are not taken from your live palette and are not recolored by it. The wand follows
the design's exact blocks and orientations.

### Placing a prefab

1. Put the wand in your offhand and run `/wand prefab` to list your designs.
2. Select one with `/wand prefab <id>`.
3. Aim at a block face and right-click. That cell becomes the design's lower-left-front anchor, and
   the design initially faces you.
4. Walk around and inspect the complete preview. Use **Shift + left-click** or
   `/wand prefab rotate` to turn it. Ordinary left-click removes the anchor so you can choose a new
   location without selecting the design again.
5. Right-click the anchored preview to request a quote. This never starts placement by itself.
6. Read the block and Use totals, then click **PLACE** in chat. Every prefab requires this final
   confirmation, even when you can afford all of it.

Use `/wand prefab cancel` to leave prefab mode. Selecting an ordinary form also leaves prefab mode.
If a confirmation expires, the anchor remains so you can inspect it and request a new quote.

An exact prefab block already at the destination is kept for free. A solid conflicting block,
blocked interior clearance, protected cell, or living entity prevents the placement from starting.
Clear the problem and request a fresh quote. The wand never clears terrain for a prefab.

If resources are short, a prefab is red **BLOCKED** and offers no placement confirmation. Every
missing prefab cell plus the activation Uses must be affordable together. Restock or restore Uses,
then right-click for a new complete quote and use the normal **PLACE** confirmation.

You can rerun a reusable design at the same anchor after an interrupted placement. Exact finished
cells are kept for free, but every remaining printable cell must be affordable before that rerun
can begin.

### Blueprint Vouchers

Some shops may give a **Blueprint Voucher** instead of unlocking a design directly. Hold the
voucher in your main hand and run `/wand prefab redeem`. It is consumed only after the permanent
unlock is saved. A duplicate voucher is not consumed.

## Restoring Uses with Denarii

Hold one Builders Wand in your offhand, then request a Use-restoration quote:

- `/wand restore` — quote all missing Uses
- `/wand restore <uses>` — quote an exact amount, such as `/wand restore 500`
- `/wand restore cancel` — cancel your pending quote

The standard rate is **50 Denarii per Use**, with a **100-Use minimum**. Restoring an empty
5,000-Use wand therefore costs 250,000 Denarii. Server pricing may change, so the price shown in
your quote is always authoritative.

Requesting a quote does not take money. Review the Uses and price, then click **CONFIRM** in chat or
run `/wand restore confirm` before the quote expires. Changing wands, beginning a print, changing
the wand's Uses, or waiting too long invalidates the quote. You cannot restore Uses while one of
your prints is running.

## Checking your wand and history

Run `/wand` with the wand in your offhand to inspect its current shape, Shell/Solid preference,
Extend Surface restriction, remaining Uses, first wielder, serial, and live hotbar palette.

Run `/wand stats` at any time to see your lifetime building and Use-restoration totals across every
Builders Wand you have used. Those totals belong to your player account, so replacing a wand does
not reset them.
