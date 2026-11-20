package org.domi.furnace.compat.nms;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 상태의 속성 값 코덱(N2b-6) — 바닐라 {@code StateDefinition} 이 만드는 {@code StateHolder.propertiesCodec} 와 같은 결과를 내는 진짜 {@code MapCodec}.
 * 바닐라처럼 속성마다 이름을 키로 하는 문자열 항목이고, 입력에 없는 속성·문자열이 아닌 값·읽을 수 없는 값은 모두 주인의 기본 상태 값이다
 * (바닐라 {@code fieldOf(name).orElseGet(.., 기본값)} — 오류 결과를 내지 않는다). 속성 순서는 이름순(바닐라 정의의 순서).
 *
 * <p>DataFixerUpper 타입을 쓰므로 이 클래스는 {@link NmsDfu#visible()} 가 참일 때만 로드한다({@code StateHolder} 가 확인한다). {@code @Override} 를 붙이지 않은 것은
 * 컴파일 대역의 {@code MapCodec} 이 추상 메서드를 갖지 않아서일 뿐, 서술자는 진짜 DFU 8.0.16 의 {@code keys}·{@code decode}·{@code encode} 와 같다
 * (S 를 한정하지 않아 지워진 서술자가 {@code (Object, DynamicOps, RecordBuilder)} 로 맞는다).
 */
public final class NmsStateCodec<S> extends MapCodec<S> {

    /** 이 상태와 같은 주인의 상태들을 읽고 쓴다(주인의 기본 상태는 처음 쓸 때 묻는다 — 상태 표를 만드는 도중에는 아직 없다). */
    private final StateHolder<?, ?> holder;

    private volatile List<Property<?>> properties;

    private NmsStateCodec(StateHolder<?, ?> holder) { this.holder = holder; }

    /** 호환 모듈 전용: {@code StateHolder} 가 만든다. */
    public static <S> MapCodec<S> of(StateHolder<?, ?> holder) { return new NmsStateCodec<>(holder); }

    /** 호환 모듈 전용: 맵 단계에서 바로 읽고 쓰는 쪽({@link NmsCodecs#blockState}). */
    static <S> NmsStateCodec<S> direct(StateHolder<?, ?> holder) { return new NmsStateCodec<>(holder); }

    private List<Property<?>> properties() {
        List<Property<?>> p = properties;
        if (p == null) {
            p = new ArrayList<>(holder.getProperties());
            p.sort(Comparator.comparing(Property::getName));
            properties = p;
        }
        return p;
    }

    public <T> Stream<T> keys(DynamicOps<T> ops) {
        return properties().stream().map(p -> ops.createString(p.getName()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> DataResult<S> decode(DynamicOps<T> ops, MapLike<T> input) {
        StateHolder state = (StateHolder) (Object) holder.furnaceDefaultState();
        for (Property<?> p : properties()) {
            T raw = input.get(p.getName());
            if (raw == null) continue;   // 없는 속성은 기본 상태의 값
            // 바닐라 appendPropertyCodec 는 fieldOf(name).orElseGet(..., 기본값) — 문자열이 아니거나 읽을 수 없는 값도 오류가 아니라 기본 상태의 값이다(검토 N2)
            String text = ops.getStringValue(raw).result().orElse(null);
            if (text == null) continue;
            var value = p.getValue(text);
            if (value.isEmpty()) continue;
            state = (StateHolder) state.setValue((Property) p, (Comparable) value.get());
        }
        return DataResult.success((S) state);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> RecordBuilder<T> encode(S input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
        StateHolder state = (StateHolder) input;
        for (Property<?> p : properties()) {
            prefix = prefix.add(p.getName(), ops.createString(((Property) p).getName((Comparable) state.getValue((Property) p))));
        }
        return prefix;
    }

    @Override
    public String toString() { return "PropertiesCodec[" + holder.getClass().getSimpleName() + "]"; }
}
