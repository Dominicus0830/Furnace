package org.domi.furnace.compat.nms;

import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.animal.CatVariant;
import net.minecraft.world.entity.animal.FrogVariant;
import net.minecraft.world.entity.animal.WolfVariant;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.level.biome.Biome;
import org.domi.furnace.api.Furnace;

/**
 * 고양이·개구리 무늬 레지스트리 shim(엔티티 데이터 설계 D4)과 늑대·그림 무늬 데이터 팩 레지스트리 shim(D4-3, {@link #wolves()}·{@link #paintings()}). 번호 순서는 서버 내장 레지스트리 목록({@code builtIn("minecraft:cat_variant")} 11개,
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

    /** 데이터 팩 레지스트리 하나를 만든 서버와 함께 둔다(서버 시작 때 고정되므로 서버마다 한 번). */
    private record Synced<T>(Furnace server, Registry<T> registry) {}

    private static volatile Synced<WolfVariant> wolves;
    private static volatile Synced<PaintingVariant> paintings;

    /**
     * 늑대 무늬 레지스트리(데이터 팩 레지스트리, 바닐라 registryAccess 의 {@code minecraft:wolf_variant}). 번호 순서는 서버가 클라에 보내는 순서
     * ({@code entries}), 값은 서버 정의({@code wolfVariant}) — 데이터 팩·플러그인 변종도 담는다. 바이옴 묶음은 태그면
     * {@link NmsBiomes#registry()} 의 이름 묶음, 아니면 그 레지스트리의 손잡이 목록이다.
     */
    public static Registry<WolfVariant> wolves() {
        Furnace server = Furnace.server();
        var c = wolves;
        if (c != null && c.server() == server) return c.registry();
        synchronized (NmsVariants.class) {
            c = wolves;
            if (c != null && c.server() == server) return c.registry();
            var regs = server.gameRegistries();
            NmsMappedRegistry<WolfVariant> r = new NmsMappedRegistry<>(Registries.WOLF_VARIANT);
            for (String id : regs.entries("minecraft:wolf_variant")) {
                var d = regs.wolfVariant(id).orElseThrow(() -> new IllegalStateException("the server has no definition for wolf variant " + id));
                r.add(ResourceLocation.parse(id), new WolfVariant(ResourceLocation.parse(d.wildTexture()), ResourceLocation.parse(d.tameTexture()),
                        ResourceLocation.parse(d.angryTexture()), biomes(d.biomes())));
            }
            wolves = new Synced<>(server, r);
            return r;
        }
    }

    /** 그림 무늬 레지스트리(데이터 팩 레지스트리 {@code minecraft:painting_variant}). 순서·값은 {@link #wolves()} 와 같은 방식. */
    public static Registry<PaintingVariant> paintings() {
        Furnace server = Furnace.server();
        var c = paintings;
        if (c != null && c.server() == server) return c.registry();
        synchronized (NmsVariants.class) {
            c = paintings;
            if (c != null && c.server() == server) return c.registry();
            var regs = server.gameRegistries();
            NmsMappedRegistry<PaintingVariant> r = new NmsMappedRegistry<>(Registries.PAINTING_VARIANT);
            for (String id : regs.entries("minecraft:painting_variant")) {
                var d = regs.paintingVariant(id).orElseThrow(() -> new IllegalStateException("the server has no definition for painting variant " + id));
                r.add(ResourceLocation.parse(id), new PaintingVariant(d.width(), d.height(), ResourceLocation.parse(d.assetId())));
            }
            paintings = new Synced<>(server, r);
            return r;
        }
    }

    /**
     * 바이옴 묶음 문자열(태그 "#id" 하나 또는 id 목록) → HolderSet. 서버가 받아들인 정의라 바닐라 코덱을 거친 꼴이다.
     * 바이옴 레지스트리에 없는 id 는 바닐라라면 정의를 읽을 때 거부되므로 여기 오지 않는다고 본다(가정) — 오면 그 id 만 뺀다.
     */
    static HolderSet<Biome> biomes(java.util.List<String> set) {
        Registry<Biome> biomes = NmsBiomes.registry();
        if (set.size() == 1 && set.get(0).startsWith("#")) {
            return biomes.getOrCreateTag(TagKey.create(Registries.BIOME, ResourceLocation.parse(set.get(0).substring(1))));
        }
        java.util.List<Holder<Biome>> out = new java.util.ArrayList<>(set.size());
        for (String id : set) biomes.getHolder(ResourceLocation.parse(id)).ifPresent(out::add);
        return HolderSet.direct(out);
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
