package slimeknights.mantle.util;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import slimeknights.mantle.client.SafeClientAccess;

/** Resolves the active lookup used by registry-aware component codecs. */
public final class RegistryAccessUtil {
  private RegistryAccessUtil() {}

  public static RegistryAccess getRegistryAccess() {
    MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
    if (server != null) {
      return server.registryAccess();
    }
    Level level = SafeClientAccess.getLevel();
    if (level != null) {
      return level.registryAccess();
    }
    throw new IllegalStateException("Component serialization requires an active level registry access");
  }
}
