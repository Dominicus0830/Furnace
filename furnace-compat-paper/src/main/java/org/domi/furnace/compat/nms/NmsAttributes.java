package org.domi.furnace.compat.nms;

import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.domi.furnace.api.Furnace;

/**
 * 속성 레지스트리 shim(NMS shim 3번 묶음). 번호 순서는 서버 내장 레지스트리 목록({@code builtIn("minecraft:attribute")}, 31개)이고, 기본값·범위는
 * Furnace 공개 API {@link org.domi.furnace.api.entity.attribute.Attribute}(서버 속성 표와 같다 — AttributeApiTest), 설명 키는 바닐라
 * {@code "attribute.name." + 경로}, 클라이언트 동기 여부는 바닐라 1.21.1 Attributes 정의다. 기본값 없는 레지스트리다.
 */
public final class NmsAttributes {

    private NmsAttributes() {}

    /** 바닐라 1.21.1 에서 setSyncable(true) 를 부르지 않는 속성. */
    private static final Set<String> NOT_SYNCED = Set.of("minecraft:generic.attack_damage", "minecraft:generic.attack_knockback",
            "minecraft:generic.follow_range", "minecraft:generic.knockback_resistance", "minecraft:zombie.spawn_reinforcements");

    private static volatile NmsMappedRegistry<Attribute> registry;

    public static Registry<Attribute> registry() { return mapped(); }

    private static NmsMappedRegistry<Attribute> mapped() {
        NmsMappedRegistry<Attribute> r = registry;
        if (r == null) {
            synchronized (NmsAttributes.class) {
                r = registry;
                if (r == null) {
                    r = new NmsMappedRegistry<>(Registries.ATTRIBUTE);
                    var ids = Furnace.server().gameRegistries().builtIn("minecraft:attribute");
                    if (ids.isEmpty()) throw new IllegalStateException("the server did not provide the attribute registry list");
                    for (String id : ids) {
                        var a = org.domi.furnace.api.entity.attribute.Attribute.find(id)
                                .orElseThrow(() -> new IllegalStateException("server attribute without a definition: " + id));
                        String path = id.substring(id.indexOf(':') + 1);
                        r.add(ResourceLocation.parse(id), new RangedAttribute("attribute.name." + path, a.defaultValue(), a.minValue(), a.maxValue())
                                .setSyncable(!NOT_SYNCED.contains(id)));
                    }
                    registry = r;
                }
            }
        }
        return r;
    }

    /** Attributes 상수용: 이름의 등록된 손잡이. */
    public static Holder<Attribute> holder(String id) {
        return mapped().getHolder(ResourceLocation.parse(id)).orElseThrow(() -> new IllegalStateException("name missing from the server attribute registry: " + id));
    }

    /** 손잡이 → Furnace 속성(등록되지 않은 손잡이는 IllegalArgumentException — 플러그인이 직접 만든 속성은 서버에 없다). */
    public static org.domi.furnace.api.entity.attribute.Attribute furnace(Holder<Attribute> holder) {
        String id = holder.unwrapKey().map(k -> k.location().toString())
                .orElseGet(() -> mapped().getResourceKey(holder.value()).map(k -> k.location().toString()).orElse(null));
        if (id == null) throw new IllegalArgumentException("attribute not registered on the server: " + holder);
        return org.domi.furnace.api.entity.attribute.Attribute.of(id);
    }
}
