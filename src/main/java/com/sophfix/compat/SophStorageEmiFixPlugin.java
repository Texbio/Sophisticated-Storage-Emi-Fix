package com.sophfix.compat;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import java.util.Set;

/**
 * Removes original generic recipes by serializer type. Per-variant synthetics replace them.
 * Dye recipes are NOT removed here — they're handled by reflection in SophStorageEmiFix
 * which removes them from byInput only, keeping them in byOutput for colored item lookups.
 */
@EmiEntrypoint
public class SophStorageEmiFixPlugin implements EmiPlugin {

	private static final Set<String> REMOVED_SERIALIZERS = Set.of(
			"sophisticatedstorage:storage_tier_upgrade",
			"sophisticatedstorage:storage_tier_upgrade_shapeless",
			"sophisticatedstorage:double_chest_tier_upgrade",
			"sophisticatedstorage:double_chest_tier_upgrade_shapeless",
			"sophisticatedstorage:storage_dye",
			"sophisticatedstorage:shulker_box_from_chest",
			"sophisticatedstorage:shulker_box_from_vanilla_shapeless",
			"sophisticatedstorage:flat_top_barrel_toggle",
			"sophisticatedstorage:barrel_material",
			"sophisticatedstorage:generic_wood_storage",
			"sophisticatedbackpacks:backpack_dye"
	);

	@Override
	public void register(EmiRegistry registry) {
		registry.removeRecipes(recipe -> {
			Recipe<?> backing = recipe.getBackingRecipe();
			if (backing == null) {
				return false;
			}
			ResourceLocation serializerId = BuiltInRegistries.RECIPE_SERIALIZER.getKey(backing.getSerializer());
			return serializerId != null && REMOVED_SERIALIZERS.contains(serializerId.toString());
		});
	}
}
