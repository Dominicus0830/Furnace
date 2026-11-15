package org.domi.furnace.compat.nms;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.FurnaceStructure;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.domi.furnace.api.Furnace;

/**
 * 월드젠 레지스트리 shim(N2b-6): 설정된 지형지물·배치된 지형지물·구조물. 항목 이름과 종류는 서버가 가진 월드젠 데이터({@code GameRegistries#worldgen})에서 읽는다 —
 * 키 목록은 바닐라 1.21.1 과 같다. 값은 식별용 껍데기다(종류별 {@link Feature} 객체·{@link PlacedFeature#feature()} 가 가리키는 같은 설정된 지형지물 손잡이).
 * 번호는 이름 순이다(바닐라는 데이터 팩 읽는 순서 — 지형지물·구조물은 번호를 쓰는 곳이 없다). 서버마다 한 번 만든다.
 */
public final class NmsWorldgen {

    private NmsWorldgen() {}

    private record Built(Furnace server, Registry<ConfiguredFeature<?, ?>> configured, Registry<PlacedFeature> placed, Registry<Structure> structures) {}

    private static volatile Built built;

    private static Built built() {
        var server = Furnace.server();
        Built b = built;
        if (b != null && b.server == server) return b;
        synchronized (NmsWorldgen.class) {
            b = built;
            if (b != null && b.server == server) return b;
            var g = server.gameRegistries();

            NmsMappedRegistry<ConfiguredFeature<?, ?>> cf = new NmsMappedRegistry<>(Registries.CONFIGURED_FEATURE);
            for (Map.Entry<String, String> e : g.worldgen("minecraft:worldgen/configured_feature").entrySet()) {
                cf.add(ResourceLocation.parse(e.getKey()), new ConfiguredFeature<>(Feature.byType(e.getValue()), null));
            }

            NmsMappedRegistry<PlacedFeature> pf = new NmsMappedRegistry<>(Registries.PLACED_FEATURE);
            for (Map.Entry<String, String> e : g.worldgen("minecraft:worldgen/placed_feature").entrySet()) {
                Holder<ConfiguredFeature<?, ?>> target = e.getValue().isEmpty() ? null : cf.getHolder(ResourceLocation.parse(e.getValue())).orElse(null);
                if (target == null) target = Holder.direct(new ConfiguredFeature<>(Feature.byType("minecraft:unknown"), null));   // 직접 적은 지형지물
                pf.add(ResourceLocation.parse(e.getKey()), new PlacedFeature(target));
            }

            NmsMappedRegistry<Structure> st = new NmsMappedRegistry<>(Registries.STRUCTURE);
            for (Map.Entry<String, String> e : g.worldgen("minecraft:worldgen/structure").entrySet()) {
                st.add(ResourceLocation.parse(e.getKey()), new FurnaceStructure(e.getValue()));
            }
            built = b = new Built(server, cf, pf, st);
            return b;
        }
    }

    public static Registry<ConfiguredFeature<?, ?>> configuredFeatures() { return built().configured; }

    public static Registry<PlacedFeature> placedFeatures() { return built().placed; }

    public static Registry<Structure> structures() { return built().structures; }
}
