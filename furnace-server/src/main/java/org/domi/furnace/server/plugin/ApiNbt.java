// 플러그인 API NBT(org.domi.furnace.api.nbt) ↔ 서버 NBT(net.io.nbt) 변환. 값 모형이 같아 깊은 복사만 한다(배열도 복사 — 서로 공유하지 않는다).
package org.domi.furnace.server.plugin;

import org.domi.furnace.server.net.io.nbt.NbtArray;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.io.nbt.NbtTags;

public final class ApiNbt {
    private ApiNbt() {}

    public static org.domi.furnace.api.nbt.NbtCompound toApi(NbtCompound c) {
        var out = new org.domi.furnace.api.nbt.NbtCompound();
        for (var e : c.tags().entrySet()) out.put(e.getKey(), toApiValue(e.getValue()));
        return out;
    }

    static Object toApiValue(Object v) {
        return switch (v) {
            case NbtCompound c -> toApi(c);
            case NbtList l -> {
                var out = new org.domi.furnace.api.nbt.NbtList();
                for (Object o : l.items()) out.add(toApiValue(o));
                yield out;
            }
            case NbtArray a -> a.toArray();
            case byte[] a -> a.clone();
            case int[] a -> a.clone();
            case long[] a -> a.clone();
            default -> v;
        };
    }

    public static NbtCompound toServer(org.domi.furnace.api.nbt.NbtCompound c) {
        NbtCompound out = new NbtCompound();
        for (var e : c.entries()) out.put(e.getKey(), toServerValue(e.getValue()));
        return out;
    }

    static Object toServerValue(Object v) {
        return switch (v) {
            case org.domi.furnace.api.nbt.NbtCompound c -> toServer(c);
            case org.domi.furnace.api.nbt.NbtList l -> {
                NbtList out = new NbtList(l.isEmpty() ? (byte) 0 : (byte) l.elementType().id());
                for (Object o : l.values()) out.add(toServerValue(o));
                yield out;
            }
            case byte[] a -> a.clone();
            case int[] a -> a.clone();
            case long[] a -> a.clone();
            default -> {
                NbtTags.typeOf(v);   // 숫자·문자열만 남는다
                yield v;
            }
        };
    }
}
