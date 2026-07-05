package slimeknights.mantle.recipe.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import slimeknights.mantle.recipe.MantleRecipes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@SuppressWarnings("WeakerAccess")
public class ShapedFallbackRecipe extends ShapedRecipe {

  /** Recipes to skip if they match */
  private final List<ResourceLocation> alternatives;
  private List<CraftingRecipe> alternativeCache;

  /**
   * Main constructor, creates a recipe from all parameters
   * @param id             Recipe ID
   * @param group          Recipe group
   * @param width          Recipe width
   * @param height         Recipe height
   * @param ingredients    Recipe input ingredients
   * @param output         Recipe output
   * @param alternatives   List of recipe names to fail this match if they match
   */
  public ShapedFallbackRecipe(ResourceLocation id, String group, CraftingBookCategory category, int width, int height, NonNullList<Ingredient> ingredients, ItemStack output, List<ResourceLocation> alternatives) {
    super(group, category, new ShapedRecipePattern(width, height, ingredients, Optional.empty()), output);
    this.alternatives = alternatives;
  }

  /**
   * Creates a recipe using a shaped recipe as a base
   * @param base          Shaped recipe to copy data from
   * @param alternatives  List of recipe names to fail this match if they match
   */
  public ShapedFallbackRecipe(ShapedRecipe base, List<ResourceLocation> alternatives) {
    super(base.getGroup(), base.category(), base.pattern, base.getResultItem((HolderLookup.Provider)null), base.showNotification());
    this.alternatives = alternatives;
  }

  @Override
  public boolean matches(CraftingInput inv, Level world) {
    // if this recipe does not match, fail it
    if (!super.matches(inv, world)) {
      return false;
    }

    // fetch all alternatives, fail if any match
    // cache to save effort down the line
    if (alternativeCache == null) {
      RecipeManager manager = world.getRecipeManager();
      alternativeCache = alternatives.stream()
                                     .map(manager::byKey)
                                     .filter(Optional::isPresent)
                                     .map(Optional::get)
                                     .map(RecipeHolder::value)
                                     .filter(recipe -> {
                                       // only allow exact shaped or shapeless match, prevent infinite recursion due to complex recipes
                                       Class<?> clazz = recipe.getClass();
                                       return clazz == ShapedRecipe.class || clazz == ShapelessRecipe.class;
                                     })
                                     .map(recipe -> (CraftingRecipe) recipe).collect(Collectors.toList());
    }
    // fail if any alterntaive matches
    return this.alternativeCache.stream().noneMatch(recipe -> recipe.matches(inv, world));
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return MantleRecipes.CRAFTING_SHAPED_FALLBACK.get();
  }

  public static class Serializer implements RecipeSerializer<ShapedFallbackRecipe> {
    private static final MapCodec<ShapedFallbackRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
      ShapedRecipe.Serializer.CODEC.forGetter(recipe -> recipe),
      ResourceLocation.CODEC.listOf().fieldOf("alternatives").forGetter(recipe -> recipe.alternatives)
    ).apply(instance, ShapedFallbackRecipe::new));
    private static final StreamCodec<RegistryFriendlyByteBuf,ShapedFallbackRecipe> STREAM_CODEC = StreamCodec.of(
      (buffer, recipe) -> {
        ShapedRecipe.Serializer.STREAM_CODEC.encode(buffer, recipe);
        buffer.writeVarInt(recipe.alternatives.size());
        for (ResourceLocation alternative : recipe.alternatives) {
          buffer.writeResourceLocation(alternative);
        }
      },
      buffer -> {
        ShapedRecipe base = ShapedRecipe.Serializer.STREAM_CODEC.decode(buffer);
        int size = buffer.readVarInt();
        List<ResourceLocation> alternatives = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
          alternatives.add(buffer.readResourceLocation());
        }
        return new ShapedFallbackRecipe(base, List.copyOf(alternatives));
      });

    @Override
    public MapCodec<ShapedFallbackRecipe> codec() {
      return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf,ShapedFallbackRecipe> streamCodec() {
      return STREAM_CODEC;
    }
  }
}
