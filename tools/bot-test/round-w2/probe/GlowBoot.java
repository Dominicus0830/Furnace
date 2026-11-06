// 부트스트랩에서 바이옴 둘과 차원 타입 하나를 등록한다(W2 봇 시험).
package probe;

import org.domi.furnace.api.plugin.bootstrap.BootstrapContext;
import org.domi.furnace.api.plugin.bootstrap.PluginBootstrap;
import org.domi.furnace.api.plugin.lifecycle.LifecycleEvents;
import org.domi.furnace.api.worldgen.BiomeDefinition;
import org.domi.furnace.api.worldgen.DimensionTypeDefinition;
import org.domi.furnace.api.worldgen.SpawnCategory;

public final class GlowBoot implements PluginBootstrap {
    @Override
    public void bootstrap(BootstrapContext ctx) {
        ctx.lifecycle().register(LifecycleEvents.GAME_REGISTRIES, r -> {
            r.registerBiome("cave", BiomeDefinition.builder().temperature(0.1f).downfall(0.9f).skyColor(0x112233).waterColor(0x22ccaa)
                    .fogColor(0x223344).waterFogColor(0x001122).grassColor(0x44ff88).foliageColor(0x33dd66)
                    .grassColorModifier(BiomeDefinition.GrassColorModifier.SWAMP)
                    .ambientParticle("minecraft:ash", 0.02f).ambientSound("glow:ambient.hum")
                    .moodSound(BiomeDefinition.MoodSound.of("minecraft:ambient.cave"))
                    .additionsSound(new BiomeDefinition.AdditionsSound("minecraft:ambient.basalt_deltas.additions", 0.0111))
                    .music(new BiomeDefinition.Music("minecraft:music.overworld.deep_dark", 1200, 3600, false))
                    .spawner(SpawnCategory.CREATURE, "minecraft:rabbit", 7, 2, 3).build());
            r.registerBiome("plain", BiomeDefinition.builder().build());
            r.registerDimensionType("deep", DimensionTypeDefinition.overworld().heightRange(0, 128).fixedTime(6000).ambientLight(0.5f).build());
        });
    }
}
