package slimeknights.mantle.recipe.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import slimeknights.mantle.Mantle;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Ingredient for non-component-sensitive items referenced by name.
 * Item IDs are intentionally resolved lazily so datagen can reference optional mods.
 */
public final class ItemNameIngredient implements ICustomIngredient {
  public static final ResourceLocation ID = Mantle.getResource("item_name");
  private static final Codec<List<ResourceLocation>> NAMES_CODEC = Codec.withAlternative(
    ResourceLocation.CODEC.listOf(), ResourceLocation.CODEC, List::of);
  public static final MapCodec<ItemNameIngredient> CODEC = NAMES_CODEC.fieldOf("item")
    .xmap(ItemNameIngredient::new, ingredient -> ingredient.names);
  public static final IngredientType<ItemNameIngredient> TYPE = new IngredientType<>(CODEC);

  private final List<ResourceLocation> names;

  private ItemNameIngredient(List<ResourceLocation> names) {
    if (names.isEmpty()) {
      throw new IllegalArgumentException("Item name ingredient requires at least one item ID");
    }
    this.names = List.copyOf(names);
  }

  /** Creates an ingredient from a list of names without resolving optional items during datagen. */
  public static Ingredient from(List<ResourceLocation> names) {
    return new ItemNameIngredient(names).toVanilla();
  }

  /** Creates an ingredient from a list of names without resolving optional items during datagen. */
  public static Ingredient from(ResourceLocation... names) {
    return from(Arrays.asList(names));
  }

  @Override
  public boolean test(@Nullable ItemStack stack) {
    return stack != null && !stack.isEmpty() && names.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
  }

  @Override
  public Stream<ItemStack> getItems() {
    return names.stream()
      .map(BuiltInRegistries.ITEM::getOptional)
      .flatMap(java.util.Optional::stream)
      .map(ItemStack::new);
  }

  @Override
  public boolean isSimple() {
    return true;
  }

  @Override
  public IngredientType<?> getType() {
    return TYPE;
  }

  /** Legacy JSON entry point retained for API consumers. */
  @Deprecated(forRemoval = true)
  public com.google.gson.JsonElement toJson() {
    return Ingredient.CODEC.encodeStart(JsonOps.INSTANCE, toVanilla()).getOrThrow();
  }

  @Override
  public boolean equals(Object object) {
    return this == object || object instanceof ItemNameIngredient other && names.equals(other.names);
  }

  @Override
  public int hashCode() {
    return Objects.hash(names);
  }
}
