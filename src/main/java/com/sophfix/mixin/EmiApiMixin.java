package com.sophfix.mixin;

import com.sophfix.compat.DyeRecipeDetector;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiFavorites;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Mixin(value = EmiApi.class, remap = false)
public class EmiApiMixin {

	@Unique
	private static final Logger SOPHFIX_LOG = LoggerFactory.getLogger("SophStorageEmiFix");

	/**
	 * Log when displayUses is called.
	 */
	@Inject(method = "displayUses", at = @At("HEAD"))
	private static void sophfix$logDisplayUses(EmiIngredient stack, CallbackInfo ci) {
		SOPHFIX_LOG.info("[SOPHFIX] displayUses called for: {}", stack.getEmiStacks().stream()
				.map(s -> s.getId() + " nbt=" + s.getNbt())
				.collect(Collectors.joining(", ")));
	}

	/**
	 * Intercept the recipe list going into mapRecipes when called from displayUses.
	 * Filters out dye recipes so they don't appear in the "uses" view.
	 */
	@ModifyArg(
			method = "displayUses",
			at = @At(value = "INVOKE", target = "mapRecipes(Ljava/util/List;)Ljava/util/Map;"),
			index = 0
	)
	private static List<EmiRecipe> sophfix$filterDyeFromUses(List<EmiRecipe> list) {
		SOPHFIX_LOG.info("[SOPHFIX] ModifyArg fired in displayUses. Recipe count before filter: {}", list.size());
		for (EmiRecipe recipe : list) {
			boolean isDye = DyeRecipeDetector.isDyeRecipe(recipe);
			boolean hasDyeInput = recipe.getInputs().stream()
					.flatMap(i -> i.getEmiStacks().stream())
					.anyMatch(s -> !s.isEmpty() && s.getItemStack().getItem() instanceof DyeItem);
			SOPHFIX_LOG.info("[SOPHFIX]   recipe id={} category={} class={} isDye={} hasDyeInput={} inputCount={}",
					recipe.getId(),
					recipe.getCategory().getId(),
					recipe.getClass().getSimpleName(),
					isDye,
					hasDyeInput,
					recipe.getInputs().size());
			if (hasDyeInput || isDye) {
				for (EmiIngredient input : recipe.getInputs()) {
					SOPHFIX_LOG.info("[SOPHFIX]     input: {}", input.getEmiStacks().stream()
							.map(s -> s.getId().toString())
							.collect(Collectors.joining(", ")));
				}
			}
		}

		List<EmiRecipe> filtered = list.stream()
				.filter(recipe -> !DyeRecipeDetector.isDyeRecipe(recipe))
				.toList();
		SOPHFIX_LOG.info("[SOPHFIX] Recipe count after filter: {} (removed {})", filtered.size(), list.size() - filtered.size());
		return filtered;
	}

	/**
	 * Also log what mapRecipes receives and returns, to catch any other entry points.
	 */
	@Inject(method = "mapRecipes", at = @At("RETURN"))
	private static void sophfix$logAndSortFavorites(List<EmiRecipe> list,
													CallbackInfoReturnable<Map<EmiRecipeCategory, List<EmiRecipe>>> cir) {
		Map<EmiRecipeCategory, List<EmiRecipe>> map = cir.getReturnValue();
		int total = map.values().stream().mapToInt(List::size).sum();
		SOPHFIX_LOG.info("[SOPHFIX] mapRecipes returned {} recipes in {} categories", total, map.size());
		for (Map.Entry<EmiRecipeCategory, List<EmiRecipe>> entry : map.entrySet()) {
			for (EmiRecipe recipe : entry.getValue()) {
				boolean hasDyeInput = recipe.getInputs().stream()
						.flatMap(i -> i.getEmiStacks().stream())
						.anyMatch(s -> !s.isEmpty() && s.getItemStack().getItem() instanceof DyeItem);
				if (hasDyeInput) {
					SOPHFIX_LOG.warn("[SOPHFIX] *** DYE RECIPE SURVIVED: id={} category={} class={}",
							recipe.getId(), recipe.getCategory().getId(), recipe.getClass().getSimpleName());
				}
			}
		}

		// Sort favorites first
		Set<ResourceLocation> favoriteRecipeIds = EmiFavorites.favorites.stream()
				.map(EmiFavorite::getRecipe)
				.filter(Objects::nonNull)
				.map(EmiRecipe::getId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());

		if (!favoriteRecipeIds.isEmpty()) {
			map.values().forEach(recipes ->
					recipes.sort((a, b) -> {
						boolean aFav = a.getId() != null && favoriteRecipeIds.contains(a.getId());
						boolean bFav = b.getId() != null && favoriteRecipeIds.contains(b.getId());
						if (aFav == bFav) return 0;
						return aFav ? -1 : 1;
					})
			);
		}
	}

	/**
	 * Filter dye recipes from the "recipes" view when the item has no color data.
	 */
	@Inject(method = "pruneSources", at = @At("RETURN"), cancellable = true)
	private static void sophfix$hideDyeFromSources(List<EmiRecipe> list, EmiStack context,
													CallbackInfoReturnable<List<EmiRecipe>> cir) {
		if (DyeRecipeDetector.hasColorData(context)) {
			return;
		}
		List<EmiRecipe> original = cir.getReturnValue();
		List<EmiRecipe> filtered = original.stream()
				.filter(recipe -> !DyeRecipeDetector.isDyeRecipe(recipe))
				.toList();
		if (filtered.size() != original.size()) {
			cir.setReturnValue(filtered);
		}
	}
}
