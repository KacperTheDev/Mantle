package slimeknights.mantle.data.loadable.common;

import com.google.gson.JsonSyntaxException;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.primitive.ResourceLocationLoadable;
import slimeknights.mantle.util.typed.TypedMap;

/** Loadable for holders from a dynamic registry. */
public record RegistryHolderLoadable<T>(ResourceKey<? extends Registry<T>> registryKey) implements ResourceLocationLoadable<Holder<T>> {
  private Registry<T> registry(TypedMap context, String operation) {
    RegistryAccess access = context.get(ContextKey.REGISTRY_ACCESS);
    if (access == null) {
      throw new IllegalStateException("Registry access is required to " + operation + " registry " + registryKey.location());
    }
    return access.registryOrThrow(registryKey);
  }

  @Override
  public Holder<T> fromKey(ResourceLocation name, String key, TypedMap context) {
    ResourceKey<T> entryKey = ResourceKey.create(registryKey, name);
    return registry(context, "parse " + key).getHolder(entryKey)
      .orElseThrow(() -> new JsonSyntaxException("Registry " + registryKey.location() + " does not contain ID " + name));
  }

  @Override
  public ResourceLocation getKey(Holder<T> holder) {
    return holder.unwrapKey()
      .filter(key -> key.isFor(registryKey))
      .map(ResourceKey::location)
      .orElseThrow(() -> new EncoderException("Holder is not bound to registry " + registryKey.location()));
  }

  @Override
  public Holder<T> decode(FriendlyByteBuf buffer, TypedMap context) {
    ResourceLocation location = buffer.readResourceLocation();
    Registry<T> registry;
    if (buffer instanceof RegistryFriendlyByteBuf registryBuffer) {
      registry = registryBuffer.registryAccess().registryOrThrow(registryKey);
    } else {
      registry = registry(context, "decode");
    }
    return registry.getHolder(ResourceKey.create(registryKey, location))
      .orElseThrow(() -> new DecoderException("Registry " + registryKey.location() + " does not contain ID " + location));
  }

  @Override
  public void encode(FriendlyByteBuf buffer, Holder<T> holder) {
    buffer.writeResourceLocation(getKey(holder));
  }
}
