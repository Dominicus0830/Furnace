package org.domi.furnace.compat.nms;

import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.domi.furnace.api.Furnace;

/**
 * 아이템 레지스트리 shim(N0). 서버의 내장 레지스트리 목록({@code GameRegistries.builtIn("minecraft:item")}, 바닐라 번호 순)에서 처음 쓸 때 한 번 만든다.
 * 값은 {@link Item#byKey} 의 같은 객체라 {@code ItemStack.getItem()} 과 {@code ==} 로 비교된다.
 */
public final class NmsItems {

    private NmsItems() {}

    private static volatile NmsDefaultedRegistry<Item> registry;

    public static DefaultedRegistry<Item> registry() {
        NmsDefaultedRegistry<Item> r = registry;
        if (r == null) {
            synchronized (NmsItems.class) {
                r = registry;
                if (r == null) {
                    r = new NmsDefaultedRegistry<>(Registries.ITEM, ResourceLocation.withDefaultNamespace("air"));
                    var ids = Furnace.server().gameRegistries().builtIn("minecraft:item");
                    if (ids.isEmpty()) throw new IllegalStateException("서버가 아이템 레지스트리 목록을 주지 않았다");
                    for (String id : ids) r.add(ResourceLocation.parse(id), Item.byKey(id));
                    registry = r;
                }
            }
        }
        return r;
    }

    /** 이름의 아이템. 모르면 공기(바닐라 DefaultedRegistry.get 과 같다). */
    public static Item get(String key) { return registry().get(ResourceLocation.parse(key)); }
}
