package slimeknights.mantle.recipe.ingredient;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import slimeknights.mantle.util.PotionHelper;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.crafting.IngredientType;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.helper.LoadableIngredientSerializer;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/** Ingredient that shows all potion variants on the displayed item list */
public class PotionDisplayIngredient extends ItemIngredient {
  /** Ingredient serializer instance */
  public static final LoadableIngredientSerializer<PotionDisplayIngredient> SERIALIZER = new LoadableIngredientSerializer<>(RecordLoadable.create(ItemsField.INSTANCE, TAG_FIELD, PotionDisplayIngredient::new));
  public static final IngredientType<PotionDisplayIngredient> TYPE = new IngredientType<>(SERIALIZER.codec());

  protected PotionDisplayIngredient(List<Item> items, @Nullable TagKey<Item> tag) {
    super(items, tag);
  }

  /** Creates a ingredient matching a list of items */
  public static Ingredient of(List<ItemLike> items) {
    return new PotionDisplayIngredient(toItem(items), null).toVanilla();
  }

  /** Creates a ingredient matching a list of items */
  public static Ingredient of(ItemLike... items) {
    return of(List.of(items));
  }

  /** Creates a ingredient matching a tag */
  public static Ingredient of(TagKey<Item> tag) {
    return new PotionDisplayIngredient(List.of(), tag).toVanilla();
  }

  @Override
  public boolean isSimple() {
    return true;
  }

  @Override
  public Stream<ItemStack> getItems() {
    // if empty, means we want wildcard, show all potions on the stack
    List<ItemStack> parentStacks = super.getItems().toList();
    return BuiltInRegistries.POTION.stream()
      .filter(pot -> pot != Potions.WATER.value())
      .flatMap(pot -> parentStacks.stream().map(item -> PotionHelper.setPotion(item.copy(), pot)));
  }

  @Override
  public IngredientType<?> getType() {
    return TYPE;
  }

  public JsonElement toJson() {
    JsonObject json = SERIALIZER.serialize(this);
    json.addProperty("type", Mantle.getResource("potion_display").toString());
    return json;
  }
}
