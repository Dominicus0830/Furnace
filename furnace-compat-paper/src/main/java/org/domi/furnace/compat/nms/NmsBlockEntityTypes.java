package org.domi.furnace.compat.nms;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.domi.furnace.api.Furnace;

/**
 * 블록 엔티티 종류 레지스트리 shim(T-182). 번호 순서는 서버 내장 표({@code builtIn("minecraft:block_entity_type")} — 바닐라 1.21.1 등록 순서,
 * 패킷 코덱과 같은 표). 기본값 없는 레지스트리다.
 */
public final class NmsBlockEntityTypes {

    private NmsBlockEntityTypes() {}

    private static volatile NmsMappedRegistry<BlockEntityType<?>> registry;

    public static Registry<BlockEntityType<?>> registry() { return mapped(); }

    /** 이름 → 종류(모르면 IllegalArgumentException — 어디서 왔는지 at 에 적는다). */
    public static BlockEntityType<?> byName(String name, String at) {
        BlockEntityType<?> t = mapped().get(ResourceLocation.parse(name));
        if (t == null) throw new IllegalArgumentException(at + " 은 모르는 블록 엔티티 종류다: " + name);
        return t;
    }

    /** 종류 → 이름(레지스트리에 없는 것이면 IllegalArgumentException). */
    public static String name(BlockEntityType<?> type, String at) {
        ResourceLocation k = mapped().getKey(type);
        if (k == null) throw new IllegalArgumentException(at + " 은 레지스트리에 없는 블록 엔티티 종류다: " + type);
        return k.toString();
    }

    private static NmsMappedRegistry<BlockEntityType<?>> mapped() {
        NmsMappedRegistry<BlockEntityType<?>> r = registry;
        if (r == null) {
            synchronized (NmsBlockEntityTypes.class) {
                r = registry;
                if (r == null) {
                    r = new NmsMappedRegistry<>(Registries.BLOCK_ENTITY_TYPE);
                    var ids = Furnace.server().gameRegistries().builtIn("minecraft:block_entity_type");
                    if (ids.isEmpty()) throw new IllegalStateException("서버가 블록 엔티티 종류 레지스트리 목록을 주지 않았다");
                    for (String id : ids) {
                        ResourceLocation loc = ResourceLocation.parse(id);
                        r.add(loc, new BlockEntityType<>(loc));
                    }
                    registry = r;
                }
            }
        }
        return r;
    }
}
