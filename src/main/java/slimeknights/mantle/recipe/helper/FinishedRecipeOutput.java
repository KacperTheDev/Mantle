package slimeknights.mantle.recipe.helper;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.Advancement.Builder;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.common.conditions.ICondition;

import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.Function;

/** Bridges vanilla 1.21 {@link RecipeOutput} to Mantle's legacy {@link FinishedRecipe} helpers. */
public record FinishedRecipeOutput(Consumer<FinishedRecipe> consumer, Function<FinishedRecipe, FinishedRecipe> wrapper) implements RecipeOutput {
  @Override
  public void accept(ResourceLocation id, Recipe<?> recipe, @Nullable AdvancementHolder advancement) {
    consumer.accept(wrapper.apply(new Result(id, recipe, advancement)));
  }

  @Override
  public void accept(ResourceLocation id, Recipe<?> recipe, @Nullable AdvancementHolder advancement, ICondition... conditions) {
    accept(id, recipe, advancement);
  }

  @Override
  public Builder advancement() {
    return Advancement.Builder.advancement();
  }

  private record Result(ResourceLocation id, Recipe<?> recipe, @Nullable AdvancementHolder advancement) implements FinishedRecipe {
    @Override
    public void serializeRecipeData(JsonObject json) {
      JsonObject full = Recipe.CODEC.encodeStart(JsonOps.INSTANCE, recipe).getOrThrow().getAsJsonObject();
      full.remove("type");
      for (String key : full.keySet()) {
        json.add(key, full.get(key));
      }
    }

    @Override
    public ResourceLocation getId() {
      return id;
    }

    @Override
    public RecipeSerializer<?> getType() {
      return recipe.getSerializer();
    }

    @Nullable
    @Override
    public JsonObject serializeAdvancement() {
      return advancement == null ? null : Advancement.CODEC.encodeStart(JsonOps.INSTANCE, advancement.value()).getOrThrow().getAsJsonObject();
    }

    @Nullable
    @Override
    public ResourceLocation getAdvancementId() {
      return advancement == null ? null : advancement.id();
    }
  }
}
