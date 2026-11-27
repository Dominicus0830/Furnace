package org.domi.furnace.compat.nms;

import java.util.List;
import java.util.Locale;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageEffects;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DeathMessageType;
import org.domi.furnace.api.Furnace;

/**
 * 피해 형식 레지스트리 shim(데이터 팩 레지스트리 {@code minecraft:damage_type}). 번호 순서는 서버가 클라에 보내는 순서({@code entries}),
 * 값은 서버 정의({@code damageType}). 태그는 {@link NmsMappedRegistry} 가 서버 태그를 읽는다. 서버·항목 목록이 바뀌면 다시 만든다({@link NmsVariants#wolves()} 와 같은 방식).
 */
public final class NmsDamageTypes {

    private NmsDamageTypes() {}

    private record Synced(Furnace server, List<String> ids, Registry<DamageType> registry) {}

    private static volatile Synced cache;

    public static Registry<DamageType> registry() {
        Furnace server = Furnace.server();
        var regs = server.gameRegistries();
        var ids = regs.entries("minecraft:damage_type");
        var c = cache;
        if (c != null && c.server() == server && c.ids().equals(ids)) return c.registry();
        synchronized (NmsDamageTypes.class) {
            c = cache;
            if (c != null && c.server() == server && c.ids().equals(ids)) return c.registry();
            NmsMappedRegistry<DamageType> r = new NmsMappedRegistry<>(Registries.DAMAGE_TYPE);
            for (String id : ids) {
                var d = regs.damageType(id).orElseThrow(() -> new IllegalStateException("the server has no definition for damage type " + id));
                r.add(ResourceLocation.parse(id), new DamageType(d.msgId(), named(DamageScaling.values(), d.scaling()), d.exhaustion(),
                        named(DamageEffects.values(), d.effects()), named(DeathMessageType.values(), d.deathMessageType())));
            }
            cache = new Synced(server, ids, r);
            return r;
        }
    }

    private static <E extends Enum<E> & net.minecraft.util.StringRepresentable> E named(E[] all, String name) {
        for (E e : all) if (e.getSerializedName().equals(name.toLowerCase(Locale.ROOT))) return e;
        throw new IllegalStateException("unknown damage type value " + name);
    }
}
