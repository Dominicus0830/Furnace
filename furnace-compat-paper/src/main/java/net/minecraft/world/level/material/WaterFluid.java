package net.minecraft.world.level.material;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** water 유체(Mojang 이름 shim, N0). 원천·흐름 두 종류. 값은 바닐라 1.21.1 과 같다. */
public abstract class WaterFluid extends FlowingFluid {

    @Override
    public Fluid getFlowing() { return Fluids.FLOWING_WATER; }

    @Override
    public Fluid getSource() { return Fluids.WATER; }

    @Override
    public Item getBucket() { return org.domi.furnace.compat.nms.NmsItems.get("minecraft:water_bucket"); }

    @Override
    public boolean isSame(Fluid fluid) { return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER; }

    @Override
    protected float getExplosionResistance() { return 100.0F; }

    @Override
    protected BlockState createLegacyState(FluidState state) { return furnaceLegacyBlock("minecraft:water", state); }

    /** 흐르는 water: level(1..8) 속성이 더 있다. */
    public static class Flowing extends WaterFluid {

        @Override
        protected List<Property<?>> furnaceStateProperties() { return List.of(FALLING, LEVEL); }

        @Override
        public int getAmount(FluidState state) { return state.getValue(LEVEL); }

        @Override
        public boolean isSource(FluidState state) { return false; }
    }

    /** water 원천. */
    public static class Source extends WaterFluid {

        @Override
        public int getAmount(FluidState state) { return 8; }

        @Override
        public boolean isSource(FluidState state) { return true; }
    }
}
