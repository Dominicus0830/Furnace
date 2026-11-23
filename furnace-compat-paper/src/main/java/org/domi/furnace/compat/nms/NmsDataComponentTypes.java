package org.domi.furnace.compat.nms;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import org.domi.furnace.api.Furnace;

/**
 * 데이터 컴포넌트 종류 레지스트리 shim(Geyser 가 번호 ↔ 종류 표로 쓴다). 번호 순서는 서버 내장 레지스트리 목록(바닐라 번호 = 네트워크 번호)이고,
 * 종류 객체는 {@link DataComponents#furnaceType} 의 것(상수와 같은 객체)이다. 기본값 없는 레지스트리다.
 */
public final class NmsDataComponentTypes {

    private NmsDataComponentTypes() {}

    private static volatile NmsMappedRegistry<DataComponentType<?>> registry;

    public static Registry<DataComponentType<?>> registry() {
        NmsMappedRegistry<DataComponentType<?>> r = registry;
        if (r == null) {
            synchronized (NmsDataComponentTypes.class) {
                r = registry;
                if (r == null) {
                    r = new NmsMappedRegistry<>(Registries.DATA_COMPONENT_TYPE);
                    var ids = Furnace.server().gameRegistries().builtIn("minecraft:data_component_type");
                    if (ids.isEmpty()) throw new IllegalStateException("the server did not provide the data component type registry list");
                    for (String id : ids) r.add(ResourceLocation.parse(id), DataComponents.furnaceRegister(id));
                    registry = r;
                }
            }
        }
        return r;
    }
}
