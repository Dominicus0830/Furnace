package org.domi.furnace.compat.nms;

import io.netty.buffer.ByteBuf;
import java.util.function.Function;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.domi.furnace.api.item.ItemSupport;
import org.domi.furnace.api.item.ItemComponentWireValue;

/**
 * 데이터 컴포넌트 값 하나의 네트워크 코덱(바닐라 {@code DataComponentType.streamCodec()}). 쓰기·읽기 규칙은 서버의 바닐라 컴포넌트 코덱이고,
 * shim 은 값 ↔ 저장 꼴 SNBT 변환만 맡는다.
 */
public final class NmsComponentWire {

    private NmsComponentWire() {}

    public static <T> StreamCodec<RegistryFriendlyByteBuf, T> codec(String key, Function<T, String> toSnbt, Function<String, T> fromSnbt) {
        return new StreamCodec<>() {
            @Override
            public T decode(RegistryFriendlyByteBuf buf) {
                ItemComponentWireValue v = ItemSupport.get().decodeComponentValue(key, buf.nioBuffer(buf.readerIndex(), buf.readableBytes()));
                buf.skipBytes(v.length());
                return fromSnbt.apply(v.snbt());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, T value) {
                buf.writeBytes(ItemSupport.get().encodeComponentValue(key, toSnbt.apply(value)));
            }
        };
    }
}
