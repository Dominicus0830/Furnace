package net.minecraft.world.level.material;

import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 유체 상태 하나(Mojang 이름 shim, N0). 종류마다 미리 만든 표의 같은 객체라 바닐라처럼 {@code ==} 로 비교된다.
 * 월드가 필요한 메서드 중 getHeight·getFlow·shouldRenderBackwardUpFace 는 두지 않았다({@link #tick} 은 서버 유체 틱으로 잇는다).
 */
class FluidState {

    public static final int AMOUNT_MAX = 9;
    public static final int AMOUNT_FULL = 8;

    /** 호환 모듈 전용 — 유체 종류가 상태 표를 만들 때만 쓴다. */
    FluidState(Fluid owner, Map<Property<?>, Comparable<?>> values) { super(owner, values); }

    @Override
    protected FluidState withValue(Property<?> property, Comparable<?> value) { return owner.furnaceStateWith(this, property, value); }

    @Override
    public FluidState furnaceDefaultState() { return owner.defaultFluidState(); }

    public Fluid getType() { return owner; }

    public boolean isSource() { return owner.isSource(this); }

    public boolean isSourceOfType(Fluid fluid) { return owner == fluid && owner.isSource(this); }

    public boolean isEmpty() { return owner.isEmpty(); }

    public float getOwnHeight() { return owner.getOwnHeight(this); }

    public int getAmount() { return owner.getAmount(this); }

    public boolean isRandomlyTicking() { return owner.isRandomlyTicking(); }

    public BlockState createLegacyBlock() { return owner.createLegacyState(this); }

    public boolean is(TagKey<Fluid> tag) { return owner.builtInRegistryHolder().is(tag); }

    public boolean is(HolderSet<Fluid> set) { return set.contains(owner.builtInRegistryHolder()); }

    public boolean is(Fluid fluid) { return owner == fluid; }

    public float getExplosionResistance() { return owner.getExplosionResistance(); }

    public Holder<Fluid> holder() { return owner.builtInRegistryHolder(); }

    public Stream<TagKey<Fluid>> getTags() { return owner.builtInRegistryHolder().tags(); }

    /**
     * 바닐라 {@code FluidState#tick(level, pos)}: 그 칸에서 이 유체 값으로 흐르는 유체 예약 틱을 한 번 돈다(퍼지기·마르기 — 서버의 FlowingFluid 이식).
     * 빈 유체는 아무것도 하지 않는다. 그 칸의 주인 region 스레드에서만(Level#setBlock 과 같다).
     */
    public void tick(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        if (isEmpty()) return;
        boolean falling = hasProperty(FlowingFluid.FALLING) && getValue(FlowingFluid.FALLING);
        String key = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(owner).toString();
        level.furnaceScheduledTick(pos, new org.domi.furnace.api.world.FluidData(key, getAmount(), falling), null, null);
    }
}
