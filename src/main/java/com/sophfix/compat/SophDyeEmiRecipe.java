package com.sophfix.compat;

import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * An EmiCraftingRecipe that:
 * <ul>
 *   <li>Indexes itself under one set of ingredients (the parent's {@code input} field —
 *       used by EMI's bake to build the byInput map), but</li>
 *   <li>Draws a possibly-different set of ingredients into the slot widgets, in
 *       a fixed (width × height) shape anchored at the top-left of the 3×3 grid.</li>
 * </ul>
 *
 * <h3>Why the split</h3>
 * The whole point of this fix is to stop the storage slot from cycling through every
 * wood variant in the display. But if we also strip the wood variants out of the
 * recipe's underlying inputs, EMI's byInput map only indexes the recipe under one
 * variant — so a player clicking, say, a birch chest in the sidebar can't find the
 * dye recipe at all.
 *
 * <p>Splitting the two lets us keep broad indexing (all 11 wood variants in the
 * underlying {@code input}) while displaying just one specific variant (passed as
 * {@code displayInputs}).
 *
 * <h3>Why the custom addWidgets</h3>
 * The parent's {@code addWidgets} computes a slot offset from {@code canFit(1, 3)} /
 * {@code canFit(3, 1)} and centers the recipe in the 3×3 grid. For a 1×2 dye
 * recipe that pushes storage and dye into the middle row. We bypass that by walking
 * the grid ourselves and mapping each cell to a (row × width + col) index in the
 * display list, so the recipe always anchors at the top-left.
 */
public class SophDyeEmiRecipe extends EmiCraftingRecipe {
	private final List<EmiIngredient> displayInputs;
	private final int recipeWidth;
	private final int recipeHeight;

	/**
	 * @param indexInputs   ingredients used for the recipe's logical inputs — these
	 *                      drive EMI's byInput map, recipe-tree resolution, and
	 *                      inventory auto-fill. Use the broadest matching ingredient
	 *                      here (e.g. all wood variants).
	 * @param displayInputs ingredients actually drawn into the grid. Length should be
	 *                      {@code recipeWidth * recipeHeight} with row-major order.
	 *                      For storage slots, pass a single-stack {@link EmiStack} so
	 *                      the display doesn't cycle.
	 */
	public SophDyeEmiRecipe(List<EmiIngredient> indexInputs, List<EmiIngredient> displayInputs,
							EmiStack output, ResourceLocation id,
							int recipeWidth, int recipeHeight) {
		super(indexInputs, output, id, false);
		this.displayInputs = displayInputs;
		this.recipeWidth = recipeWidth;
		this.recipeHeight = recipeHeight;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		widgets.addTexture(EmiTexture.EMPTY_ARROW, 60, 18);

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				EmiIngredient cell = EmiStack.EMPTY;
				if (row < recipeHeight && col < recipeWidth) {
					int shapeIndex = row * recipeWidth + col;
					if (shapeIndex < displayInputs.size()) {
						cell = displayInputs.get(shapeIndex);
					}
				}
				widgets.addSlot(cell, col * 18, row * 18);
			}
		}

		widgets.addSlot(output, 92, 14).large(true).recipeContext(this);
	}
}
