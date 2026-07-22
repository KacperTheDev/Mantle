package slimeknights.mantle.recipe.helper;

import net.minecraft.data.recipes.RecipeOutput;

/** Transitional builder result that can emit a real recipe to the native 1.21 sink. */
public interface EmittableRecipeOutput extends RecipeOutput {
  void emitTo(RecipeOutput output);
}
