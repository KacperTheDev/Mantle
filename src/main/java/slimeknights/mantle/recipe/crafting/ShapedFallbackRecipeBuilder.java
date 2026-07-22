package slimeknights.mantle.recipe.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.common.conditions.ICondition;
import slimeknights.mantle.recipe.MantleRecipes;
import slimeknights.mantle.recipe.helper.FinishedRecipe;
import slimeknights.mantle.recipe.helper.FinishedRecipeOutput;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/** Builder for a shaped recipe with fallbacks */
@SuppressWarnings("unused")
@RequiredArgsConstructor(staticName = "fallback")
public class ShapedFallbackRecipeBuilder {
  private final ShapedRecipeBuilder base;
  private final List<ResourceLocation> alternatives = new ArrayList<>();

  /**
   * Adds a single alternative to this recipe. Any matching alternative causes this recipe to fail
   * @param location  Alternative
   * @return  Builder instance
   */
  public ShapedFallbackRecipeBuilder addAlternative(ResourceLocation location) {
    this.alternatives.add(location);
    return this;
  }

  /**
   * Adds a list of alternatives to this recipe. Any matching alternative causes this recipe to fail
   * @param locations  Alternative list
   * @return  Builder instance
   */
  public ShapedFallbackRecipeBuilder addAlternatives(Collection<ResourceLocation> locations) {
    this.alternatives.addAll(locations);
    return this;
  }

  /**
   * Builds the recipe using the output as the name
   * @param consumer  Recipe consumer
   */
  public void build(RecipeOutput output) {
    base.save(wrap(output));
  }

  /**
   * Builds the recipe using the given ID
   * @param consumer  Recipe consumer
   * @param id        Recipe ID
   */
  public void build(RecipeOutput output, ResourceLocation id) {
    base.save(wrap(output), id);
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
          throw new IllegalArgumentException("Fallback recipes require a shaped recipe, got " + recipe.getClass().getName());
        }
        output.accept(id, new ShapedFallbackRecipe(shaped, List.copyOf(alternatives)), advancement, conditions);
      }
    };
  }

  private record Result(FinishedRecipe base, List<ResourceLocation> alternatives) implements FinishedRecipe {
    @Override
    public void serializeRecipeData(JsonObject json) {
      base.serializeRecipeData(json);
      json.add("alternatives", alternatives.stream()
                                           .map(ResourceLocation::toString)
                                           .collect(JsonArray::new, JsonArray::add, JsonArray::addAll));
    }

    @Override
    public RecipeSerializer<?> getType() {
      return MantleRecipes.CRAFTING_SHAPED_FALLBACK.get();
    }

    @Override
    public ResourceLocation getId() {
      return base.getId();
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
