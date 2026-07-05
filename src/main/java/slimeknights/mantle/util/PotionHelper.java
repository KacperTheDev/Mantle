package slimeknights.mantle.util;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/** Helpers for the 1.21 potion data component. */
public final class PotionHelper {
  private PotionHelper() {}

  /** Gets the potion stored on the given stack. */
  public static Potion getPotion(ItemStack stack) {
    PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
    return contents.potion().orElse(Potions.WATER).value();
  }

  /** Sets the potion stored on the given stack. */
  public static ItemStack setPotion(ItemStack stack, Potion potion) {
    stack.set(DataComponents.POTION_CONTENTS, new PotionContents(BuiltInRegistries.POTION.wrapAsHolder(potion)));
    return stack;
  }

  /** Sets the potion stored on the given stack. */
  public static ItemStack setPotion(ItemStack stack, Holder<Potion> potion) {
    stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
    return stack;
  }
}
