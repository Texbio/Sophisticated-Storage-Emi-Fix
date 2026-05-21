package com.sophfix.compat;

import com.sophfix.SophStorageEmiFix;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ListEmiIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cleans up Sophisticated Storage's recipe and sidebar display in EMI without
 * reflection or Mixins, using only {@link EmiRegistry} API.
 *
 * <h3>What it does</h3>
 * <ol>
 *   <li><b>Sidebar:</b> hides bare-NBT wood-storage items (the bare
 *       {@code sophisticatedstorage:chest}/{@code barrel}/etc. entries that EMI
 *       auto-adds from the item registry). These render as a fallback acacia
 *       texture with no recipes mapped to them, so they're noise.</li>
 *   <li><b>Generic placeholder recipes:</b> removes the original tag-based
 *       (#minecraft:planks) and tier-upgrade recipes whose per-variant synthetics
 *       Sophisticated Storage's own EMI plugin already adds.</li>
 *   <li><b>Synthetic dye recipes:</b> removes the multi-wood-variant cycling
 *       versions and replaces them with single-display-variant ones rendered at
 *       the top-left of the grid (storage on the left, dye to the right).</li>
 *   <li><b>BaseTierWoodenStorage ingredient recipes (controller, storage_io,
 *       storage_input, storage_output):</b> replaces the displayed "B" slot
 *       (which otherwise cycles through ~56 stacks including every colored
 *       variant) with a clean 22-stack ingredient covering just the wood-typed
 *       chests and barrels. Server-side matching is unchanged.</li>
 * </ol>
 *
 * <h3>Why output stacks omit woodType</h3>
 * Sophisticated Storage's {@code addCreativeTabItems} adds colored sidebar
 * entries with {@code mainColor}/{@code accentColor} only — no {@code woodType}.
 * The chest/barrel subtype interpreter compares on
 * {@code [woodName, mainColor, accentColor, doubleChest|flatTop]}. For dye
 * recipe outputs to be findable from those sidebar entries, the output stack
 * must also omit {@code woodType}.
 */
@EmiEntrypoint
public class SophFixEmiPlugin implements EmiPlugin {

	private static final String SOPHSTORAGE = "sophisticatedstorage";

	/**
	 * FQCN of Sophisticated Storage's custom Ingredient. We detect recipes using
	 * it via class-name match so we don't need a compile-time dep on sophstorage.
	 */
	private static final String BASE_TIER_INGREDIENT_CLASS =
			"net.p3pp3rf1y.sophisticatedstorage.crafting.BaseTierWoodenStorageIngredient";

	/**
	 * Wood types in Sophisticated Storage's
	 * {@code WoodStorageBlockBase.CUSTOM_TEXTURE_WOOD_TYPES}. Used to build the
	 * multi-variant index ingredient for dye recipes and the clean chests+barrels
	 * ingredient for controller-family recipes.
	 */
	private static final List<String> WOOD_TYPES = List.of(
			"acacia", "bamboo", "birch", "cherry", "crimson",
			"dark_oak", "jungle", "mangrove", "oak", "spruce", "warped"
	);

	private static final Set<String> GENERIC_SERIALIZERS = Set.of(
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

	/**
	 * Sophisticated Storage item path → wood type for the displayed input stack
	 * in dye recipes. Null = no wood type (shulker boxes). Same set is used to
	 * decide which bare items to strip from the sidebar.
	 */
	private static final Map<String, String> STORAGE_DEFAULTS = buildStorageDefaults();

	private static Map<String, String> buildStorageDefaults() {
		Map<String, String> m = new LinkedHashMap<>();
		for (String n : new String[]{
				"chest", "copper_chest", "iron_chest", "gold_chest", "diamond_chest", "netherite_chest"
		}) m.put(n, "oak");
		for (String n : new String[]{
				"barrel", "copper_barrel", "iron_barrel", "gold_barrel", "diamond_barrel", "netherite_barrel"
		}) m.put(n, "spruce");
		for (int i = 1; i <= 4; i++) {
			for (String tier : new String[]{"", "copper_", "iron_", "gold_", "diamond_", "netherite_"}) {
				m.put("limited_" + tier + "barrel_" + i, "spruce");
			}
		}
		for (String n : new String[]{
				"shulker_box", "copper_shulker_box", "iron_shulker_box",
				"gold_shulker_box", "diamond_shulker_box", "netherite_shulker_box"
		}) m.put(n, null);
		return m;
	}

	@Override
	public void register(EmiRegistry registry) {
		removeBareWoodStorageFromSidebar(registry);
		removeGenericOriginals(registry);
		removeSophStorageSyntheticDyeRecipes(registry);
		removeBaseTierStorageRecipes(registry);

		if (ModList.get().isLoaded(SOPHSTORAGE)) {
			addReplacementDyeRecipes(registry);
			addReplacementBaseTierStorageRecipes(registry);
		}
	}

	// ── Sidebar cleanup ──────────────────────────────────────────────────────

	/**
	 * Removes bare-NBT sophstorage wood-storage items from EMI's sidebar. These
	 * are the {@code EmiStack.of(item)} entries EMI builds from the item
	 * registry; they have no NBT, render via the renderer's acacia fallback, and
	 * map to no recipes because all of sophstorage's intended outputs carry
	 * either {@code woodType} or color NBT.
	 *
	 * <p>Colored sidebar entries ({@code mainColor}/{@code accentColor}) and
	 * wood-typed entries ({@code woodType}) survive because they have NBT.
	 */
	private void removeBareWoodStorageFromSidebar(EmiRegistry registry) {
		registry.removeEmiStacks(stack -> {
			Item item = stack.getKeyOfType(Item.class);
			if (item == null) return false;
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
			if (id == null || !SOPHSTORAGE.equals(id.getNamespace())) return false;
			if (!STORAGE_DEFAULTS.containsKey(id.getPath())) return false;
			ItemStack is = stack.getItemStack();
			return !is.hasTag();
		});
	}

	// ── Recipe removals ─────────────────────────────────────────────────────

	private void removeGenericOriginals(EmiRegistry registry) {
		registry.removeRecipes(recipe -> {
			Recipe<?> backing = recipe.getBackingRecipe();
			if (backing == null) return false;
			ResourceLocation serializerId = BuiltInRegistries.RECIPE_SERIALIZER.getKey(backing.getSerializer());
			return serializerId != null && GENERIC_SERIALIZERS.contains(serializerId.toString());
		});
	}

	private void removeSophStorageSyntheticDyeRecipes(EmiRegistry registry) {
		registry.removeRecipes(recipe -> {
			ResourceLocation id = recipe.getId();
			if (id == null) return false;
			if (!SOPHSTORAGE.equals(id.getNamespace())) return false;
			if (!id.getPath().startsWith("/")) return false;
			return hasDyeInput(recipe);
		});
	}

	/**
	 * Removes wrapped recipes whose backing ShapedRecipe contains a
	 * BaseTierWoodenStorageIngredient. We replace them in {@link
	 * #addReplacementBaseTierStorageRecipes} with the same shape but a cleaner
	 * displayed storage ingredient.
	 */
	private void removeBaseTierStorageRecipes(EmiRegistry registry) {
		registry.removeRecipes(recipe -> {
			Recipe<?> backing = recipe.getBackingRecipe();
			if (!(backing instanceof ShapedRecipe shaped)) return false;
			return shapedHasBaseTierIngredient(shaped);
		});
	}

	private static boolean shapedHasBaseTierIngredient(ShapedRecipe shaped) {
		for (Ingredient ing : shaped.getIngredients()) {
			if (BASE_TIER_INGREDIENT_CLASS.equals(ing.getClass().getName())) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasDyeInput(EmiRecipe recipe) {
		for (EmiIngredient input : recipe.getInputs()) {
			for (EmiStack stack : input.getEmiStacks()) {
				if (!stack.isEmpty() && stack.getItemStack().getItem() instanceof DyeItem) {
					return true;
				}
			}
		}
		return false;
	}

	// ── Dye recipe replacements ──────────────────────────────────────────────

	private void addReplacementDyeRecipes(EmiRegistry registry) {
		STORAGE_DEFAULTS.forEach((itemName, displayWoodType) -> {
			ResourceLocation key = new ResourceLocation(SOPHSTORAGE, itemName);
			if (!BuiltInRegistries.ITEM.containsKey(key)) return;
			Item storageItem = BuiltInRegistries.ITEM.get(key);
			if (storageItem == Items.AIR) return;

			EmiIngredient indexInput = buildIndexStorageIngredient(storageItem, displayWoodType != null);
			EmiStack displayInput = EmiStack.of(buildStorageStack(storageItem, displayWoodType, null, null));

			for (DyeColor color : DyeColor.values()) {
				addSingleColorRecipe(registry, storageItem, itemName, indexInput, displayInput, color);
			}
			addMultiColorRecipe(registry, storageItem, itemName, indexInput, displayInput);
		});
	}

	/**
	 * Index ingredient for byInput coverage:
	 * <ul>
	 *   <li>For wood storage items: every wood variant plus a bare stack (the
	 *       bare entry is a safety net — even though we strip bare entries from
	 *       the sidebar via {@link #removeBareWoodStorageFromSidebar}, the bare
	 *       stack can still appear in other contexts).</li>
	 *   <li>For shulker boxes: just the bare stack — they have no wood type.</li>
	 * </ul>
	 *
	 * <p>Uses {@link ListEmiIngredient} directly rather than
	 * {@code EmiIngredient.of(Ingredient.of(stacks))}, because that path goes
	 * through {@link dev.emi.emi.registry.EmiTags#getIngredient} which compares
	 * stacks via their default comparison to decide whether to collapse the
	 * list. Before Sophisticated Storage's {@code setDefaultComparison} has
	 * registered the chest/barrel subtype interpreter, the default comparison
	 * is "always equal" and the multi-stack list would collapse to a single
	 * EmiStack, breaking byInput coverage. Plugin order isn't deterministic, so
	 * we sidestep the conversion entirely.
	 */
	private static EmiIngredient buildIndexStorageIngredient(Item storageItem, boolean hasWoodVariants) {
		if (!hasWoodVariants) {
			return EmiStack.of(new ItemStack(storageItem));
		}
		List<EmiStack> stacks = new ArrayList<>(WOOD_TYPES.size() + 1);
		for (String wood : WOOD_TYPES) {
			stacks.add(EmiStack.of(buildStorageStack(storageItem, wood, null, null)));
		}
		stacks.add(EmiStack.of(new ItemStack(storageItem)));
		return new ListEmiIngredient(stacks, 1);
	}

	private void addSingleColorRecipe(EmiRegistry registry, Item storageItem, String itemName,
									  EmiIngredient indexStorage, EmiStack displayStorage,
									  DyeColor color) {
		ItemStack outputStack = buildStorageStack(storageItem, null, color, color);
		EmiIngredient dye = EmiIngredient.of(Ingredient.of(color.getTag()));

		List<EmiIngredient> indexInputs = List.of(indexStorage, dye);
		List<EmiIngredient> displayInputs = List.of(displayStorage, dye);

		ResourceLocation id = new ResourceLocation(SophStorageEmiFix.MOD_ID,
				"/dye/" + itemName + "/" + color.getSerializedName());

		// 2 wide × 1 tall: storage on the left, dye to the right.
		registry.addRecipe(new SophDyeEmiRecipe(
				indexInputs, displayInputs, EmiStack.of(outputStack), id, 2, 1));
	}

	private void addMultiColorRecipe(EmiRegistry registry, Item storageItem, String itemName,
									 EmiIngredient indexStorage, EmiStack displayStorage) {
		ItemStack outputStack = buildStorageStack(storageItem, null, DyeColor.YELLOW, DyeColor.LIME);
		EmiIngredient yellow = EmiIngredient.of(Ingredient.of(DyeColor.YELLOW.getTag()));
		EmiIngredient lime = EmiIngredient.of(Ingredient.of(DyeColor.LIME.getTag()));

		List<EmiIngredient> indexInputs = List.of(yellow, indexStorage, lime);
		List<EmiIngredient> displayInputs = List.of(yellow, displayStorage, lime);

		ResourceLocation id = new ResourceLocation(SophStorageEmiFix.MOD_ID,
				"/dye/" + itemName + "/multi_yellow_lime");

		// 3 wide × 1 tall: yellow, storage, lime.
		registry.addRecipe(new SophDyeEmiRecipe(
				indexInputs, displayInputs, EmiStack.of(outputStack), id, 3, 1));
	}

	// ── Controller-family replacements ──────────────────────────────────────

	/**
	 * Re-add controller-family recipes with the BaseTierWoodenStorageIngredient
	 * slot replaced by our clean 22-stack ingredient. Uses
	 * {@link EmiRegistry#addDeferredRecipes} so we can iterate the vanilla recipe
	 * manager after all plugins have registered.
	 */
	private void addReplacementBaseTierStorageRecipes(EmiRegistry registry) {
		registry.addDeferredRecipes(consumer -> {
			ClientLevel level = Minecraft.getInstance().level;
			if (level == null) return;
			RegistryAccess registryAccess = level.registryAccess();
			EmiIngredient cleanStorage = buildChestsAndBarrelsIngredient();
			if (cleanStorage.isEmpty()) return;

			for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
				if (!(recipe instanceof ShapedRecipe shaped)) continue;
				if (!shapedHasBaseTierIngredient(shaped)) continue;

				NonNullList<Ingredient> ingredients = shaped.getIngredients();
				List<EmiIngredient> newInputs = new ArrayList<>(ingredients.size());
				for (Ingredient ing : ingredients) {
					if (BASE_TIER_INGREDIENT_CLASS.equals(ing.getClass().getName())) {
						newInputs.add(cleanStorage);
					} else {
						newInputs.add(EmiIngredient.of(ing));
					}
				}

				ItemStack result = shaped.getResultItem(registryAccess);
				ResourceLocation origId = recipe.getId();
				ResourceLocation newId = new ResourceLocation(SophStorageEmiFix.MOD_ID,
						"/replaced/" + origId.getNamespace() + "_" + origId.getPath());

				consumer.accept(new EmiCraftingRecipe(newInputs, EmiStack.of(result), newId, false));
			}
		});
	}

	/**
	 * Build the clean "chests and barrels" ingredient: 11 wood-typed chests +
	 * 11 wood-typed barrels, displayed as a cycling list. No colored variants,
	 * no bare items.
	 */
	private static EmiIngredient buildChestsAndBarrelsIngredient() {
		List<EmiStack> stacks = new ArrayList<>();
		appendWoodVariantStacks(stacks, "chest");
		appendWoodVariantStacks(stacks, "barrel");
		if (stacks.isEmpty()) return EmiStack.EMPTY;
		return new ListEmiIngredient(stacks, 1);
	}

	private static void appendWoodVariantStacks(List<EmiStack> out, String itemPath) {
		ResourceLocation id = new ResourceLocation(SOPHSTORAGE, itemPath);
		if (!BuiltInRegistries.ITEM.containsKey(id)) return;
		Item item = BuiltInRegistries.ITEM.get(id);
		if (item == Items.AIR) return;
		for (String wood : WOOD_TYPES) {
			out.add(EmiStack.of(buildStorageStack(item, wood, null, null)));
		}
	}

	// ── Stack construction (vanilla NBT only) ────────────────────────────────

	/**
	 * Build a storage stack with optional wood type and dye colors written as
	 * top-level NBT tags. Color encoding matches Sophisticated Storage's
	 * {@code ColorHelper.getColor}: each channel scaled 0-255 and packed as
	 * {@code r<<16 | g<<8 | b}.
	 */
	private static ItemStack buildStorageStack(Item item, String woodType,
												DyeColor mainColor, DyeColor accentColor) {
		ItemStack stack = new ItemStack(item);
		CompoundTag tag = stack.getOrCreateTag();
		if (woodType != null) {
			tag.putString("woodType", woodType);
		}
		if (mainColor != null) {
			tag.putInt("mainColor", colorToInt(mainColor));
		}
		if (accentColor != null) {
			tag.putInt("accentColor", colorToInt(accentColor));
		}
		// If nothing was written, drop the empty CompoundTag so this stack hashes
		// equal to a freshly-constructed bare ItemStack (which has no tag at all).
		if (tag.isEmpty()) {
			stack.setTag(null);
		}
		return stack;
	}

	private static int colorToInt(DyeColor color) {
		float[] rgb = color.getTextureDiffuseColors();
		int r = (int) (rgb[0] * 255);
		int g = (int) (rgb[1] * 255);
		int b = (int) (rgb[2] * 255);
		return (r << 16) | (g << 8) | b;
	}
}
