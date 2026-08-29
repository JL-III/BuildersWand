# Builders Wand — Shapes, Limits, and Reusable Prefabs

> **Status: implemented baseline; staged-server verification pending**
>
> **Date:** 2026-08-28
>
> This document records the product direction and safety/economy contract implemented in this
> worktree, including the 2026-08-29 all-or-nothing affordability amendment. Sections describing
> later expressive shapes remain future direction.

## 1. Outcome

The Builders Wand grows in two complementary directions:

1. Faster primitives for ordinary survival building: walls, floors, beams, pillars, and filled
   versions of the existing shapes.
2. Reusable prefabs that a player unlocks through the server shop, previews in the world, confirms,
   and pays for on every placement with ordinary blocks and wand Uses.

The wand remains a survival convenience tool. It does not become creative-mode WorldEdit: every
actual block still needs feedstock, Uses, permission, a safe target, and a visible preview.

## 2. Decisions at a glance

| Question | Implemented ruling |
|---|---|
| Add a Wall form? | **Yes.** It is a high-frequency shortcut, even though a one-block-deep Box can already make one. |
| Does Wall cover columns? | **Yes.** A one-block-wide Wall is a pillar. Add a general Line for pillars and beams rather than a separate Column form. |
| Other first additions | **Extend Surface, Line, and Floor.** Add roofs and decorative solids later. |
| Filled shapes | Add one **Shell / Solid** property; do not double the form list with separate “filled” forms. |
| Dimension limits | Separate the bounded candidate scan from the cells that would actually change after kept/occupied filtering, then also enforce span and touched-chunk guards. |
| 512-block line | **No.** It is technically 512 cells but operationally too long. Start Line at a configurable 64-block span. |
| Prefab access | A durable, player-global unlock, never state stored only on a particular wand. |
| Prefab purchase | The external shop owns the Denarii transaction; BuildersWand stores only the resulting unlock. |
| One-shot or reusable? | **Reusable.** Every placement pays normal cell Uses plus a configurable prefab activation cost. |
| Free prefab materials? | **No.** The player supplies the exact authored materials, preventing `/sell hand` materialization exploits. |
| Missing materials or Uses | **Block the entire start for every form.** Every printable cell must be affordable together; no subset placement is offered. |
| Texture shortage behavior | Never substitute another palette material or recompute the pattern. A missing material makes its assigned cells red and blocks the operation. |
| Status colors | Use the same semantics everywhere: **green = ready**, **yellow = supporting detail**, and **red = blocked/failure**, including resource shortages. |
| Confirmation | Required for every prefab placement and bound to the exact preview, inventory quote, wand, location, and rotation. |
| Prefab authoring anchor | Ignore WorldEdit's stored copy offset. Infer the lower-left corner of the canonical front face after an optional YAML source rotation. |
| Obstructions | Existing exact matches are kept. A conflicting non-replaceable block or obstructed authored-air clearance refuses the placement. Nothing is cleared as terrain. |
| Undo | No wand undo. Ordinary prints use LogBlock when available; prefabs fail closed by default unless player-attributed LogBlock logging is connected. |

## 3. Implemented runtime baseline

The implementation exposes eight forms and one shared configured legality policy:

| Form | Default spans | Density / restriction |
|---|---:|---|
| Diagonal | run 8, width 5 | Intrinsically solid |
| Box | 16 per axis | Shell or Solid |
| Cylinder | size 9, length 8 | Shell or Solid |
| Sphere / Capsule | size 7, length 8 | Shell or Solid |
| Wall | 32 × 32 | Intrinsically solid |
| Line | length 64 | Intrinsically solid |
| Floor | 32 × 32 | Intrinsically solid |
| Extend Surface | 64 × 64 search spans | Free, Row, or Column |

An ordinary plan may inspect at most 4,096 candidate cells, change at most 1,024 cells after
kept/occupied filtering, and touch at most 9 chunks by default. Individual dimension measurement
also stops at its configured span. A whole-operation limit refusal suppresses the block ghosts and
shows only the named reason plus a resize hint; it never appends a material or Uses quote for an
operation that cannot begin. Right-click refuses without clearing the session, so the player can
keep aiming to reduce the shape. These limits are loaded from `config.yml` and shared by
measurement, expansion, ghost status, and final preflight. Prefab admission remains a separate
512-cell, 8-chunk policy.

Every anchored ordinary plan has an independent thin gold marker with an aqua glow at the
immutable clicked anchor. It remains visible when the anchor cell is kept, when an over-limit plan
has no block ghosts, and when signed sizing moves the effective geometry origin. Downward sizing is
valid—for example, a Box can grow below its clicked anchor without redefining that reference point.

Palette capture and feedstock accounting ignore every customized `ItemStack` for which
`hasItemMeta()` is true. Custom names, lore, enchantments, model data, PDC, and other metadata can
therefore neither enter a palette nor be consumed as ordinary feedstock.

Box, Cylinder, and Sphere/Capsule implement a persisted Shell/Solid preference. Extend Surface
performs a bounded exact-BlockData traversal and binds the source cells so a changed face cannot be
placed from a stale preview.

