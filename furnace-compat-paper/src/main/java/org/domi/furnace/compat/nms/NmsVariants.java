package org.domi.furnace.compat.nms;

import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.CatVariant;
import net.minecraft.world.entity.animal.FrogVariant;
import org.domi.furnace.api.Furnace;

/**
 * 고양이·개구리 무늬 레지스트리 shim(엔티티 데이터 설계 D4). 번호 순서는 서버 내장 레지스트리 목록({@code builtIn("minecraft:cat_variant")} 11개,
 * {@code builtIn("minecraft:frog_variant")} 3개)이고, 그림 위치는 바닐라 1.21.1 bootstrap 과 같은 꼴(고양이 {@code textures/entity/cat/<이름>.png},
 * 개구리 {@code textures/entity/frog/<이름>_frog.png}). 기본값 없는 레지스트리다(바닐라 registerSimple).
 */
public final class NmsVariants {

    private NmsVariants() {}

    private static volatile NmsMappedRegistry<CatVariant> cats;
    private static volatile NmsMappedRegistry<FrogVariant> frogs;

    public static Registry<CatVariant> cats() {
        var r = cats;
        if (r == null) {
            synchronized (NmsVariants.class) {
                if ((r = cats) == null) cats = r = build(Registries.CAT_VARIANT, "minecraft:cat_variant",
                        p -> new CatVariant(ResourceLocation.withDefaultNamespace("textures/entity/cat/" + p + ".png")));
            }
        }
        return r;
    }

    public static Registry<FrogVariant> frogs() {
        var r = frogs;
        if (r == null) {
            synchronized (NmsVariants.class) {
                if ((r = frogs) == null) frogs = r = build(Registries.FROG_VARIANT, "minecraft:frog_variant",
                        p -> new FrogVariant(ResourceLocation.withDefaultNamespace("textures/entity/frog/" + p + "_frog.png")));
            }
        }
        return r;
    }

    private static <T> NmsMappedRegistry<T> build(ResourceKey<? extends Registry<T>> key, String name, Function<String, T> make) {
        var ids = Furnace.server().gameRegistries().builtIn(name);
        if (ids.isEmpty()) throw new IllegalStateException("the server did not provide the " + name + " registry list");
        NmsMappedRegistry<T> r = new NmsMappedRegistry<>(key);
        for (String id : ids) r.add(ResourceLocation.parse(id), make.apply(id.substring(id.indexOf(':') + 1)));
        return r;
    }

    /** 손잡이 → 레지스트리 이름. 바닐라 holderRegistry 코덱처럼 등록되지 않은 값이면 IllegalArgumentException. */
    public static <T> String name(Registry<T> registry, Holder<T> holder, String at) {
        return holder.unwrapKey().filter(k -> registry.containsKey(k)).or(() -> registry.getResourceKey(holder.value()))
                .map(k -> k.location().toString())
                .orElseThrow(() -> new IllegalArgumentException(at + ": can't find id for '" + holder.value() + "' in registry " + registry.key()));
    }

    /** 레지스트리 이름 → 등록된 손잡이(없는 이름이면 IllegalArgumentException). */
    public static <T> Holder<T> holder(Registry<T> registry, String name, String at) {
        return registry.getHolder(ResourceLocation.parse(name)).<Holder<T>>map(h -> h)
                .orElseThrow(() -> new IllegalArgumentException(at + ": no " + name + " in registry " + registry.key()));
    }
}
