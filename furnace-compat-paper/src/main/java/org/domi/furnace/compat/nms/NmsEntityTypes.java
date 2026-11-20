package org.domi.furnace.compat.nms;

import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.domi.furnace.api.Furnace;

/**
 * 엔티티 종류 레지스트리 shim(NMS shim 5e — voxy 가 먼 플레이어의 탈것 종류 이름을 읽는다). 서버의 내장 레지스트리 목록({@code builtIn("minecraft:entity_type")},
 * 바닐라 번호 순)에서 처음 쓸 때 한 번 만든다. 값은 {@link EntityType#of} 의 같은 객체라 {@code Entity.getType()} 과 {@code ==} 로 비교된다. 기본값은 돼지(바닐라).
 */
public final class NmsEntityTypes {

    private NmsEntityTypes() {}

    private static volatile NmsDefaultedRegistry<EntityType<?>> registry;

    public static DefaultedRegistry<EntityType<?>> registry() {
        NmsDefaultedRegistry<EntityType<?>> r = registry;
        if (r == null) {
            synchronized (NmsEntityTypes.class) {
                r = registry;
                if (r == null) {
                    r = new NmsDefaultedRegistry<>(Registries.ENTITY_TYPE, ResourceLocation.withDefaultNamespace("pig"));
                    var ids = Furnace.server().gameRegistries().builtIn("minecraft:entity_type");
                    if (ids.isEmpty()) throw new IllegalStateException("the server did not provide the entity type registry list");
                    for (String id : ids) r.add(ResourceLocation.parse(id), EntityType.of(ResourceLocation.parse(id)));
                    registry = r;
                }
            }
        }
        return r;
    }
}