Every form now uses exact all-or-nothing resource admission. Material assignment happens before
affordability, and every printable cell must have its assigned feedstock and Use cost before a wave
can begin. A shortage blocks without placing or spending. Prefabs use the same full-plan budget
with authored materials, strict clearance, activation Uses, and mandatory confirmation after the
complete operation becomes affordable.

`PlacementLogger` includes a runtime adapter to LogBlock's player actor and place/replace consumer
API. Ordinary forms retain optional logging. With `prefabs.require-logblock: true`, prefab preflight
fails closed if that adapter is unavailable. The exact installed LogBlock version and rollback
lookup still require a staged-server smoke test before production rollout.

## 4. Prior art and the survival-building lesson

Official documentation for comparable tools shows a stable interaction vocabulary:

- [Construction Wand](https://github.com/Theta-Dev/ConstructionWand/blob/1.20.2-2.12/README.md) focuses on extending an
  existing face and offers row/column restrictions. Its tiers cap a use at 9, 27, 128, or 1,024
  blocks, and survival placement draws blocks from inventory or supported containers.
- [Effortless Building](https://github.com/Requios/effortless-building-multi) exposes Line, Wall,
  Floor, Cube, diagonal variants, Circle, Cylinder, and Sphere, with configurable reach and
  per-operation block limits. It also demonstrates that mirror and array modifiers eventually add
  more expression than an endlessly growing primitive menu.
- [Building Gadgets 2](https://github.com/Direwolf20-MC/BuildingGadgets2/tree/21.6) gives direct
  row, column, wall, surface, stairs, and grid modes. Its
  [Copy/Paste templates](https://github.com/Direwolf20-MC/BuildingGadgets2/blob/21.6/src/main/resources/assets/buildinggadgets2/patchouli_books/buildinggadgets2book/en_us/entries/copypaste.json)
  are reusable while each paste again consumes real materials and per-block energy, making it the
  clearest precedent for reusable, pay-per-placement prefabs.
- [WorldEdit's generation tools](https://worldedit.enginehub.org/en/latest/usage/generation/)
  distinguish hollow and filled circles, cylinders, spheres, and pyramids. That is useful evidence
  for the shape vocabulary, but not a reason to inherit WorldEdit's creative-scale limits.

The design inference is that survival tools are easiest to understand when common intent has a
short path, resource consumption remains visible, and large operations are bounded. We should
borrow the vocabulary, not the unrestricted scale.

### 4.1 Resource shortages in prior art

There is no universal atomic-placement rule, but mature survival building tools lean strongly
toward progressive placement:

- Construction Wand builds only the connected cells its current inventory can supply. Its
  [inventory supplier](https://github.com/Theta-Dev/ConstructionWand/blob/15089d96dd04c26b9110b44388a6133598d744d7/src/main/java/thetadev/constructionwand/wand/supplier/SupplierInventory.java#L44-L99)
  stops expansion when blocks run out, so a shortage produces a smaller operation rather than a
  complete refusal.
- Building Gadgets 2 documents that ordinary builds and copy/paste templates skip cells whose
  items are unavailable. It pairs that behavior with unavailable-cell coloring and a required-
  versus-available material list in its
  [building guide](https://github.com/Direwolf20-MC/BuildingGadgets2/blob/cfaf541b842b9a075d587a6718a0075f3a209c14/src/main/resources/assets/buildinggadgets2/patchouli_books/buildinggadgets2book/en_us/entries/buildinggadget.json#L11-L27)
  and [copy/paste guide](https://github.com/Direwolf20-MC/BuildingGadgets2/blob/cfaf541b842b9a075d587a6718a0075f3a209c14/src/main/resources/assets/buildinggadgets2/patchouli_books/buildinggadgets2book/en_us/entries/copypaste.json#L20-L35).
- Effortless Building deliberately allocates the available subset, marks missing cells in red,
  shows available and missing item counts, and places nearer cells first. Its
  [availability calculation](https://github.com/Requios/effortless-building-multi/blob/5e38603322cf30b120283a06e796b36dc43c1509/common/src/main/java/nl/requios/effortlessbuilding/utilities/ItemUsageTracker.java#L90-L147)
  and [preview renderer](https://github.com/Requios/effortless-building-multi/blob/5e38603322cf30b120283a06e796b36dc43c1509/common/src/main/java/nl/requios/effortlessbuilding/render/BlockPreviewRenderer.java#L105-L197)
  make the missing portion explicit.
- Create's Schematicannon can start without every material. Its default is to pause at the next
  shortage and resume after restocking; **Skip Missing Blocks** is a separate explicit option that
  leaves holes. The [official printing guide](https://github.com/Creators-of-Create/Create/wiki/Printing-a-Schematic)
  documents both behaviors. When skipping accidentally became the default, players reported it
  and the maintainers confirmed it as a bug in
  [Create issue #9543](https://github.com/Creators-of-Create/Create/issues/9543).

Prior art demonstrates that progressive placement can work, but it also makes shortages, holes,
and continuation state part of the core interaction. BuildersWand deliberately chooses the simpler
survival contract: the preview and result must match completely at wave admission. Every printable
cell is budgeted before the first placement, missing assignments are red, and any resource shortage
blocks the entire start. Players can resize or restock without learning a separate continuation
workflow. Mid-wave interruption and entity skips remain runtime safety outcomes, not economic
permission to begin an underfunded build.

## 5. Primitive catalog

### 5.1 Implemented rollout and future direction

#### Implemented — frequent construction

1. **Extend Surface** — extend a connected exposed face, with free, row, and column restrictions.
2. **Wall** — a world-vertical rectangle, one block thick.
3. **Line** — an axis-aligned row, beam, or pillar.
4. **Floor** — a horizontal rectangle, one block thick.

Wall and Floor are intentionally separate player-facing forms. A generic Plane is slightly more
compact in code, but separate names make the orientation predictable before the first click.

#### Implemented — density

5. **Shell / Solid** — a property shared by Box, Cylinder, Sphere/Capsule, and future round forms.

#### Future — architectural expression

6. **Pyramid / Cone** — roofs, towers, monuments, and landscaping.
7. **Dome / Arch** — common architectural portions that are awkward to derive from a complete
   sphere or cylinder.
8. **Mirror and Array modifiers** — after the base interaction is stable. These multiply the value
   of every primitive and are more useful than adding many niche shapes.

Torus, helix, arbitrary curves, player capture/copy, and free-form selection remain out of scope
for this expansion. Server-curated prefabs cover complex authored structures without turning the
wand into WorldEdit.

### 5.2 Wall interaction

Wall should take one anchor and one opposite-corner aim:

1. Right-click a surface to set the anchor corner.
2. Move the crosshair; the ghost changes width and rise together.
3. Right-click again to request the print.

The height axis is always world vertical, including when the anchor was selected on the top or
bottom of a block. The width axis is the dominant horizontal direction from the anchor. Negative
drag grows from the same anchor in the opposite direction.

Special cases are useful rather than errors:

- Width `1` produces a pillar.
- Height `1` produces a horizontal row.
- `1 × 1` produces one block.

### 5.3 Line interaction

Line uses one anchor and one endpoint. The dominant world axis determines X, Y, or Z, so the same
form makes horizontal rows, beams, and vertical columns. It does not replace Diagonal, whose
purpose remains a rising stair run with configurable tread width.

### 5.4 Floor interaction

Floor uses one corner and one opposite-corner aim in the horizontal plane. It is always one block
thick. It avoids selecting a three-dimensional Box simply to make a platform, ceiling, or roof
deck.

### 5.5 Extend Surface interaction

The targeted block and clicked face identify a connected, coplanar source surface. One right-click
previews the layer immediately outside that surface; the next right-click commits it. Restrictions
cycle between:

- **Free** — all connected matching face cells within the operation limits.
- **Row** — only the dominant horizontal row.
- **Column** — only the vertical column.

The source search must stop at the configured cell, span, and chunk limits. It may match exact
BlockData by default; a later “same material” option can deliberately ignore orientation.

### 5.6 Universal all-or-nothing affordability

Resource admission is shared by every ordinary form and every prefab. Prefabs are not an economic
exception, although they retain their separate confirmation step.

1. Material assignment happens for the complete geometry before affordability. If an oak-assigned
   cell cannot be supplied, that exact cell glows **red**; stone never substitutes for it and the
   deterministic texture never rephases.
2. The complete printable set and its full Use cost are evaluated together. All assigned blocks
   and Uses must be available before anything can begin.
3. A shortage makes the overall operation red **BLOCKED** and reports the exact missing materials
   and Uses. Cells lacking their assigned resources are red in the normal preview. Nothing is
   placed, removed from inventory, or deducted from the wand.
4. Ordinary forms offer no shortage confirmation. Prefabs offer their mandatory **PLACE**
   confirmation only after the complete printable plan plus activation Uses is affordable.
5. Restocking or restoring Uses recomputes the live preview. There is no frozen placement subset,
   subset confirmation, or retained shortage continuation.
6. Final commit or prefab confirmation rechecks inventory, Uses, wand, palette or authored plan,
   world, anchor, dimensions, rotation, protection, and target state. A changed input either
   remains blocked or produces a fresh complete plan; it never admits a different subset.

Once a fully funded wave starts, materials and Uses are still charged per successfully completed
cell so a runtime failure can leave all unprocessed resources untouched. Kept cells and failed or
entity-skipped cells cost nothing. Water follows the same admission rule: the bucket remains a
catalyst, and the wand must afford the configured per-source Use cost for every printable source
before the wave starts.

### 5.7 Unified status colors and language

The wand should use one status vocabulary across preview glows, action bars, chat, confirmations,
and completion messages. Use Minecraft's bright named colors consistently:

| State | Color | Meaning | Preview behavior | Text label |
|---|---|---|---|---|
| Ready | Green (`§a`, `#55FF55`) | The quoted plan can proceed completely. | Every cell that will be placed glows green. | **READY** |
| Supporting detail | Yellow (`§e`, `#FFFF55`) | Non-blocking information needs the player's attention. | Only the relevant supporting detail is yellow; it never implies that an underfunded subset can place. | Overall status remains **READY** or **BLOCKED**. |
| Blocked / failure | Red (`§c`, `#FF5555`) | Nothing may begin: any missing material or Use, protection denial, conflict, illegal geometry, unsafe clearance, stale quote, or another hard failure. | Resource-unavailable cells and printable/conflicting cells glow red; whole-operation limit refusals use the special presentation below. | **BLOCKED** |

Kept cells are not a status and should remain absent from the ghost or use a neutral treatment;
they must not appear green as though they will be charged and placed. **PLACE** is green and exists
only for a fully affordable prefab; **CANCEL** remains neutral rather than red.

A whole-operation scan, changed-cell, span, or chunk refusal is a special blocked presentation.
Suppress all of its block ghosts rather than rendering an impossible operation as if some part
might place. Keep the independent gold/aqua clicked-anchor marker and show the exact limit reason
with `Aim closer to reduce the shape · LEFT cancels`. Do not append material availability, Uses,
or `RIGHT requests print`: right-click must refuse without spending or clearing the anchored
session, leaving the player free to resize it.

Water's configured Use multiplier is a yellow supporting detail, not a warning state: a legal,
fully affordable water plan remains green **READY**. A shortage or illegal water plan is
**BLOCKED**.

The primary action-bar line, chat prefix, and confirmation action must use the operation's overall
state color. Supporting details may remain white or gray for readability, but must not contradict
the state. A completed wave reports in green; a refusal reports in red with the reason. For
example:

```text
READY — 120 blocks · 120 Uses
BLOCKED — missing Stone ×24, Oak Planks ×13 · Uses 83 / 120
```

Color is reinforcement, not the only signal. Every state retains its explicit word, exact counts,
and reason so color-blind players and players using unusual resource packs receive the same
information.

## 6. Shell and Solid

Filled shapes should be a mode property, not separate entries in the form cycle.

- **Shell** remains the default and preserves current behavior.
- **Solid** fills the complete enclosed volume.
- Wall, Floor, Line, and Diagonal are intrinsically solid and ignore the property.
- A Shell Cylinder remains the current open tube; a Solid Cylinder is a filled cylindrical prism.
- A Shell Sphere/Capsule remains a one-cell skin; Solid fills its interior.
- A Shell Box remains a room shell; Solid fills the cuboid.

The action bar and ghost must say `SHELL` or `SOLID` prominently. Switching to Solid immediately
recomputes the ghost, exact material requirements, and Uses. Solid should never be the implicit
default because hidden interior blocks are much more expensive in survival.

The property can live on the wand as a preference; losing a wand may lose that preference but must
not lose economic history or prefab ownership.

## 7. Limit model

### 7.1 Cell budget is necessary, but not sufficient

The user's proposed principle is correct: a thin operation should generally be allowed to grow
longer than a volumetric one. The legal next dimension should be derived from the shape's actual
expanded cell count rather than an unrelated fixed 1–8 table.

Cell count alone is not enough. A 1,024-block line could cross about 65 chunks and expose 1,024
ghosts over an unusable distance. It is much riskier than a compact 1,024-cell wall even though
both would change the same number of blocks. A candidate-volume guard is also necessary: kept and
occupied cells should not consume the mutation budget, but the server must still bound the work
required to expand and inspect them.

Every plan must therefore satisfy all of these limits:

1. `candidate cells <= max-scanned-cells-per-plan`
2. `printable cells <= max-cells-per-print` after kept/occupied filtering
3. every semantic span is within that form's `max-span`
4. `touched chunks <= max-chunks-per-print`
5. the anchor remains within ordinary anchor reach
6. the complete ghost and preflight can be produced inside the server's time budget

### 7.2 Recommended initial defaults

| Guard | Initial value | Reason |
|---|---:|---|
| Changed cells per ordinary print | 1,024 | Counts only actual mutations after kept/occupied filtering, matching resource and wave cost. |
| Candidate cells scanned per ordinary plan | 4,096 | Bounds geometry and world inspection even when most targets are already built or occupied. |
| Line span | 64 | Long enough for roads and beams; at most five chunks when aligned poorly. |
| Wall/Floor axis span | 32 | Allows a complete `32 × 32` plane when all 1,024 cells need placement. |
| Box axis span | 16 | Lets shells become less cube-bound without authorizing huge solids. |
| Touched chunks | 9 | Allows a `32 × 32` plane to cross a `3 × 3` chunk grid at an unlucky anchor. |
| Anchor reach | 16 | Preserve current interaction reach. |
| Prefab non-air cells | 512 | Reuses the existing preview, protection, feedstock, Uses, and wave pipeline. |
| Prefab bounding span | 32 per axis | Keeps clearance checks and previews local. |
| Prefab touched chunks | 8 | Remains independent from the ordinary-form chunk budget. |

These are safe starting values, not permanent magic numbers. They should be configuration with
hard validation at startup.

### 7.3 Centralized legality

Introduce one `FormLimits`/plan-policy service used by:

- live dimension measurement,
- the geometry expander,
- ghost generation,
- final preflight,
- prefab catalog validation, and
- configuration diagnostics.

The current axis bounds are duplicated between gesture measurement and `Dims` validation. Dynamic
limits should not add a third copy. The same proposed dimensions must always produce the same
legal/refused answer and named reason.

For example:

- A `64 × 1` Line is legal if it stays within the chunk limit.
- A `32 × 32` Wall is exactly 1,024 candidate cells and is legal when all 1,024 need placement.
- A 1,352-cell Box shell may still be legal when at least 328 cells are already built or occupied
  and the other guards pass, because no more than 1,024 cells would actually change.
- A 4,096-cell Solid Box reaches the scan ceiling, but is refused by the change limit if all 4,096
  cells would be placed.
- Any plan with more than 4,096 candidate cells is refused before world-state filtering.
- A larger-radius Sphere naturally permits a shorter capsule axis than a small-radius one.
- Switching a legal shell to Solid may make it illegal; the action bar must show the refusal
  instead of silently reducing the requested dimensions.

## 8. Reusable prefabs

### 8.1 Product contract

A prefab is a reusable building design that the player unlocks once and pays to place each time.
For example, the server shop might sell the **Starter House** design for 150,000 Denarii. The shop
price grants access; it does not buy one paste.

Every placement then consumes:

1. one matching inventory item for every newly placed prefab cell,
2. the normal per-cell wand Uses, including any configured water multiplier if water is supported
   in a later prefab version, and
3. a small configurable prefab activation cost.

There is no separate Denarii withdrawal inside the placement path. Use restoration remains the
single recurring Denarii sink. At the current 50 Denarii per Use, a 420-block house with a 20-Use
activation cost consumes 440 Uses, equivalent to 22,000 Denarii when those Uses are restored. The
20-Use surcharge is only 1,000 Denarii of that total; the ordinary per-block Uses do most of the
recurring economic work.

The suggested `10–20 Uses` is a flat activation cost, not a per-cell multiplier. Ten Uses per cell
would make the same 420-block house cost 4,200 Uses; twenty per cell would exceed the current
5,000-Use wand maximum. Per-cell multiplication is unnecessary because the ordinary one-Use-per-
block cost already scales with prefab size.

Recommended activation defaults:

| Prefab tier | Activation Uses | Role |
|---|---:|---|
| Small decoration | 10–20 | Light convenience fee. |
| House / medium structure | 20–50 | Noticeable but secondary to cell Uses. |
| Large or especially valuable layout | 50–100 | Stronger recurring sink. |

Each prefab overrides the global default. The shop's 150,000-Denarii example remains configured in
the shop, not duplicated as an authoritative BuildersWand setting.

### 8.2 Ownership and shop handoff

Prefab access must be player-global. It must not live only on a wand PDC, because replacing a lost
wand should not erase a paid design.

The recommended v1 entitlement is a small durable table keyed by player UUID and stable prefab ID:

```text
prefab_unlocks(player_uuid, prefab_id, unlocked_at, source, PRIMARY KEY(player_uuid, prefab_id))
```

This is entitlement state, not an economy ledger. BuildersWand never records or calculates the
shop's Denarii transaction.

The shop can deliver the unlock in either of two ways:

1. Preferred when the shop can run console commands: after payment, run
   `/wand prefab grant <player> <prefab-id>`.
2. Essentials-kit fallback: the kit gives a PDC-backed **Blueprint Voucher**. The player redeems
   it with `/wand prefab redeem`; the voucher is consumed only after the durable unlock is written.

The voucher is one-use because it conveys the unlock. The prefab itself is reusable. Redeeming a
duplicate refuses without consuming the voucher. If the entitlement store is unavailable, grant,
redeem, autocomplete, and placement fail closed so paid access cannot be lost or bypassed.

Permissions can additionally grant staff or rank access (`builderswand.prefab.<id>` and
`builderswand.prefab.all`), but normal shop ownership should survive permission-plugin changes.

### 8.3 Player commands

```text
/wand prefab                         list unlocked designs
/wand prefab <id>                    select a design; tab-complete only accessible IDs
/wand prefab confirm <opaque-token>  accept the exact current quote
/wand prefab cancel                  cancel preview or pending confirmation
/wand prefab redeem                  redeem the held Blueprint Voucher
```

Administrative surface:

```text
/wand prefab grant <player> <id>
/wand prefab revoke <player> <id>
/wand prefab voucher <player> <id>
/wand prefab validate <id>
/wand prefab reload
```

Grant, revoke, voucher creation, redemption, reload, and every confirmed placement receive an
audit-log entry.

### 8.4 Placement interaction

Prefab placement has four explicit states:

```text
SELECTED → ANCHORED → AWAITING CONFIRMATION → PLACING
```

1. **Selected:** the player runs `/wand prefab <id>` while their wand is in the offhand. The design
   is armed, but cannot place anything. A lightweight footprint/origin indicator follows the target
   so a large full ghost is not rebuilt at every unanchored aim update.
2. **Anchored:** the first ordinary right-click maps the prefab's inferred lower-left-front anchor to
   the clicked world cell and freezes its initial rotation. By default the prefab's front faces the
   player at anchor time. The complete prefab ghost appears. The player can walk around it and
   inspect the build, clearance, missing materials, conflicts, and Use cost. Shift + left-click
   rotates the whole prefab 90 degrees; rotating invalidates any older quote. Ordinary left-click
   removes the anchor while keeping the prefab selected, making it easy to choose a different
   location. A command alternative should exist for rotation and cancellation.
3. **Awaiting confirmation:** a second ordinary right-click on the anchored preview requests the
   exact confirmation quote. It **does not place blocks**. Conflicts and hard safety failures
   refuse with the exact reason and remain anchored. A resource shortage also refuses: every
   printable cell and the activation Uses must be affordable together before the player receives
   **PLACE** and **CANCEL**.
4. **Placing:** clicking **PLACE**, or running `/wand prefab confirm <opaque-token>`, performs one
   final revalidation. Only that successful confirmation can charge the activation cost or start
   the paced wave.

Selecting a normal form exits prefab mode. Selecting another prefab replaces the preview. Moving
world, losing the offhand wand, logging out, or quote expiry cancels the pending confirmation.
Quote expiry returns to the anchored state rather than discarding the player's carefully chosen
location. Any anchor or rotation change requires a fresh quote.

### 8.5 Confirmation copy

Confirmation is required for every placement, even though the design is reusable. It prevents an
expensive build at the wrong anchor or rotation.

Example:

```text
READY — Starter House — 17 × 9 × 14
Place 420 blocks; keep 8 matching blocks; 0 conflicts
Uses: 420 placement + 20 prefab = 440
Materials: Oak Planks ×180, Cobblestone ×112, ...
Make sure the preview and cleared area are correct. There is no wand undo.
[PLACE] [CANCEL]  Expires in 30s
```

Shortage refusal:

```text
BLOCKED — Starter House — 17 × 9 × 14
Missing: Oak Planks ×64, Glass ×30, Cobblestone ×14
Uses: 332 available / 440 needed
Every printable cell must be affordable. Nothing was placed or spent.
```

The unavailable cells are red in the preview and no confirmation token or **PLACE** button is
issued. After restocking, the player right-clicks again for a fresh complete quote.

Do not say the design is one-shot or that it cannot be rerun. It can be placed repeatedly and can
be rerun at the same anchor to repair missing cells. The warning is about the absence of undo for
blocks that were actually placed.

The opaque confirmation token must bind at least:

- player UUID,
- wand identity and exact current Uses,
- prefab ID plus catalog version/content hash,
- world, anchor, and rotation,
- exact expanded plan hash,
- exact complete material requirements and inventory availability,
- activation cost,
- issue time and expiry.

Any changed input invalidates the quote and asks the player to preview again. An old clickable
button must never approve a newer plan.

### 8.6 Existing blocks and reruns

Prefab matching is stricter than the current primitive “occupied means kept” rule:

- Air or another replaceable world cell means the expected block is missing and should be placed.
- The exact expected BlockData already present is **matching/kept** and costs nothing.
- A different non-replaceable block or important BlockData is a **conflict** and refuses the
  complete placement before anything is spent.
- If every prefab block already matches, refuse as already complete and spend nothing.

This makes rerunning safe and useful: an interrupted house can be completed without
repaying for its finished cells. It also prevents a tree, chest, or wall from being silently
incorporated into a malformed prefab.

Air inside the authored schematic is clearance, not an instruction to delete blocks. A non-air
block in required clearance is an obstruction. `structure_void` or explicit metadata can mark
cells that the prefab does not care about. BuildersWand never clears terrain for a prefab.

### 8.7 Materials and block states

Prefab v1 uses its authored materials and BlockData, not the live hotbar texture palette. The
inventory supplies matching items from the same feedstock rules used by ordinary prints. This
keeps roofs, stairs, logs, slabs, windows, and deliberate material patterns faithful to the design.

The plugin must not include free materials with a prefab. Free blocks could be placed, mined, and
sold through the server worth table. Requiring exact feedstock eliminates that materialization
loop while retaining the convenience value of the design.

Initial catalog validation should allow ordinary item-backed blocks and reject or strip:

- entities and entity NBT,
- block entities and inventory/content NBT,
- command blocks, structure/jigsaw blocks, barriers, bedrock, and other administrative blocks,
- containers, shulker boxes, signs/books with content, spawners, and decorated pots,
- doors, beds, and other multi-block items until their placement/debit semantics are explicit,
- fluids in prefab v1,
- any block without a safe one-item-to-one-cell feedstock mapping.

Rotation must transform all directional BlockData, not only coordinates. A prefab is rejected at
catalog load if any state cannot be safely rotated or accounted for.

### 8.8 File and catalog format

Use the standard Sponge `.schem` format for authoring/import, then validate it into an immutable
runtime prefab definition at reload. The [Sponge Schematic v3 specification](https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-3.md)
defines dimensions, offsets, block-state palettes, block entities, and entities; BuildersWand
intentionally accepts only the safe subset above.

WorldEdit is the intended authoring tool. A repeatable administrator workflow is:

1. Build the prefab in a staging world. By convention, the authored front faces south. If it does
   not, set `source-rotation` in the metadata rather than rebuilding or manipulating a clipboard
   origin.
2. Make a tight cuboid selection around the entire structure. Interior air inside the outermost
   placeable blocks becomes clearance; complete exterior air planes are trimmed, so padding beyond
   those outermost blocks cannot reserve headroom in v1.
3. Stand anywhere convenient and run `//copy` without entity or biome flags, then
   `//schem save builderswand/<id>`. WorldEdit 7 writes a Sponge `.schem`; its stored player-relative
   copy offset is deliberately ignored by BuildersWand.
4. Copy the resulting file from
   `plugins/WorldEdit/schematics/builderswand/<id>.schem` into
   `plugins/BuildersWand/prefabs/<id>.schem` and add the adjacent metadata file below.
5. Run `/wand prefab validate <id>`. A successful report shows the name, version, inferred source
   anchor, dimensions, non-air cells, clearance cells, and activation Uses. A failed report lists
   the rejected metadata, block, or state issues. Validation changes nothing.
6. Run `/wand prefab reload` only after validation succeeds, then grant yourself access and test all
   four rotations in a staging claim.

The WorldEdit commands and origin behavior are documented in its
[clipboard guide](https://worldedit.enginehub.org/en/latest/usage/clipboard/) and
[command reference](https://worldedit.enginehub.org/en/latest/commands/), but authors do not need to
manage that origin for this system. At catalog load, BuildersWand:

1. applies `source-rotation` to coordinates and directional BlockData,
2. trims complete exterior air-only planes to find the tight structure bounds while preserving air
   inside those bounds as clearance,
3. treats the canonical south-facing facade as the front, and
4. chooses the bottom-left corner of that front face as local anchor `(0, 0, 0)`.

Viewed from outside the canonical front, the prefab grows right, up, and inward from that anchor.
This is independent of the WorldEdit selection order, the author's position during `//copy`, and
the schematic's stored offset.

Each file has adjacent server-owned metadata:

```yaml
id: starter_house
name: Starter House
version: 1
schematic: starter_house.schem
# Clockwise quarter-turn applied once at import so the authored front becomes canonical south.
# Allowed values: 0, 90, 180, 270.
source-rotation: 0
activation-uses: 20
allow-rotation: true
clearance: schematic-air
```

The stable ID is the entitlement key. Updating a prefab version updates what already-unlocked
players can place, so catalog changes require deliberate versioning and a preview hash. The
Denarii shop price is not duplicated here.

Catalog reload is atomic: validate every changed prefab into a new catalog first, then swap it in.
An invalid file never replaces only part of the live version. Existing confirmation tokens become
invalid when their prefab hash changes.

### 8.9 Safety and accounting invariants

Prefab placement must reuse the existing per-cell wave path rather than calling a bulk schematic
paste API. That preserves:

- WorldGuard and Lands checks for every target and required-clearance cell,
- complete-plan resource classification before the first block,
- all-or-nothing material, cell-Use, and activation-Use admission,
- per-completed-cell inventory and Use charging,
- player-attributed LogBlock placement logging when the runtime hook is connected,
- chunk tickets and paced placement,
- global player build statistics, and
- the no-teleport entity policy.

The final confirmation rechecks every condition. A hard preflight refusal, any resource shortage,
or an expired/changed quote spends no materials, no Uses, and no activation cost.

That recheck includes the world border, build height, touched chunks, safe replaceability,
required-clearance volume, and entity intersections in addition to access, protection, materials,
and Uses.

The complete material and cell-Use budget is checked together with the activation cost before a
confirmation is issued, and the activation cost is charged only when the fully revalidated wave
starts. If no cell can begin because of an internal failure, restore it. Once at least one cell is
successfully placed, the activation work has been used and the activation cost remains spent;
ordinary cell Uses and materials are still charged only for completed cells.

A living entity intersecting any solid prefab target refuses final confirmation. If an entity
enters during the paced wave, use the existing defer/retry behavior and leave an uncharged gap if
it remains. The completion message must identify the gap and explain that the player can clear the
area and rerun the same reusable prefab at the same anchor. The wand never pushes or teleports an
entity.

Wrong blocks, lost permission, a changed wand, logout, or server interruption can leave an
incomplete physical structure, just as an interrupted primitive wave can today. A rerun safely
keeps exact matches, but all remaining printable cells must be affordable before it starts. This
implementation does not add a wand-level rollback system.

### 8.10 Prefab size

Prefab v1 should remain at 512 non-air cells so it can reuse the current full-cell ghost and wave
implementation. A compact starter-house shell can fit within that budget.

If desirable shop prefabs exceed 512 cells, do not merely raise the constant. A larger-prefab
phase first needs:

- a sampled or outline preview rather than one display entity per block,
- a separate `max-prefab-cells` and clearance-volume cap,
- persisted/resumable placement jobs across disconnects and restarts,
- stronger chunk scheduling and per-tick work budgets, and
- clear recovery tooling for interrupted jobs.

Splitting a large build into separately unlockable foundation, shell, roof, and interior sections
is a safer initial alternative and creates useful progression in the shop.

## 9. Implemented configuration

```yaml
limits:
  max-cells-per-print: 1024
  max-scanned-cells-per-plan: 4096
  max-chunks-per-print: 9
  max-span:
    line: 64
    wall: 32
    floor: 32
    box: 16

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

Configuration validation must reject negative Use costs, zero/negative limits, an activation cost
larger than the configured wand maximum, and internally inconsistent span/cell/chunk values.

## 10. Delivery record

### Implemented — limit foundation

- Centralize cell/span/chunk legality.
- Separate the configurable 4,096-cell candidate scan ceiling from the 1,024-cell actual-change
  ceiling applied after kept/occupied filtering.
- Raise the ordinary chunk default to 9 while retaining separate 512-cell and 8-chunk prefab
  admission.
- Keep an independent gold/aqua marker on the immutable clicked anchor, including during signed
  downward sizing and whole-operation limit refusals.
- Suppress block ghosts and resource quotes for whole-operation limit refusals while retaining the
  adjustable anchored session.
- Remove duplicated gesture/validator bounds.
- Add unit tests at every exact boundary and just beyond it.

### Implemented — atomic resource admission

- Assign the complete deterministic material pattern before checking affordability; never
  substitute materials or rephase the texture.
- Require every printable cell and its Use cost to be affordable together before a wave can start.
- Show resource-unavailable cells and the overall shortage as red **BLOCKED**, with exact missing
  materials and Uses and no subset confirmation.
- Keep prefab confirmation for fully affordable plans, bound to the exact complete quote.

### Implemented — common primitives

- Add Extend Surface, Wall, Line, and Floor to geometry, preview, form cycle, commands, lore/name,
  and action bar.
- Keep one geometry truth shared by ghost and commit.
- Add negative-drag, every-face, every-heading, kept-cell, protection, and entity tests.

### Implemented — density

- Add Shell/Solid state and costs.
- Test exact geometry counts and refusal when switching from a legal shell to an over-budget solid.

### Implemented — prefab catalog and entitlements

- Load and validate safe `.schem` subsets and metadata.
- Add durable player unlocks, shop grant, voucher redemption, filtered autocomplete, and audits.
- Fail closed on entitlement-store problems.

### Implemented — prefab preview and placement

- Add anchor/rotation preview, strict conflict/clearance preflight, bound confirmation, and existing
  per-cell wave integration.
- Add exact rerun behavior, accounting, protection, LogBlock, entity, stale-token, reload-race, and
  restart/disconnect tests.

### Future — expressive follow-up

- Evaluate Pyramid/Cone, Dome/Arch, and modifiers using live player feedback rather than committing
  to the entire catalog at once.

## 11. Release verification checklist

The implemented baseline is ready for production only when all of the following are verified:

- A player can make a wall, floor, beam, or pillar with one anchor and one final aim.
- Ghost and commit use the same cells and the same centralized limit answer.
- A legal thin form can grow farther than a volumetric form without crossing span or chunk limits.
- Kept/occupied cells consume neither the 1,024-change budget nor resources, while every ordinary
  plan stays within the separate 4,096-candidate scan ceiling and 9-chunk guard.
- The original clicked anchor remains visible as a gold/aqua marker when a Box grows downward or a
  whole-operation limit hides all block ghosts.
- A whole-operation limit refusal names the limit and resize action without quoting material or
  Uses, and right-click leaves the preview anchored and adjustable.
- Solid mode cannot silently exceed its quoted cells, items, or Uses.
- Every form keeps the full intended ghost on a resource shortage, marks unavailable assigned cells
  red, and blocks the entire start.
- Preview, action bar, chat, and confirmation messages consistently use green for ready, yellow for
  supporting details, and red for blocked/failure without relying on color alone.
- No shortage confirmation or subset placement is offered. Restocking can make the complete
  live plan ready without changing its deterministic material assignments.
- Any resource shortage spends nothing; kept and entity-skipped cells cost nothing.
- `/wand prefab` reveals and completes only designs the player has unlocked or is permitted to use.
- A paid unlock survives restarts and wand replacement.
- The same prefab can be placed repeatedly; no placement consumes the unlock.
- Every placement has an exact, expiring confirmation and revalidates before spending.
- Preflight failure spends nothing.
- Existing exact prefab blocks are kept free; any conflict refuses the entire start.
- Prefabs consume exact player materials and cannot create sellable matter for free.
- Protection, logging, statistics, entity safety, and per-cell accounting behave like ordinary wand
  placement.
- A real player-attributed LogBlock hook is present and verified before prefab launch, unless the
  owner has explicitly accepted operation without rollback attribution.
- An interrupted prefab can be rerun at the same anchor; exact matches are kept, and every
  remaining printable cell plus activation Uses must be affordable before confirmation.
- There is no claim of rollback or refund for blocks that were successfully placed.

## 12. Remaining rollout choices

The broad direction is implemented. These values and follow-ups still benefit from playtesting:

1. Whether the shop can call the grant command directly or needs Blueprint Vouchers in Essentials
   kits.
2. Whether `20` or `50` is the best default house activation cost; per-cell Uses already dominate
   the recurring price.
3. Whether the initial form spans, 1,024-change/4,096-scan ordinary limits, and 9-chunk guard feel
   generous without making ghosts or paced waves too expensive.
4. Whether a 512-cell house catalog is expressive enough before investing in resumable large jobs.
5. Which safe multi-block materials, if any, are important enough to support after prefab v1.
