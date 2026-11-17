package net.minecraft.world.level.material;

import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 흐르는 유체(물·용암)의 바탕(Mojang 이름 shim, N0). 원천·흐름 상태 만들기와 레거시 level 변환은 바닐라 1.21.1 {@code epc} 와 같다.
 * 흐름 계산(spread·getFlow)은 서버 안에 있어 두지 않았다.
 */
public abstract class FlowingFluid extends Fluid {

    public static final BooleanProperty FALLING = BooleanProperty.create("falling");
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 8);

    @Override
    protected List<Property<?>> furnaceStateProperties() { return List.of(FALLING); }

    public abstract Fluid getFlowing();

    public abstract Fluid getSource();

    public FluidState getFlowing(int amount, boolean falling) {
        return getFlowing().defaultFluidState().setValue(LEVEL, amount).setValue(FALLING, falling);
    }

    public FluidState getSource(boolean falling) { return getSource().defaultFluidState().setValue(FALLING, falling); }

    /** 바닐라: 원천 0, 아니면 8 - min(양, 8), 떨어지면 +8. */
    protected static int getLegacyLevel(FluidState state) {
        return state.isSource() ? 0 : 8 - Math.min(state.getAmount(), 8) + (state.getValue(FALLING) ? 8 : 0);
    }

    @Override
    public float getOwnHeight(FluidState state) { return state.getAmount() / 9.0F; }

    @Override
    public abstract int getAmount(FluidState state);

    /** 호환 모듈 전용 — 물·용암 블록의 level 을 맞춘 상태(바닐라 createLegacyState). */
    protected static BlockState furnaceLegacyBlock(String block, FluidState state) {
        BlockState base = org.domi.furnace.compat.nms.NmsBlocks.get(block).defaultBlockState();
        @SuppressWarnings("unchecked")
        Property<Integer> level = (Property<Integer>) base.getBlock().getStateDefinition().getProperty("level");
        return base.setValue(level, getLegacyLevel(state));
    }
}
