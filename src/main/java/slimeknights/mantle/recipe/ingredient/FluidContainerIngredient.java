package slimeknights.mantle.recipe.ingredient;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.recipe.helper.LoadableIngredientSerializer;
import slimeknights.mantle.registration.object.FluidObject;
import slimeknights.mantle.util.JsonHelper;

import javax.annotation.Nullable;
import java.util.stream.Stream;

/** Ingredient that matches a container of fluid */
@SuppressWarnings("unused")  // API
public class FluidContainerIngredient implements ICustomIngredient {
  public static final ResourceLocation ID = Mantle.getResource("fluid_container");
  public static final Serializer SERIALIZER = new Serializer();
  public static final IngredientType<FluidContainerIngredient> TYPE = new IngredientType<>(SERIALIZER.codec());

  /** Ingredient to use for matching */
  private final FluidIngredient fluidIngredient;
  /** Internal ingredient to display the ingredient recipe viewers */
  @Nullable
  private final Ingredient display;
  private ItemStack[] displayStacks;
  protected FluidContainerIngredient(FluidIngredient fluidIngredient, @Nullable Ingredient display) {
    this.fluidIngredient = fluidIngredient;
    this.display = display;
  }

  /** Creates an instance from a fluid ingredient with a display container */
  public static FluidContainerIngredient fromIngredient(FluidIngredient ingredient, Ingredient display) {
    return new FluidContainerIngredient(ingredient, display);
  }

  /** Creates an instance from a fluid ingredient with no display, not recommended */
  public static FluidContainerIngredient fromIngredient(FluidIngredient ingredient) {
    return new FluidContainerIngredient(ingredient, null);
  }

  /** Creates an instance from a fluid ingredient with a display container */
  public static FluidContainerIngredient fromFluid(FluidObject<?> fluid) {
    return fromIngredient(fluid.ingredient(FluidType.BUCKET_VOLUME), Ingredient.of(fluid));
  }

  @Override
  public boolean test(@Nullable ItemStack stack) {
    // first, must have a fluid capability
    if (stack == null || stack.isEmpty()) {
      return false;
    }
    IFluidHandlerItem cap = stack.getCapability(Capabilities.FluidHandler.ITEM);
    if (cap == null || cap.getTanks() != 1) {
      return false;
    }
    FluidStack contained = cap.getFluidInTank(0);
    if (contained.isEmpty() || fluidIngredient.getAmount(contained.getFluid()) != contained.getAmount() || !fluidIngredient.test(contained.getFluid())) {
      return false;
    }
    // so far so good, from this point on we are forced to make copies as we need to try draining
    ItemStack copy = stack.copyWithCount(1);
    IFluidHandlerItem copyCap = copy.getCapability(Capabilities.FluidHandler.ITEM);
    if (copyCap == null) {
      return false;
    }
    Fluid fluid = copyCap.getFluidInTank(0).getFluid();
    int amount = fluidIngredient.getAmount(fluid);
    FluidStack drained = copyCap.drain(amount, FluidAction.EXECUTE);
    // we need an exact match, and we need the resulting container item to be the same as the item stack's container item
    return drained.getFluid() == fluid && drained.getAmount() == amount && ItemStack.matches(stack.getCraftingRemainingItem(), copyCap.getContainer());
  }

  @Override
  public Stream<ItemStack> getItems() {
    if (displayStacks == null) {
      // no container? unfortunately hard to display this recipe so show nothing
      if (display == null) {
        displayStacks = new ItemStack[0];
      } else {
        displayStacks = display.getItems();
      }
    }
    return Stream.of(displayStacks);
  }

  public JsonElement toJson() {
    JsonObject json = serializeData();
    json.addProperty("type", ID.toString());
    return json;
  }

  /** Serializes fields for the ingredient codec. */
  private JsonObject serializeData() {
    JsonElement element = fluidIngredient.serialize();
    JsonObject json;
    if (element.isJsonObject()) {
      json = element.getAsJsonObject();
    } else {
      json = new JsonObject();
      json.add("fluid", element);
    }
    if (display != null) {
      json.add("display", Ingredient.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, display).result().orElse(JsonNull.INSTANCE));
    }
    return json;
  }

  @Override
  public boolean isSimple() {
    return false;
  }

  @Override
  public IngredientType<?> getType() {
    return TYPE;
  }

  /** Serializer logic */
  public static class Serializer {
    public com.mojang.serialization.MapCodec<FluidContainerIngredient> codec() {
      return LoadableIngredientSerializer.mapCodec(this::parse, FluidContainerIngredient::serializeData);
    }

    public FluidContainerIngredient parse(JsonObject json) {
      FluidIngredient fluidIngredient;
      // if we have fluid and its not a primitive, then its nested
      if (json.has("fluid") && !json.get("fluid").isJsonPrimitive()) {
        fluidIngredient = FluidIngredient.LOADABLE.getIfPresent(json, "fluid");
      } else {
        fluidIngredient = FluidIngredient.LOADABLE.convert(json, "fluid");
      }
      Ingredient display = null;
      if (json.has("display")) {
        display = Ingredient.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, JsonHelper.getElement(json, "display")).result().orElse(Ingredient.EMPTY);
      }
      return new FluidContainerIngredient(fluidIngredient, display);
    }

    public FluidContainerIngredient parse(FriendlyByteBuf buffer) {
      FluidIngredient fluidIngredient = FluidIngredient.LOADABLE.decode(buffer);
      Ingredient display = null;
      if (buffer.readBoolean() && buffer instanceof net.minecraft.network.RegistryFriendlyByteBuf registryBuffer) {
        display = Ingredient.CONTENTS_STREAM_CODEC.decode(registryBuffer);
      }
      return new FluidContainerIngredient(fluidIngredient, display);
    }

    public void write(FriendlyByteBuf buffer, FluidContainerIngredient ingredient) {
      FluidIngredient.LOADABLE.encode(buffer, ingredient.fluidIngredient);
      if (ingredient.display != null) {
        buffer.writeBoolean(true);
        if (buffer instanceof net.minecraft.network.RegistryFriendlyByteBuf registryBuffer) {
          Ingredient.CONTENTS_STREAM_CODEC.encode(registryBuffer, ingredient.display);
        }
      } else {
        buffer.writeBoolean(false);
      }
    }
  }
}
