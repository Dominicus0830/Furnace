// 데이터 컴포넌트 종류(바닐라 DataComponentType): 이름, 저장 코덱(없으면 저장하지 않는 종류), 네트워크 코덱.
// 네트워크 코덱을 따로 정하지 않은 종류는 바닐라처럼 저장 코덱을 NBT 로 실어 보낸다(ByteBufCodecs.fromCodecWithRegistries).
package org.domi.furnace.server.item;

import io.netty.buffer.ByteBuf;

public final class ComponentType {

    private final String name;
    private final PCodec persistent;
    private final NCodec network;

    ComponentType(String name, PCodec persistent, NCodec network) {
        this.name = name;
        this.persistent = persistent;
        this.network = network != null ? network : nbtOf(persistent);
    }

    public String name() { return name; }

    /** 저장 코덱(바닐라 codec()). 저장하지 않는 종류(transient)면 null. */
    public PCodec persistent() { return persistent; }

    public NCodec network() { return network; }

    public boolean isTransient() { return persistent == null; }

    private java.util.function.BiPredicate<Object, Object> equality = Val::same;
    private java.util.function.ToIntFunction<Object> hasher = Val::hash;

    /** 바닐라 값 객체의 equals 가 구조 비교와 다를 때(ItemStack 참조 비교, 숨은 효과 무시 등). */
    ComponentType equality(java.util.function.BiPredicate<Object, Object> eq, java.util.function.ToIntFunction<Object> hash) {
        this.equality = eq;
        this.hasher = hash;
        return this;
    }

    /** 바닐라 값 equals 대응. */
    public boolean same(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return equality.test(a, b);
    }

    public int hash(Object v) { return v == null ? 0 : hasher.applyAsInt(v); }

    /** 어떤 꼴의 값이든 정규 값으로. 저장 코덱이 없는 종류는 이미 정규 값이라고 본다. */
    public Object normalize(Object v) { return persistent != null ? persistent.normalize(v) : v; }

    private static NCodec nbtOf(PCodec p) {
        if (p == null) throw new IllegalArgumentException("코덱이 하나도 없는 컴포넌트");
        return new NCodec() {
            public Object read(ByteBuf buf) {
                Object tag = NbtVals.readNetwork(buf);
                if (tag == null) throw new CodecException("Missing component value");
                return p.normalize(tag);
            }
            public void write(ByteBuf buf, Object v) { NbtVals.writeNetwork(buf, p.toNbt(v)); }
        };
    }

    @Override
    public String toString() { return name; }
}
