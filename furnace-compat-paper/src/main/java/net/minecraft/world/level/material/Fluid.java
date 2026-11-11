package net.minecraft.world.level.material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 유체 종류(Mojang 이름 shim, N0). 다섯 종류({@link Fluids})뿐이고 플러그인이 새로 만들 수 없다. 상태 표는 바닐라 StateDefinition 순서
 * (속성 이름순, 뒤 속성이 빨리 바뀜)이고 기본 상태는 바닐라처럼 표의 첫 상태({@code stateDefinition.any()})다.
 * 월드가 필요한 메서드(틱·흐름·높이·소리)는 월드 shim 이 없어 두지 않았다 — 부르면 H7 덫.
 */
public abstract class Fluid {

    protected final StateDefinition<Fluid, FluidState> stateDefinition;
    private final List<Property<?>> furnaceProperties;
    private final FluidState[] furnaceStates;
    private FluidState defaultFluidState;

    protected Fluid() {
        List<Property<?>> props = new ArrayList<>(furnaceStateProperties());
        props.sort((a, b) -> a.getName().compareTo(b.getName()));
        this.furnaceProperties = List.copyOf(props);
        Map<String, Property<?>> byName = new LinkedHashMap<>();
        for (Property<?> p : props) byName.put(p.getName(), p);
        List<Map<Property<?>, Comparable<?>>> combos = new ArrayList<>();
        combos.add(new LinkedHashMap<>());
        for (Property<?> p : props) {   // 앞 속성이 바깥 고리(바닐라 StateDefinition 생성자의 flatMap 순서)
            List<Map<Property<?>, Comparable<?>>> next = new ArrayList<>();
            for (Map<Property<?>, Comparable<?>> m : combos) {
                for (Comparable<?> v : p.getPossibleValues()) {
                    Map<Property<?>, Comparable<?>> c = new LinkedHashMap<>(m);
                    c.put(p, v);
                    next.add(c);
                }
            }
            combos = next;
        }
        this.furnaceStates = new FluidState[combos.size()];
        for (int i = 0; i < furnaceStates.length; i++) furnaceStates[i] = new FluidState(this, combos.get(i));
        this.stateDefinition = new StateDefinition<>(this, byName, List.of(furnaceStates));
        registerDefaultState(stateDefinition.any());
    }

    /** 호환 모듈 전용 — 이 종류가 가진 속성(바닐라 createFluidStateDefinition). */
    protected List<Property<?>> furnaceStateProperties() { return List.of(); }

    /** 호환 모듈 전용 — 한 속성만 바꾼 상태(같은 표에서 찾는다). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    FluidState furnaceStateWith(FluidState from, Property<?> property, Comparable<?> value) {
        outer:
        for (FluidState s : furnaceStates) {
            for (Property p : furnaceProperties) {
                Object want = p == property ? value : from.getValue(p);
                if (!s.getValue(p).equals(want)) continue outer;
            }
            return s;
        }
        throw new IllegalStateException("상태 표에 없는 값: " + from + " " + property + "=" + value);
    }

    public StateDefinition<Fluid, FluidState> getStateDefinition() { return stateDefinition; }

    protected final void registerDefaultState(FluidState state) { this.defaultFluidState = state; }

    public final FluidState defaultFluidState() { return defaultFluidState; }

    public abstract Item getBucket();

    protected boolean isEmpty() { return false; }

    protected boolean isRandomlyTicking() { return false; }

    protected abstract float getExplosionResistance();

    public abstract float getOwnHeight(FluidState state);

    protected abstract BlockState createLegacyState(FluidState state);

    public abstract boolean isSource(FluidState state);

    public abstract int getAmount(FluidState state);

    public boolean isSame(Fluid fluid) { return fluid == this; }

    /** @deprecated 바닐라도 FluidState.is 를 권한다. */
    @Deprecated
    public boolean is(TagKey<Fluid> tag) { return builtInRegistryHolder().is(tag); }

    public Holder.Reference<Fluid> builtInRegistryHolder() {
        ResourceLocation key = BuiltInRegistries.FLUID.getKey(this);
        return BuiltInRegistries.FLUID.getHolder(key).orElseThrow();
    }
}
