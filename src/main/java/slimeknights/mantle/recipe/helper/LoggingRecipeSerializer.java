package slimeknights.mantle.recipe.helper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import slimeknights.mantle.Mantle;

import javax.annotation.Nullable;

/**
 * Recipe serializer that logs network exceptions before throwing them as otherwise the exceptions may be invisible
 * @param <T>  Recipe class
 */
public interface LoggingRecipeSerializer<T extends Recipe<?>> extends RecipeSerializer<T> {
  /** Dummy ID for codecs, as vanilla 1.21 supplies recipe IDs via RecipeHolder instead of serializers. */
  ResourceLocation CODEC_ID = ResourceLocation.fromNamespaceAndPath("mantle", "unknown_recipe");

  /** Reads the legacy public JSON shape. The actual runtime ID is supplied by RecipeHolder in 1.21. */
  T fromJson(ResourceLocation id, JsonObject json);

  /** Writes the legacy public JSON shape without changing field names or values. */
  JsonObject toJson(T recipe);

  /**
   * Read the recipe from the packet
   * @param id      Recipe ID
   * @param buffer  Buffer instance
   * @return  Parsed recipe
   * @throws RuntimeException  If any errors happen, the exception will be logged automatically
   */
  @Nullable
  T fromNetworkSafe(ResourceLocation id, RegistryFriendlyByteBuf buffer);

  /**
   * Write the method to the buffer
   * @param buffer  Buffer instance
   * @param recipe  Recipe instance
   * @throws RuntimeException  If any errors happen, the exception will be logged automatically
   */
  void toNetworkSafe(RegistryFriendlyByteBuf buffer, T recipe);

  @Nullable
  default T fromNetwork(ResourceLocation id, RegistryFriendlyByteBuf buffer) {
    try {
      return fromNetworkSafe(id, buffer);
    } catch (RuntimeException e) {
      Mantle.logger.error("{}: Error reading recipe {} from packet", this.getClass().getSimpleName(), id, e);
      throw e;
    }
  }

  default void toNetwork(RegistryFriendlyByteBuf buffer, T recipe) {
    try {
      toNetworkSafe(buffer, recipe);
    } catch (RuntimeException e) {
      Mantle.logger.error("{}: Error writing recipe of class {} and type {} to packet", this.getClass().getSimpleName(), recipe.getClass().getSimpleName(), recipe.getType(), e);
      throw e;
    }
  }

  @Override
  default MapCodec<T> codec() {
    Codec<T> codec = new Codec<>() {
      @Override
      public <O> DataResult<Pair<T,O>> decode(DynamicOps<O> ops, O input) {
        try {
          JsonObject json = ops.convertTo(JsonOps.INSTANCE, input).getAsJsonObject();
          return DataResult.success(Pair.of(fromJson(CODEC_ID, json), input));
        } catch (JsonParseException | IllegalStateException e) {
          return DataResult.error(e::getMessage);
        }
      }

      @Override
      public <O> DataResult<O> encode(T input, DynamicOps<O> ops, O prefix) {
        try {
          return DataResult.success(JsonOps.INSTANCE.convertTo(ops, toJson(input)));
        } catch (RuntimeException e) {
          return DataResult.error(e::getMessage);
        }
      }
    };
    return MapCodec.assumeMapUnsafe(codec);
  }

  @Override
  default StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() {
    return StreamCodec.of(this::toNetwork, buffer -> fromNetwork(CODEC_ID, buffer));
  }
}
