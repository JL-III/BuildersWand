# BuildersWand
Player crafts a wand that allows primitive shapes with different dimensions to be built.

# TODO
- Determine the way a player chooses the shape.
- Determine the way a player chooses the dimensions.
- Determine the way a player chooses the orientation of the shape.
- How do we show where the player is going to order the shape creation?

Could have a player send a command while holding the stick?
We could have this plugin use worldguard region api and then we create a schematic minus the air blocks.
Then when a player tries to use the schematic it adopts the shape of the schematic but places the blocks a player is holding... hmmm.
Could we have the wand assigned the block type in a crafting table?
Maybe the shape is also determined in the crafting table.
where x is the material block and w is the wand.

so like a square wand would be created like this.
```
xx-
xx-
w--
```
A circle would be like this
```
-x-
x-x
wx-
```
A triangle would be like this
```
-x-
x-x
-w-
```

# Features
- Build primitive shapes (cube, sphere, cylinder) with different dimensions.
- Build shapes with different sizes.
- Build shapes with different orientations.