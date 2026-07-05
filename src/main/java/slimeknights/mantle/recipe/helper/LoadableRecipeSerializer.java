package slimeknights.mantle.recipe.helper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.primitive.StringLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.util.typed.TypedMapBuilder;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Recipe serializer instance using loadables. Use {@link ContextKey#ID} to get the recipe ID.
 * @param <T>  Recipe type
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class LoadableRecipeSerializer<T extends Recipe<?>> implements LoggingRecipeSerializer<T> {
  /** Dummy ID for codecs, as vanilla 1.21 supplies recipe IDs via RecipeHolder instead of serializers. */
  private static final ResourceLocation CODEC_ID = ResourceLocation.fromNamespaceAndPath(Mantle.modId, "unknown_recipe");
  /** Context key to use if you want the recipe serializer passed into your recipe */
  public static final ContextKey<RecipeSerializer<?>> SERIALIZER = new ContextKey<>("serializer");
  /** Context key to use if you want a type aware serializer in the recipe, requires {@link #of(RecordLoadable, Supplier)} for your serializer. */
  public static final ContextKey<TypeAwareRecipeSerializer<?>> TYPED_SERIALIZER = new ContextKey<>("typed_serializer");
  /** Context key to use if you want the recipe type passed into your recipe, requires {@link #of(RecordLoadable, Supplier)} for your serializer. */
  public static final ContextKey<RecipeType<?>> TYPE = new ContextKey<>("type");
  /** Field for a group key in a recipe (common requirement) */
  public static final LoadableField<String,Recipe<?>> RECIPE_GROUP = StringLoadable.DEFAULT.defaultField("group", "", Recipe::getGroup);


  protected final RecordLoadable<T> loadable;

  /** Creates a standard serializer from a loadable */
  public static <T extends Recipe<?>> RecipeSerializer<T> of(RecordLoadable<T> loadable) {
    return new LoadableRecipeSerializer<>(loadable);
  }

  /** Creates a type aware serializer from a loadable */
  public static <T extends R, R extends Recipe<?>> TypeAwareRecipeSerializer<T> of(RecordLoadable<T> loadable, Supplier<? extends RecipeType<R>> type) {
    return new TypeAware<>(loadable, type);
  }

  /** Creates a serializer that is deprecated, logging a warning when used */
  public static <T extends Recipe<?>> RecipeSerializer<T> deprecated(RecordLoadable<T> loadable, String replacement) {
    return new Deprecated<>(loadable, replacement);
  }

  /** Builds a context for the given ID */
  protected TypedMapBuilder buildContext(ResourceLocation id) {
    return TypedMapBuilder.builder().put(ContextKey.ID, id).put(ContextKey.DEBUG, "Recipe " + id).put(SERIALIZER, this);
  }

  public T fromJson(ResourceLocation id, JsonObject json) {
    return loadable.deserialize(json, buildContext(id).build());
  }

  @Override
  public MapCodec<T> codec() {
    return MapCodec.assumeMapUnsafe(new RecipeLoadableCodec<>(this, loadable));
  }

  @Override
  public T fromNetworkSafe(ResourceLocation id, FriendlyByteBuf buffer) {
    return loadable.decode(buffer, buildContext(id).build());
  }

  @Override
  public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() {
    return StreamCodec.of(this::toNetworkSafe, buffer -> fromNetworkSafe(CODEC_ID, buffer));
  }

  @Override
  public void toNetworkSafe(FriendlyByteBuf buffer, T recipe) {
    loadable.encode(buffer, recipe);
  }

  public static class TypeAware<T extends Recipe<?>> extends LoadableRecipeSerializer<T> implements TypeAwareRecipeSerializer<T> {
    private final Supplier<? extends RecipeType<?>> type;
    protected TypeAware(RecordLoadable<T> loadable, Supplier<? extends RecipeType<?>> type) {
      super(loadable);
      this.type = type;
    }

    @Override
    protected TypedMapBuilder buildContext(ResourceLocation id) {
      return super.buildContext(id).put(TYPE, getType()).put(TYPED_SERIALIZER, this);
    }

    @Override
    public RecipeType<?> getType() {
      return type.get();
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() {
      return StreamCodec.of(this::toNetworkSafe, buffer -> fromNetworkSafe(CODEC_ID, buffer));
    }
  }

  /** Helper class that logs a warning on recipe parse about planned removal */
  private static class Deprecated<T extends Recipe<?>> extends LoadableRecipeSerializer<T> {
    private final String replacement;
    protected Deprecated(RecordLoadable<T> loadable, String replacement) {
      super(loadable);
      this.replacement = replacement;
    }

    @Override
    public T fromJson(ResourceLocation id, JsonObject json) {
      T recipe = super.fromJson(id, json);
      Mantle.logger.warn("Using deprecated recipe serializer {} for recipe {}, {}", BuiltInRegistries.RECIPE_SERIALIZER.getKey(this), id, replacement);
      return recipe;
    }
  }

  private record RecipeLoadableCodec<T extends Recipe<?>>(LoadableRecipeSerializer<T> serializer, RecordLoadable<T> loadable) implements com.mojang.serialization.Codec<T> {
    @Override
    public <O> DataResult<Pair<T,O>> decode(DynamicOps<O> ops, O input) {
      try {
        JsonObject json = ops.convertTo(JsonOps.INSTANCE, input).getAsJsonObject();
        return DataResult.success(Pair.of(loadable.deserialize(json, serializer.buildContext(CODEC_ID).build()), input));
      } catch (JsonParseException | IllegalStateException e) {
        Mantle.logger.warn("Unable to decode recipe using loadable {}", loadable, e);
        return DataResult.error(e::getMessage);
      }
    }

    @Override
    public <O> DataResult<O> encode(T input, DynamicOps<O> ops, O prefix) {
      try {
        JsonObject json = new JsonObject();
        loadable.serialize(input, json);
        return DataResult.success(JsonOps.INSTANCE.convertTo(ops, json));
      } catch (JsonParseException | IllegalStateException e) {
        Mantle.logger.warn("Unable to encode recipe using loadable {}", loadable, e);
        return DataResult.error(e::getMessage);
      }
    }
  }
}
