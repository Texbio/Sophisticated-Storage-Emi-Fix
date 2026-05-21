# Sophisticated Storage EMI Fix (v2)

Client-side Forge mod for Minecraft 1.20.1. Cleans up Sophisticated Storage's
recipe display in EMI/JEMI using EMI's public API only — no reflection on EMI
internals, no Mixins, no per-tick listeners.

## What it does

### Removes generic placeholder recipes
The vanilla JSON recipes that use a `#minecraft:planks` tag (and a few other
generic serializers) are filtered out via `EmiRegistry.removeRecipes(predicate)`.
Per-variant synthetic recipes from Sophisticated Storage's own EMI plugin take
their place, so the visible recipes show specific wood types instead of "any
plank".

### Replaces multi-variant dye recipes
Sophisticated Storage's synthetic dye recipes build their storage ingredient
from every wood variant (oak chest, birch chest, spruce chest, cherry, bamboo,
…), and EMI displays that as a cycling slot. This mod removes those synthetic
recipes and adds drop-in replacements that **display** a single specific wood
variant (chests → oak, barrels → spruce, shulkers → no wood type) while
**indexing** under every wood variant — so the slot doesn't cycle, but the
recipe still shows up when you click any wood-variant chest in the sidebar.

The replacement output stack carries only `mainColor`/`accentColor` — no
`woodType` — to match the colored entries Sophisticated Storage adds to the
sidebar (`fillItemCategory` builds those without a wood type). With a wood
type baked into the output, the chest/barrel subtype comparison would mismatch
and the recipe wouldn't be findable from any sidebar entry.

### Lays the recipe out at the top-left
EMI's `EmiCraftingRecipe` centers narrow recipes by computing a slot offset
from `canFit(1, 3)` / `canFit(3, 1)`. For the synthetic dye recipes — a 2-item
list flattened from a 1×2 shape — `canFit(3, 1)` is true, so storage and dye
end up in the middle row.

The previous fix tried to defeat this by padding the input list to 4 with empty
stacks. That doesn't work because `canFit` only counts non-empty cells: the
padding is invisible to the check and the recipe still lands in the middle row.

This version uses a small subclass — `SophDyeEmiRecipe` — that stores the true
shape (1×2 for single-color, 3×1 for multi-color) and renders the slots
directly into the top-left of the 3×3 grid, bypassing the centering entirely.

## Difference from v1

| | v1 (old) | v2 (this) |
|---|---|---|
| Removes generic serializers | ✓ (`removeRecipes` API) | ✓ (kept) |
| Removes synthetic dye recipes | reflection on `EmiRecipes.manager.byInput` every tick | `removeRecipes` predicate |
| Single-variant storage display | reflection on `EmiCraftingRecipe.input` field | new recipe with `EmiStack.of(stack)` |
| Top-left layout | pad to 4 with empty (didn't actually work) | custom `addWidgets` override |
| EMI display filtering | Mixin on `EmiApi.displayUses` / `mapRecipes` / `pruneSources` | none needed |
| Recipe sorting | manual reflection on `RecipeScreen.recipes` | none needed |
| Mixins | 1 (`EmiApiMixin`) | 0 |
| Client tick listeners | 1 (recipe manager scan) | 0 |
| Screen init listeners | 1 (recipe sort) | 0 |

The v1 approach was working against EMI; v2 works with it.

## Compatibility

- **Required:** EMI for Forge 1.20.1
- **Optional:** Sophisticated Storage — generates replacements only if loaded
- Backpack dye recipes are left alone (we don't have replacements for them,
  so removing them outright would leave you with nothing)

## Building

1. JDK 17
2. `./gradlew build`
3. Jar lands in `build/libs/`

## Installation

Drop the jar in `.minecraft/mods/` alongside Forge 1.20.1 and EMI.
