package slimeknights.mantle.recipe.condition;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.common.conditions.ICondition;
import slimeknights.mantle.Mantle;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Condition checking for a combination of tags having any entries
 * @param match  List of tags that the entry must match
 * @param ignore Entries in this tag will be ignored towards the match. If null, all entries are considered
 * @param <T>  Registry type
 */
@SuppressWarnings("unused")
public record TagCombinationCondition<T>(List<TagKey<T>> match, @Nullable TagKey<T> ignore) implements ICondition {
  public static final ResourceLocation ID = Mantle.getResource("tag_combination_filled");
  private static final Codec<List<ResourceLocation>> MATCH_CODEC = Codec.either(ResourceLocation.CODEC, ResourceLocation.CODEC.listOf())
    .xmap(either -> either.map(List::of, Function.identity()), list -> list.size() == 1 ? Either.left(list.get(0)) : Either.right(list));
  public static final MapCodec<TagCombinationCondition<?>> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
    ResourceLocation.CODEC.optionalFieldOf("registry", Registries.ITEM.location()).forGetter(value -> value.match.get(0).registry().location()),
    MATCH_CODEC.fieldOf("match").forGetter(value -> value.match.stream().map(TagKey::location).toList()),
    ResourceLocation.CODEC.optionalFieldOf("ignore").forGetter(value -> Optional.ofNullable(value.ignore).map(TagKey::location))
  ).apply(instance, TagCombinationCondition::read));

  public TagCombinationCondition {
    if (match.isEmpty()) {
      throw new IllegalArgumentException("Must match at least 1 tag");
    }
  }

  /** Creates a new instance ignoring the first tag and matching the rest */
  @SafeVarargs
  public static <T> TagCombinationCondition<T> match(@Nullable TagKey<T> ignore, TagKey<T>... match) {
    return new TagCombinationCondition<>(List.of(match), ignore);
  }

  /** Creates a new instance matching all the passed tags */
  @SafeVarargs
  public static <T> TagCombinationCondition<T> intersection(TagKey<T>... match) {
    return match(null, match);
  }

  /** Creates a new instance matching all the passed tags */
  public static <T> TagCombinationCondition<T> difference(TagKey<T> match, TagKey<T> ignore) {
    return match(ignore, match);
  }


  public ResourceLocation getID() {
    return ID;
  }

  @Override
  public MapCodec<? extends ICondition> codec() {
    return CODEC;
  }

  @Override
  public boolean test(IContext context) {
    // if there is just one tag, just needs to be filled
    List<Collection<Holder<T>>> tags = match.stream().map(context::getTag).toList();
    Collection<Holder<T>> ignored = ignore == null ? List.of() : context.getTag(ignore);
    if (tags.size() == 1 && ignored.isEmpty()) {
      return !tags.get(0).isEmpty();
    }
    // if any remaining tag is empty, give up
    int count = tags.size();
    for (int i = 1; i < count; i++) {
      if (tags.get(i).isEmpty()) {
        return false;
      }
    }

    // all tags have something, so find the first item that is in all tags
    itemLoop:
    for (Holder<T> entry : tags.get(0)) {
      if (ignored.contains(entry)) {
        continue;
      }
      // find the first item contained in all other intersection tags
      for (int i = 1; i < count; i++) {
        if (!tags.get(i).contains(entry)) {
          continue itemLoop;
        }
      }
      // all tags contain the item? success
      return true;
    }
    // no item in all tags
    return false;
  }

  /** Reads the condition from codec fields. */
  private static TagCombinationCondition<?> read(ResourceLocation registryName, List<ResourceLocation> match, Optional<ResourceLocation> ignore) {
    ResourceKey<Registry<Object>> registry = ResourceKey.createRegistryKey(registryName);
    return new TagCombinationCondition<>(
      match.stream().map(id -> TagKey.create(registry, id)).toList(),
      ignore.map(id -> TagKey.create(registry, id)).orElse(null));
  }
}
