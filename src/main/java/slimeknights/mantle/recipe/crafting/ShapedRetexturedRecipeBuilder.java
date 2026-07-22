package slimeknights.mantle.recipe.crafting;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import lombok.RequiredArgsConstructor;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.common.conditions.ICondition;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.recipe.MantleRecipes;
import slimeknights.mantle.recipe.helper.FinishedRecipe;
import slimeknights.mantle.recipe.helper.FinishedRecipeOutput;

import javax.annotation.Nullable;
import java.util.function.Consumer;

@SuppressWarnings("unused")
@RequiredArgsConstructor(staticName = "fromShaped")
public class ShapedRetexturedRecipeBuilder {
  private final ShapedRecipeBuilder parent;
  private Ingredient texture = null;
  private char textureKey = '\0';
  private boolean matchAll = false;

  /**
   * Sets the texture source to the given ingredient
   * @param texture Ingredient to use for texture
   * @return Builder instance
   */
  public ShapedRetexturedRecipeBuilder setSource(Ingredient texture) {
    this.texture = texture;
    this.textureKey = '\0';
    return this;
  }

  /**
   * Sets the texture source to the given tag
   * @param tag Tag to use for texture
   * @return Builder instance
   */
  public ShapedRetexturedRecipeBuilder setSource(TagKey<Item> tag) {
    return setSource(Ingredient.of(tag));
  }

  /** Sets the texture source to a key from the texture map. Is not validated as that is too much work. */
  public ShapedRetexturedRecipeBuilder setSource(char textureKey) {
    this.textureKey = textureKey;
    this.texture = null;
    return this;
  }

  /**
   * Sets the match first property on the recipe.
   * If set, the recipe uses the first ingredient match for the texture. If unset, all items that match the ingredient must be the same or no texture is applied
   * @return Builder instance
   */
  public ShapedRetexturedRecipeBuilder setMatchAll() {
    this.matchAll = true;
    return this;
  }

  /**
   * Builds the recipe with the default name using the given consumer
   * @param consumer Recipe consumer
   */
  public void build(RecipeOutput consumer) {
    this.validate();
    parent.save(wrap(consumer));
  }

  /**
   * Builds the recipe using the given consumer
   * @param consumer Recipe consumer
   * @param location Recipe location
   */
  public void build(RecipeOutput consumer, ResourceLocation location) {
    this.validate();
    parent.save(wrap(consumer), location);
  }

  private RecipeOutput wrap(RecipeOutput output) {
    return new RecipeOutput() {
      @Override
      public Advancement.Builder advancement() {
        return output.advancement();
      }

      @Override
      public void accept(ResourceLocation id, Recipe<?> recipe, @Nullable AdvancementHolder advancement, ICondition... conditions) {
        if (!(recipe instanceof ShapedRecipe shaped)) {
          throw new IllegalArgumentException("Retextured recipes require a shaped recipe, got " + recipe.getClass().getName());
        }
        output.accept(id, new ShapedRetexturedRecipe(shaped, texture, matchAll), advancement, conditions);
      }
    };
  }

  /**
   * Ensures this recipe can be built
   * @throws IllegalStateException If the recipe cannot be built
   */
  private void validate() {
    if (texture == null && textureKey == '\0') {
      throw new IllegalStateException("No texture defined for texture recipe");
    }
  }

  private class Result implements FinishedRecipe {
    private final FinishedRecipe base;

    private Result(FinishedRecipe base) {
      this.base = base;
    }

    @Override
    public RecipeSerializer<?> getType() {
      return MantleRecipes.CRAFTING_SHAPED_RETEXTURED.get();
    }

    @Override
    public ResourceLocation getId() {
      return base.getId();
    }

    @Override
    public void serializeRecipeData(JsonObject json) {
      base.serializeRecipeData(json);
      if (textureKey != '\0') {
        json.addProperty("texture", textureKey);
      } else if (texture != null) {
        json.add("texture", Ingredient.CODEC.encodeStart(JsonOps.INSTANCE, texture).getOrThrow());
        Mantle.logger.warn("Using deprecated ingredient format on texture for shaped retextured recipe {}. Use key instead.", getId());
      }
      json.addProperty("match_all", matchAll);
    }

    @Nullable
    @Override
    public JsonObject serializeAdvancement() {
      return base.serializeAdvancement();
    }

    @Nullable
    @Override
    public ResourceLocation getAdvancementId() {
      return base.getAdvancementId();
    }
  }
}
