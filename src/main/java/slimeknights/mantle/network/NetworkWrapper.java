package slimeknights.mantle.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.network.packet.ISimplePacket;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A small network implementation/wrapper using Mantle packets inside a NeoForge custom payload.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class NetworkWrapper {
  /** Compatibility shim for old call sites using {@code INSTANCE.network.sendToServer(packet)}. */
  public final Sender network = new Sender();
  private final CustomPacketPayload.Type<WrappedPayload> payloadType;
  private final String version;
  private final Map<Integer, Registration<? extends ISimplePacket>> byId = new HashMap<>();
  private final Map<Class<?>, Integer> byClass = new HashMap<>();
  private int id = 0;

  /**
   * Creates a new network wrapper
   * @param channelName  Unique packet channel name
   * @deprecated Give your channel a version number.
   */
  @Deprecated
  public NetworkWrapper(ResourceLocation channelName) {
    this(channelName, "1");
  }

  public NetworkWrapper(ResourceLocation channelName, String version) {
    this.payloadType = new CustomPacketPayload.Type<>(channelName);
    this.version = version;
  }

  /** Registers the payload wrapper with NeoForge. */
  public void registerPayload(RegisterPayloadHandlersEvent event) {
    PayloadRegistrar registrar = event.registrar(payloadType.id().getNamespace()).versioned(version);
    registrar.playBidirectional(payloadType, StreamCodec.ofMember(WrappedPayload::encode, this::decode), this::handle);
  }

  /**
   * Registers a new {@link ISimplePacket}
   * @param clazz    Packet class
   * @param decoder  Packet decoder, typically the constructor
   * @param direction Ignored, kept for source compatibility.
   * @param <MSG>  Packet class type
   */
  public <MSG extends ISimplePacket> void registerPacket(Class<MSG> clazz, Function<FriendlyByteBuf, MSG> decoder, PacketDirection direction) {
    int nextId = this.id++;
    byId.put(nextId, new Registration<>(clazz, wrapLogger(clazz, decoder)));
    byClass.put(clazz, nextId);
  }

  /** Wraps the given decoder function */
  private static <MSG extends ISimplePacket> Function<FriendlyByteBuf,MSG> wrapLogger(Class<MSG> clazz, Function<FriendlyByteBuf,MSG> decoder) {
    return buffer -> {
      try {
        return decoder.apply(buffer);
      } catch (Exception e) {
        Mantle.logger.error("Exception while decoding packet of class {}", clazz.getName(), e);
        throw e;
      }
    };
  }

  private WrappedPayload decode(RegistryFriendlyByteBuf buffer) {
    int id = buffer.readVarInt();
    Registration<? extends ISimplePacket> registration = byId.get(id);
    if (registration == null) {
      throw new IllegalArgumentException("Unknown Mantle packet ID " + id);
    }
    return new WrappedPayload(id, registration.decoder.apply(buffer));
  }

  private void handle(WrappedPayload payload, IPayloadContext context) {
    payload.packet.handle(context);
  }

  private WrappedPayload wrap(ISimplePacket packet) {
    Integer id = byClass.get(packet.getClass());
    if (id == null) {
      throw new IllegalArgumentException("Unregistered Mantle packet " + packet.getClass().getName());
    }
    return new WrappedPayload(id, packet);
  }


  /* Sending packets */

  /**
   * Sends a packet to the server
   * @param msg  Packet to send
   */
  public void sendToServer(ISimplePacket msg) {
    PacketDistributor.sendToServer(wrap(msg));
  }

  /**
   * Sends a vanilla packet to the given entity
   * @param player  Player receiving the packet
   * @param packet  Packet
   */
  public void sendVanillaPacket(Packet<?> packet, Entity player) {
    if (player instanceof ServerPlayer sPlayer) {
      sPlayer.connection.send(packet);
    }
  }

  /**
   * Sends a packet to a player
   * @param msg     Packet
   * @param player  Player to send
   */
  public void sendTo(ISimplePacket msg, Player player) {
    if (player instanceof ServerPlayer serverPlayer) {
      sendTo(msg, serverPlayer);
    }
  }

  /**
   * Sends a packet to a player
   * @param msg     Packet
   * @param player  Player to send
   */
  public void sendTo(ISimplePacket msg, ServerPlayer player) {
    if (!(player instanceof FakePlayer)) {
      PacketDistributor.sendToPlayer(player, wrap(msg));
    }
  }

  /**
   * Sends a packet to players near a location
   * @param msg          Packet to send
   * @param serverWorld  World instance
   * @param position     Position within range
   */
  public void sendToClientsAround(ISimplePacket msg, ServerLevel serverWorld, BlockPos position) {
    PacketDistributor.sendToPlayersTrackingChunk(serverWorld, new ChunkPos(position), wrap(msg));
  }

  /**
   * Sends a packet to all entities tracking the given entity
   * @param msg     Packet
   * @param entity  Entity to check
   */
  public void sendToTrackingAndSelf(ISimplePacket msg, Entity entity) {
    PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, wrap(msg));
  }

  /**
   * Sends a packet to all entities tracking the given entity
   * @param msg     Packet
   * @param entity  Entity to check
   */
  public void sendToTracking(ISimplePacket msg, Entity entity) {
    PacketDistributor.sendToPlayersTrackingEntity(entity, wrap(msg));
  }

  /** Packet direction kept for registration readability. */
  public enum PacketDirection {
    PLAY_TO_CLIENT,
    PLAY_TO_SERVER
  }

  /** Compatibility sender for old {@code network.sendToServer(packet)} call sites. */
  public class Sender {
    public void sendToServer(ISimplePacket msg) {
      NetworkWrapper.this.sendToServer(msg);
    }
  }

  private record Registration<MSG extends ISimplePacket>(Class<MSG> clazz, Function<FriendlyByteBuf, MSG> decoder) {}

  private record WrappedPayload(int id, ISimplePacket packet) implements CustomPacketPayload {
    @Override
    public Type<? extends CustomPacketPayload> type() {
      return MantleNetwork.INSTANCE.payloadType;
    }

    public void encode(RegistryFriendlyByteBuf buffer) {
      buffer.writeVarInt(id);
      packet.encode(buffer);
    }
  }
}
