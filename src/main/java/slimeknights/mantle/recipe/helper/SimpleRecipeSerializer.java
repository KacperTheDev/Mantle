package slimeknights.mantle.recipe.helper;

import com.google.gson.JsonObject;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.function.Function;

/** Simple implementation of a recipe serializer with no properties other than recipe ID. */
public record SimpleRecipeSerializer<T extends Recipe<?>>(Function<ResourceLocation,T> constructor) implements RecipeSerializer<T> {
  private static final ResourceLocation CODEC_ID = ResourceLocation.fromNamespaceAndPath("mantle", "unknown_recipe");

  public T fromJson(ResourceLocation id, JsonObject pSerializedRecipe) {
    return constructor.apply(id);
  }

  public MapCodec<T> codec() {
    return MapCodec.unit(() -> constructor.apply(CODEC_ID));
  }

  public T fromNetwork(ResourceLocation id, FriendlyByteBuf pBuffer) {
    return constructor.apply(id);
  }

  public void toNetwork(FriendlyByteBuf pBuffer, T pRecipe) {}

  @Override
  public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() {
    return StreamCodec.unit(constructor.apply(CODEC_ID));
  }
}
