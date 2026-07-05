package slimeknights.mantle.recipe.helper;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Compatibility interface for Mantle's recipe builder helpers.
 * Vanilla removed FinishedRecipe in 1.21 in favor of RecipeOutput.
 */
public interface FinishedRecipe {
  /** Serializes the full recipe JSON. */
  default JsonObject serializeRecipe() {
    JsonObject json = new JsonObject();
    json.addProperty("type", Objects.requireNonNull(BuiltInRegistries.RECIPE_SERIALIZER.getKey(getType())).toString());
    serializeRecipeData(json);
    return json;
  }

  /** Serializes recipe data, excluding the type. */
  void serializeRecipeData(JsonObject json);

  /** Gets the recipe ID. */
  ResourceLocation getId();

  /** Gets the recipe serializer. */
  RecipeSerializer<?> getType();

  /** Serializes advancement data, if present. */
  @Nullable
  JsonObject serializeAdvancement();

  /** Gets the advancement ID, if present. */
  @Nullable
  ResourceLocation getAdvancementId();
}
