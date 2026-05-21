# Sophisticated Storage EMI Fix

</br >\- Made with Claude (AI)

A client-side Forge mod for Minecraft 1.20.1 that cleans up Sophisticated Storage and Sophisticated Backpacks recipe display in EMI/JEMI.

## Fixes

### Remove Generic Placeholder Recipes
- Sophisticated Storage registers generic recipes that accept any variant as input (e.g., "any chest + copper = copper chest"). These are redundant because per-variant synthetic recipes already exist. This mod removes the originals so you only see the correct variant-specific recipes — an oak chest upgrade shows oak results, not birch or spruce.

- Covers all serializer types: tier upgrades, tier upgrade shapeless, double chest tier upgrades, storage dye, shulker box conversions, flat top barrel toggle, and barrel material recipes.

### Hide Dye Recipes From "Uses" View
- When looking up a base storage item (e.g., iron chest), dye recipes no longer flood the uses list. You'll only see tier upgrades and controller/IO recipes.

- Dye recipes **still show up** when looking up a specifically colored item as an output — so if you want to know how to craft a colored copper barrel, that recipe is still there.

## Features

### Default Recipes First
- Default Recipes are sorted to the top of the recipe list whenever you look up an item.

## Compatibility

- **Required:** EMI (Forge 1.20.1)
- **Optional:** Sophisticated Storage, Sophisticated Backpacks — fixes apply automatically when present, no crash if absent

The mod has no hard dependency on Sophisticated Storage or Backpacks. Recipes are identified by serializer registry name and ID pattern, so if those mods aren't installed the filters simply match nothing.

## Images

<img width="300" alt="image" src="https://github.com/user-attachments/assets/9bfc87f2-d9a0-435d-89bf-5c979a208dc3" />

<img width="300" alt="image" src="https://github.com/user-attachments/assets/a73f65fe-5159-48d6-81a1-9cc4c6f518d2" />

<img width="300" alt="image" src="https://github.com/user-attachments/assets/f32411ce-d121-49e7-ab51-65f78fac44a1" />

<img width="300" alt="image" src="https://github.com/user-attachments/assets/9d86780b-01eb-43ef-8f27-b3394c3e4ee6" />

