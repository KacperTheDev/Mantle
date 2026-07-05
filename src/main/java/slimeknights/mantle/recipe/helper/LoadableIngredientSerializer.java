package slimeknights.mantle.recipe.helper;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import slimeknights.mantle.data.loadable.record.RecordLoadable;

import java.util.function.Function;

/** Ingredient serializer made using loadables */
public record LoadableIngredientSerializer<T extends ICustomIngredient>(RecordLoadable<T> loadable) {
  /** Creates a codec from JSON load/save functions. */
  public static <T> MapCodec<T> mapCodec(Function<JsonObject,T> parser, Function<T,JsonObject> serializer) {
    return MapCodec.assumeMapUnsafe(Codec.PASSTHROUGH.flatXmap(
      dynamic -> {
        if (dynamic.convert(JsonOps.INSTANCE).getValue() instanceof JsonObject json) {
          return DataResult.success(parser.apply(json));
        }
        return DataResult.error(() -> "Expected ingredient object");
      },
      value -> DataResult.success(new Dynamic<>(JsonOps.INSTANCE, serializer.apply(value)))));
  }

  /** Gets the codec for this serializer. */
  public MapCodec<T> codec() {
    return mapCodec(this::parse, this::serialize);
  }

  public T parse(FriendlyByteBuf buffer) {
    return loadable.decode(buffer);
  }

  public T parse(JsonObject json) {
    return loadable.deserialize(json);
  }

  public void write(FriendlyByteBuf buffer, T ingredient) {
    loadable.encode(buffer, ingredient);
  }

  /** Serializes the ingredient to JSON */
  public JsonObject serialize(T ingredient) {
    JsonObject json = new JsonObject();
    loadable.serialize(ingredient, json);
    return json;
  }
}
