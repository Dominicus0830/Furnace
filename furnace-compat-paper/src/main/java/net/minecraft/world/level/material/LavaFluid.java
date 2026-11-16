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

    /** 바닐라: 그 레벨 차원이 초고온(네더)이면 10, 아니면 30. 서버 월드가 아닌 레벨은 UnsupportedNmsCall(차원을 알 수 없다). 아무 스레드. */
    @Override
    public int getTickDelay(net.minecraft.world.level.LevelReader level) {
        var w = org.domi.furnace.compat.nms.NmsEffects.world(level, "LavaFluid#getTickDelay");
        return org.domi.furnace.compat.nms.NmsChunks.bridge().ultraWarm(w) ? 10 : 30;
    }

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
