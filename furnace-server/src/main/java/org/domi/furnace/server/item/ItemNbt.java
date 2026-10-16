// 저장 파일의 아이템 컴포넌트: 바닐라 "components" compound(DataComponentPatch.CODEC + NbtOps).
// 초기 Furnace 가 남긴 네트워크 바이트("furnace:components"/"Comp")도 읽어 옮긴다.
package org.domi.furnace.server.item;

import io.netty.buffer.Unpooled;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ItemNbt {
    private ItemNbt() {}

    private static final Logger log = LoggerFactory.getLogger(ItemNbt.class);

    public static final String KEY = "components";
    /** 초기 Furnace 형식(네트워크 바이트). 읽기만 한다. */
    public static final String LEGACY_KEY = "furnace:components";

    /** 패치 → "components" 값(비었으면 null). */
    public static NbtCompound write(ComponentPatch patch) {
        if (patch == null || patch.isEmpty()) return null;
        NbtCompound c = patch.toNbt();
        return c.tags().isEmpty() ? null : c;
    }

    /**
     * 아이템 항목에서 컴포넌트 패치를 읽는다. 바닐라는 잘못된 아이템을 오류 로그와 함께 버린다 — 그때 null.
     *
     * @param legacyKey 초기 형식 바이트가 들어 있을 수 있는 키(없으면 null)
     */
    public static ComponentPatch read(NbtCompound entry, String item, String legacyKey) {
        Object comps = entry.tags().get(KEY);
        try {
            if (comps != null) return ComponentPatch.fromCodec(comps).normalizedFor(item);
            if (legacyKey != null && entry.tags().get(legacyKey) instanceof byte[] b && b.length > 0 && ItemContext.installed()) {
                return ComponentPatch.read(Unpooled.wrappedBuffer(b)).normalizedFor(item);
            }
            return ComponentPatch.EMPTY;
        } catch (CodecException | IndexOutOfBoundsException e) {
            log.error("Tried to load invalid item: '{}' ({})", item, e.getMessage());
            return null;
        }
    }
}
