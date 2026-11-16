package net.minecraft.world.level.material;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** lava 유체(Mojang 이름 shim, N0). 원천·흐름 두 종류. 값은 바닐라 1.21.1 과 같다. */
public abstract class LavaFluid extends FlowingFluid {

    public static final float MIN_LEVEL_CUTOFF = 0.44444445F;

    @Override
    public Fluid getFlowing() { return Fluids.FLOWING_LAVA; }

    @Override
    public Fluid getSource() { return Fluids.LAVA; }

    @Override
    public Item getBucket() { return org.domi.furnace.compat.nms.NmsItems.get("minecraft:lava_bucket"); }

    @Override
    public boolean isSame(Fluid fluid) { return fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA; }

    @Override
    protected float getExplosionResistance() { return 100.0F; }

    @Override
    protected boolean isRandomlyTicking() { return true; }

    @Override
    protected BlockState createLegacyState(FluidState state) { return furnaceLegacyBlock("minecraft:lava", state); }

    /** 흐르는 lava: level(1..8) 속성이 더 있다. */
    public static class Flowing extends LavaFluid {

        @Override
        protected List<Property<?>> furnaceStateProperties() { return List.of(FALLING, LEVEL); }

        @Override
        public int getAmount(FluidState state) { return state.getValue(LEVEL); }

        @Override
        public boolean isSource(FluidState state) { return false; }
    }

    /** lava 원천. */
    public static class Source extends LavaFluid {

        @Override
        public int getAmount(FluidState state) { return 8; }

        @Override
        public boolean isSource(FluidState state) { return true; }
    }
}
