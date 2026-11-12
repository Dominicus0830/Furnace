package net.minecraft.network.codec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 번호 → 코덱 목록 shim(Mojang 이름, N3 1단계). 패킷 등록부를 읽는 플러그인(ProtocolLib 등)이 {@code byId} 목록과
 * {@link Entry} 의 {@code type()}·{@code serializer()} 로 "번호 ↔ 패킷 종류"를 얻는다. 목록의 인덱스가 와이어 번호다.
 * 바닐라는 번호 조회에 fastutil 지도를 쓰지만 컴파일 경로에 없어 일반 지도로 대신한다.
 */
public class IdDispatchCodec<B extends io.netty.buffer.ByteBuf, V, T> implements StreamCodec<B, V> {

    private static final int UNKNOWN_TYPE = -1;

    private final Function<V, ? extends T> typeGetter;
    private final List<Entry<B, V, T>> byId;
    private final Map<T, Integer> toId;

    IdDispatchCodec(Function<V, ? extends T> typeGetter, List<Entry<B, V, T>> byId, Map<T, Integer> toId) {
        this.typeGetter = typeGetter;
        this.byId = byId;
        this.toId = toId;
    }

    public static <B extends io.netty.buffer.ByteBuf, V, T> Builder<B, V, T> builder(Function<V, ? extends T> typeGetter) {
        return new Builder<>(typeGetter);
    }

    /** 번호 → 종류. 없으면 null. */
    public T typeOf(int id) { return id >= 0 && id < byId.size() ? byId.get(id).type() : null; }

    /** 종류 → 번호. 없으면 -1. */
    public int idOf(T type) { return toId.getOrDefault(type, UNKNOWN_TYPE); }

    @Override
    public V decode(B input) {
        throw new net.minecraft.network.codec.IdDispatchCodec.Unsupported("decode");
    }

    @Override
    public void encode(B output, V value) {
        throw new net.minecraft.network.codec.IdDispatchCodec.Unsupported("encode");
    }

    /** 이 단계의 코덱은 등록부 모양만 있다(패킷 바이트 변환은 다음 단계). */
    static final class Unsupported extends org.domi.furnace.compat.nms.UnsupportedNmsCall {
        Unsupported(String what) { super("net.minecraft.network.codec.IdDispatchCodec#" + what, "패킷 바이트 변환은 아직 연결되지 않았다(ProtocolLib 다리 3단계)"); }
    }

    record Entry<B, V, T>(StreamCodec<? super B, ? extends V> serializer, T type) {}

    public static class Builder<B extends io.netty.buffer.ByteBuf, V, T> {
        private final List<Entry<B, V, T>> entries = new ArrayList<>();
        private final Function<V, ? extends T> typeGetter;

        Builder(Function<V, ? extends T> typeGetter) { this.typeGetter = typeGetter; }

        public Builder<B, V, T> add(T type, StreamCodec<? super B, ? extends V> serializer) {
            entries.add(new Entry<>(serializer, type));
            return this;
        }

        public IdDispatchCodec<B, V, T> build() {
            Map<T, Integer> toId = new HashMap<>();
            for (int i = 0; i < entries.size(); i++) {
                if (toId.putIfAbsent(entries.get(i).type(), i) != null) throw new IllegalStateException("Duplicate registration for type " + entries.get(i).type());
            }
            return new IdDispatchCodec<>(typeGetter, List.copyOf(entries), Map.copyOf(toId));
        }
    }
}
