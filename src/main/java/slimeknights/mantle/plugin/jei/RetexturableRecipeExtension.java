package slimeknights.mantle.plugin.jei;

import com.google.common.collect.Streams;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.Ingredient;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.SafeClientAccess;
import slimeknights.mantle.recipe.crafting.ShapedRetexturedRecipe;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * JEI crafting extension to properly show, animate, and focus {@link ShapedRetexturedRecipe} instances
 */
public class RetexturableRecipeExtension implements ICraftingCategoryExtension<ShapedRetexturedRecipe> {
  /** Gets the list of all textured variants, fallback for JEI display */
  private static List<ItemStack> getDisplayOutputs(ShapedRetexturedRecipe recipe) {
    // set the output to display all variants from the texture ingredient
    Ingredient texture = recipe.getTexture();
    // fetch all stacks from the ingredient, note any variants that are not blocks will get a blank look
    RegistryAccess access = Objects.requireNonNull(SafeClientAccess.getRegistryAccess());
    List<ItemStack> displayOutputs = Arrays.stream(texture.getItems())
                                           .map(stack -> recipe.getResultItem(stack.getItem(), access))
                                           .toList();
    // empty display means the tag found nothing, so just use the original output
    return displayOutputs.isEmpty() ? List.of(recipe.getResultItem(access)) : displayOutputs;
  }

  /** Gets ingredient indexes of all texture slots. */
  private static int[] getTextureSlots(ShapedRetexturedRecipe recipe) {
    Ingredient texture = recipe.getTexture();
    // find out which inputs match the texture, we will need to use those for the focus link
    List<Ingredient> inputs = recipe.getIngredients();
    return IntStream.range(0, inputs.size()).filter(i -> ingredientsMatch(texture, inputs.get(i))).toArray();
  }

  /** Checks if two ingredients match based on their display items */
  private static boolean ingredientsMatch(Ingredient left, Ingredient right) {
    ItemStack[] leftStacks = left.getItems();
    ItemStack[] rightStacks = right.getItems();
    if (leftStacks.length != rightStacks.length) {
      return false;
    }
    for (int i = 0; i < leftStacks.length; i++) {
      if (!ItemStack.isSameItemSameComponents(leftStacks[i], rightStacks[i])) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Optional<ResourceLocation> getRegistryName(RecipeHolder<ShapedRetexturedRecipe> recipe) {
    return Optional.of(recipe.id());
  }

  @Override
  public int getWidth(RecipeHolder<ShapedRetexturedRecipe> recipe) {
    return recipe.value().getWidth();
  }

  @Override
  public int getHeight(RecipeHolder<ShapedRetexturedRecipe> recipe) {
    return recipe.value().getHeight();
  }

  @Override
  public void setRecipe(RecipeHolder<ShapedRetexturedRecipe> recipeHolder, IRecipeLayoutBuilder builder, ICraftingGridHelper craftingGridHelper, IFocusGroup focuses) {
    ShapedRetexturedRecipe recipe = recipeHolder.value();
    List<ItemStack> displayOutputs = getDisplayOutputs(recipe);
    int[] textureSlots = getTextureSlots(recipe);
//    guiItemStacks.addTooltipCallback(this);
    // we need the blank version for the sake of recipe lookup due to the subtype interpreter making it not the same
    builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).addItemStack(recipe.getResultItem(Objects.requireNonNull(SafeClientAccess.getRegistryAccess())));

    // add the itemstacks to the grid
    List<List<ItemStack>> inputStacks = recipe.getIngredients().stream().map(ingredient -> List.of(ingredient.getItems())).toList();
    int width = recipe.getWidth();
    int height = recipe.getHeight();
    List<IRecipeSlotBuilder> inputs = craftingGridHelper.createAndSetInputs(builder, VanillaTypes.ITEM_STACK, inputStacks, recipe.getWidth(), recipe.getHeight());
    IRecipeSlotBuilder output = craftingGridHelper.createAndSetOutputs(builder, displayOutputs);
    if (inputs.size() != 9) {
      Mantle.logger.error("Failed to create focus link for {} as the layout {} is not 3x3", recipeHolder.id(), builder.getClass().getName());
    } else {
      // link the output to all inputs that match the texture
      builder.createFocusLink(Streams.concat(Stream.of(output), Arrays.stream(textureSlots).mapToObj(i -> inputs.get(MantleJEIConstants.getCraftingIndex(i, width, height)))).toArray(IRecipeSlotBuilder[]::new));
    }
  }
}
