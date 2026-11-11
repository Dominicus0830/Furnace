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
 * 월드가 필요한 메서드(getHeight·getFlow·tick·shouldRenderBackwardUpFace)는 두지 않았다.
 */
public final class FluidState extends StateHolder<Fluid, FluidState> {

    public static final int AMOUNT_MAX = 9;
    public static final int AMOUNT_FULL = 8;

    /** 호환 모듈 전용 — 유체 종류가 상태 표를 만들 때만 쓴다. */
    FluidState(Fluid owner, Map<Property<?>, Comparable<?>> values) { super(owner, values); }

    @Override
    protected FluidState withValue(Property<?> property, Comparable<?> value) { return owner.furnaceStateWith(this, property, value); }

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
}
