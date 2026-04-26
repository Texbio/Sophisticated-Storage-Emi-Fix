package com.sophfix;

import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.registry.EmiRecipes;
import dev.emi.emi.screen.RecipeScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mod(SophStorageEmiFix.MOD_ID)
public class SophStorageEmiFix {
	public static final String MOD_ID = "sophstorageemifix";

	private static final Set<String> STORAGE_NAMESPACES = Set.of(
			"sophisticatedstorage", "sophisticatedbackpacks"
	);

	private Object lastManager = null;

	public SophStorageEmiFix() {
		MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
		MinecraftForge.EVENT_BUS.addListener(this::onScreenInit);
	}

	// ── Manager change: clean dye recipes + simplify inputs ──

	private void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		Object currentManager = EmiRecipes.manager;
		if (currentManager == lastManager) return;
		lastManager = currentManager;
		try {
			cleanRecipeManager(currentManager);
		} catch (Exception ignored) {
		}
	}

	// ── Recipe screen open: sort default recipes first ──

	@SuppressWarnings("unchecked")
	private void onScreenInit(ScreenEvent.Init.Pre event) {
		if (!(event.getScreen() instanceof RecipeScreen screen)) return;

		try {
			Field recipesField = RecipeScreen.class.getDeclaredField("recipes");
			recipesField.setAccessible(true);
			Map<Object, List<EmiRecipe>> recipes = (Map<Object, List<EmiRecipe>>) recipesField.get(screen);
			if (recipes == null) return;

			for (Map.Entry<Object, List<EmiRecipe>> entry : recipes.entrySet()) {
				List<EmiRecipe> list = entry.getValue();
				if (list.size() > 1) {
					List<EmiRecipe> sorted = new ArrayList<>(list);
					sorted.sort((a, b) -> {
						boolean aDefault = isDefaultRecipe(a);
						boolean bDefault = isDefaultRecipe(b);
						if (aDefault == bDefault) return 0;
						return aDefault ? -1 : 1;
					});
					entry.setValue(sorted);
				}
			}
		} catch (Exception ignored) {
		}
	}

	/**
	 * Check if this recipe is the user's default recipe for any of its outputs.
	 */
	private boolean isDefaultRecipe(EmiRecipe recipe) {
		for (EmiStack output : recipe.getOutputs()) {
			EmiRecipe defaultRecipe = BoM.getRecipe(output);
			if (defaultRecipe != null && defaultRecipe.equals(recipe)) {
				return true;
			}
		}
		return false;
	}

	// ── Dye recipe cleanup ──

	@SuppressWarnings("unchecked")
	private void cleanRecipeManager(Object manager) throws Exception {
		Field byInputField = manager.getClass().getDeclaredField("byInput");
		byInputField.setAccessible(true);
		Map<Object, List<EmiRecipe>> byInput = (Map<Object, List<EmiRecipe>>) byInputField.get(manager);

		for (Map.Entry<Object, List<EmiRecipe>> entry : byInput.entrySet()) {
			List<EmiRecipe> recipes = entry.getValue();
			List<EmiRecipe> filtered = recipes.stream().filter(r -> !isDyeRecipe(r)).toList();
			if (filtered.size() < recipes.size()) {
				entry.setValue(filtered);
			}
		}

		Field byOutputField = manager.getClass().getDeclaredField("byOutput");
		byOutputField.setAccessible(true);
		Map<Object, List<EmiRecipe>> byOutput = (Map<Object, List<EmiRecipe>>) byOutputField.get(manager);

		for (List<EmiRecipe> recipes : byOutput.values()) {
			for (EmiRecipe recipe : recipes) {
				if (isDyeRecipe(recipe) && recipe instanceof EmiCraftingRecipe craftingRecipe) {
					simplifyDyeRecipeInputs(craftingRecipe);
				}
			}
		}
	}

	private void simplifyDyeRecipeInputs(EmiCraftingRecipe recipe) {
		try {
			Field inputField = EmiCraftingRecipe.class.getDeclaredField("input");
			inputField.setAccessible(true);

			List<EmiIngredient> currentInputs = recipe.getInputs();
			List<EmiIngredient> newInputs = new ArrayList<>();
			boolean changed = false;

			for (EmiIngredient ingredient : currentInputs) {
				if (isStorageIngredient(ingredient)) {
					EmiStack defaultStack = createDefaultStack(ingredient);
					if (defaultStack != null) {
						newInputs.add(defaultStack);
						changed = true;
					} else {
						newInputs.add(ingredient);
					}
				} else {
					newInputs.add(ingredient);
				}
			}

			if (changed) {
				inputField.set(recipe, newInputs);
			}
		} catch (Exception ignored) {
		}
	}

	// ── Helpers ──

	private boolean isStorageIngredient(EmiIngredient ingredient) {
		return ingredient.getEmiStacks().stream().anyMatch(stack -> {
			if (stack.isEmpty()) return false;
			if (stack.getItemStack().getItem() instanceof DyeItem) return false;
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItemStack().getItem());
			return STORAGE_NAMESPACES.contains(id.getNamespace());
		});
	}

	private EmiStack createDefaultStack(EmiIngredient ingredient) {
		EmiStack first = ingredient.getEmiStacks().stream()
				.filter(s -> !s.isEmpty()).findFirst().orElse(null);
		if (first == null) return null;

		ItemStack itemStack = first.getItemStack().copy();
		String path = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).getPath();

		if (path.contains("shulker")) {
			itemStack.getOrCreateTag().remove("woodType");
		} else if (path.contains("barrel") || path.contains("limited")) {
			itemStack.getOrCreateTag().putString("woodType", "spruce");
		} else if (path.contains("chest")) {
			itemStack.getOrCreateTag().putString("woodType", "oak");
		}

		return EmiStack.of(itemStack);
	}

	private static boolean isDyeRecipe(EmiRecipe recipe) {
		ResourceLocation id = recipe.getId();
		if (id == null || !STORAGE_NAMESPACES.contains(id.getNamespace())) return false;
		return recipe.getInputs().stream()
				.flatMap(ingredient -> ingredient.getEmiStacks().stream())
				.anyMatch(stack -> !stack.isEmpty() && stack.getItemStack().getItem() instanceof DyeItem);
	}
}
