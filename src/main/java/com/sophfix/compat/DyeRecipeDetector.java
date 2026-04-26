package com.sophfix.compat;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

import java.util.Set;

/**
 * Detects Sophisticated Storage / Backpacks dye recipes by checking
 * if the recipe has a dye item in its inputs and belongs to a storage namespace.
 */
public final class DyeRecipeDetector {

	private static final Set<String> STORAGE_NAMESPACES = Set.of(
			"sophisticatedstorage",
			"sophisticatedbackpacks"
	);

	private DyeRecipeDetector() {
	}

	/**
	 * A recipe is a storage dye recipe if:
	 * 1. Its ID is in a storage namespace
	 * 2. At least one input ingredient contains a DyeItem
	 */
	public static boolean isDyeRecipe(EmiRecipe recipe) {
		ResourceLocation id = recipe.getId();
		if (id == null || !STORAGE_NAMESPACES.contains(id.getNamespace())) {
			return false;
		}
		return recipe.getInputs().stream()
				.flatMap(ingredient -> ingredient.getEmiStacks().stream())
				.anyMatch(stack -> !stack.isEmpty() && stack.getItemStack().getItem() instanceof DyeItem);
	}

	/**
	 * Returns true if the item stack has color NBT data (mainColor or accentColor).
	 */
	public static boolean hasColorData(EmiStack stack) {
		ItemStack itemStack = stack.getItemStack();
		if (itemStack.isEmpty() || !itemStack.hasTag()) {
			return false;
		}
		CompoundTag tag = itemStack.getTag();
		if (tag.contains("mainColor") || tag.contains("accentColor")) {
			return true;
		}
		if (tag.contains("BlockEntityTag", 10)) {
			CompoundTag beTag = tag.getCompound("BlockEntityTag");
			if (beTag.contains("storageWrapper", 10)) {
				CompoundTag wrapper = beTag.getCompound("storageWrapper");
				return wrapper.contains("mainColor") || wrapper.contains("accentColor");
			}
		}
		return false;
	}
}
