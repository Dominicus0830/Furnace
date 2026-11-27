package net.minecraft.world.level.material;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** 빈 유체(Mojang 이름 shim, N0). 값은 바닐라 1.21.1 {@code epb} 와 같다. */
class EmptyFluid {

    @Override
    public Item getBucket() { return org.domi.furnace.compat.nms.NmsItems.get("minecraft:air"); }

    @Override
    protected boolean isEmpty() { return true; }

    /** 바닐라 Fluid 기본값 — EmptyFluid 는 재정의하지 않는다. */
    @Override
    public int getTickDelay(net.minecraft.world.level.LevelReader level) { return 0; }

    @Override
    protected float getExplosionResistance() { return 0.0F; }

    @Override
    public float getOwnHeight(FluidState state) { return 0.0F; }

    @Override
    protected BlockState createLegacyState(FluidState state) { return Blocks.AIR.defaultBlockState(); }

    @Override
    public boolean isSource(FluidState state) { return false; }

    @Override
    public int getAmount(FluidState state) { return 0; }
}
