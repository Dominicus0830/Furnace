// W2: 플러그인 바이옴·차원 타입 등록 — 바닐라 뒤에 붙는 순서, 키 규칙, 전송 NBT, 날씨·스폰 상태 켜기/걷기, 고정 뒤 규칙,
// RegistryData 패킷(hasData), 플러그인 API(World.biomeAt/setBiome).
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.plugin.bootstrap.BootstrapContext;
import org.domi.furnace.api.plugin.bootstrap.PluginBootstrap;
import org.domi.furnace.api.plugin.lifecycle.LifecycleEvents;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.worldgen.BiomeDefinition;
import org.domi.furnace.api.worldgen.DimensionTypeDefinition;
import org.domi.furnace.api.worldgen.SpawnCategory;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.spawn.BiomeSpawns;
import org.domi.furnace.server.entity.spawn.MobCategory;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.protocol.configuration.RegistryData;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BiomeWeather;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameRegistryTest {

    private static final String P = "org.domi.furnace.server.plugin.GameRegistryTest$";
    private static final String BIOME = GameRegistryStore.BIOME_REGISTRY;
    private static final String DIM = GameRegistryStore.DIMENSION_TYPE_REGISTRY;

    @TempDir Path tmp;
    private PluginHost host;
    private FurnaceSchedulerImpl sched;

    @BeforeEach
    void setUp() {
        TestPlugins.LOG.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (host != null) {
            host.pluginsImpl().disableAll();
            host.pluginsImpl().closeAll();
            host.close();
        }
        if (sched != null) sched.close();
        TestServers.drainIoPools();
        BiomeWeather.removeCustom("glow:cave");
        BiomeSpawns.removeCustom("glow:cave");
    }

    // ---------------------------------------------------------------- 시험용 부트스트랩

    static BiomeDefinition glowCave() {
        return BiomeDefinition.builder().temperature(0.1f).downfall(0.9f).skyColor(0x112233).waterColor(0x22ccaa)
                .grassColor(0x44ff88).grassColorModifier(BiomeDefinition.GrassColorModifier.SWAMP)
                .ambientParticle("minecraft:ash", 0.02f).ambientSound("glow:ambient.hum")
                .moodSound(BiomeDefinition.MoodSound.of("minecraft:ambient.cave"))
                .music(new BiomeDefinition.Music("glow:music.deep", 1200, 3600, false))
                .spawner(SpawnCategory.CREATURE, "minecraft:rabbit", 7, 2, 3)
                .spawnCost("minecraft:rabbit", 1.0, 0.12)
                .creatureSpawnProbability(0.5f).build();
    }

    public static final class GlowBoot implements PluginBootstrap {
        @Override
        public void bootstrap(BootstrapContext ctx) {
            ctx.lifecycle().register(LifecycleEvents.GAME_REGISTRIES, r -> {
                TestPlugins.LOG.add("glow:registries");
                r.registerBiome("cave", glowCave());
                r.registerBiome("glow:meadow", glowCave().toBuilder().hasPrecipitation(false).build());
                r.registerDimensionType("deep", DimensionTypeDefinition.overworld().heightRange(0, 128).fixedTime(6000).ambientLight(0.5f).build());
                assertTrue(r.hasBiome("glow:cave"));
                assertTrue(r.hasBiome("minecraft:plains"));
                assertFalse(r.hasBiome("glow:nope"));
            });
        }
    }

    public static final class AfterBoot implements PluginBootstrap {
        @Override
        public void bootstrap(BootstrapContext ctx) {
            ctx.lifecycle().register(LifecycleEvents.GAME_REGISTRIES, r -> {
                TestPlugins.LOG.add("after:registries");
                r.registerBiome("after:late", BiomeDefinition.builder().build());
            });
        }
    }

    public static final class Plain extends FurnacePlugin {}

    private PluginBootstrapper bootstrapper() throws Exception {
        Path dir = tmp.resolve("plugins");
        PluginBootstrapTest.jar(dir.resolve("glow.jar"), "name: Glow\nversion: 1\nmain: " + P + "Plain\nbootstrapper: " + P + "GlowBoot\n", Map.of());
        PluginBootstrapTest.jar(dir.resolve("after.jar"), "name: After\nversion: 1\nmain: " + P + "Plain\nbootstrapper: " + P + "AfterBoot\ndepend: [Glow]\n", Map.of());
        return PluginBootstrapper.bootstrap(dir);
    }

    private static RegistrySet vanilla() { return RegistrySet.load("registries/1.21.1.txt"); }

    // ---------------------------------------------------------------- 시험

    @Test
    void customEntriesAppendAfterVanillaInRegistrationOrderWithData() throws Exception {
        RegistrySet base = vanilla();
        PluginBootstrapper boot = bootstrapper();
        RegistrySet set = boot.collectGameRegistries(base);

        assertEquals(List.of("glow:registries", "after:registries"), TestPlugins.LOG, "훅은 플러그인 부트스트랩 순서로 한 번씩");
        int vanillaBiomes = base.entries(BIOME).size();
        assertEquals(vanillaBiomes + 3, set.entries(BIOME).size());
        assertEquals(base.entries(BIOME), set.entries(BIOME).subList(0, vanillaBiomes), "바닐라 바이옴의 인덱스는 그대로");
        assertEquals(List.of("glow:cave", "glow:meadow", "after:late"), set.entries(BIOME).subList(vanillaBiomes, vanillaBiomes + 3));
        assertEquals(vanillaBiomes, set.indexOf(BIOME, "glow:cave"));
        assertEquals(base.entries(DIM).size() + 1, set.entries(DIM).size());
        assertEquals(base.entries(DIM).size(), set.indexOf(DIM, "glow:deep"));
        assertEquals(base.registryKeys(), set.registryKeys(), "레지스트리 순서도 그대로");

        assertNull(set.data(BIOME, "minecraft:plains"), "바닐라 항목은 데이터 없이(known packs)");
        NbtCompound b = set.data(BIOME, "glow:cave");
        assertEquals(0.1f, b.getFloat("temperature"));
        assertEquals(0.9f, b.getFloat("downfall"));
        assertTrue(b.getBoolean("has_precipitation"));
        assertFalse(b.contains("temperature_modifier"), "기본값은 생략");
        NbtCompound e = b.getCompound("effects");
        assertEquals(0x112233, e.getInt("sky_color"));
        assertEquals(0x44ff88, e.getInt("grass_color"));
        assertEquals("swamp", e.getString("grass_color_modifier"));
        assertEquals("minecraft:ash", e.getCompound("particle").getCompound("options").getString("type"));
        assertEquals(0.02f, e.getCompound("particle").getFloat("probability"));
        assertEquals("glow:ambient.hum", e.getCompound("ambient_sound").getString("sound_id"), "소리는 직접 형식");
        assertEquals("minecraft:ambient.cave", e.getCompound("mood_sound").getCompound("sound").getString("sound_id"));
        assertEquals(6000, e.getCompound("mood_sound").getInt("tick_delay"));
        assertEquals(1200, e.getCompound("music").getInt("min_delay"));
        assertFalse(e.getCompound("music").getBoolean("replace_current_music"));
        assertFalse(e.contains("foliage_color"));
        assertFalse(set.data(BIOME, "glow:meadow").getBoolean("has_precipitation"));

        NbtCompound d = set.data(DIM, "glow:deep");
        assertEquals(6000L, d.getLong("fixed_time"));
        assertEquals(0, d.getInt("min_y"));
        assertEquals(128, d.getInt("height"));
        assertEquals(128, d.getInt("logical_height"));
        assertEquals("#minecraft:infiniburn_overworld", d.getString("infiniburn"));
        assertEquals("minecraft:overworld", d.getString("effects"));
        assertEquals(0.5f, d.getFloat("ambient_light"));
        assertTrue(d.getBoolean("has_skylight"));
        assertEquals("minecraft:uniform", d.getCompound("monster_spawn_light_level").getString("type"));
        assertEquals(7, d.getCompound("monster_spawn_light_level").getInt("max_inclusive"));
    }

    @Test
    void registryDataPacketSendsBodyOnlyForPluginEntries() throws Exception {
        RegistrySet set = bootstrapper().collectGameRegistries(vanilla());
        RegistryData p = new RegistryData(DIM, set.entries(DIM), set);
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(DIM, Types.readString(buf, 256));
        int n = Types.readVarInt(buf);
        assertEquals(set.entries(DIM).size(), n);
        for (int i = 0; i < n; i++) {
            String id = Types.readString(buf, 256);
            boolean hasData = buf.readBoolean();
            if (!id.equals("glow:deep")) {
                assertFalse(hasData, id);
                continue;
            }
            assertTrue(hasData);
            byte[] rest = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), rest);
            NbtCompound body = (NbtCompound) NbtIo.readUnnamed(rest);
            assertEquals(128, body.getInt("height"));
            assertEquals(i, n - 1, "플러그인 항목은 맨 뒤라 본문이 끝까지");
            buf.release();
            return;
        }
        fail("glow:deep 이 없다");
    }

    @Test
    void weatherAndSpawnsFollowThePluginAndGoAwayWhenItIsDisabled() throws Exception {
        PluginBootstrapper boot = bootstrapper();
        RegistrySet set = boot.collectGameRegistries(vanilla());

        var cold = BiomeWeather.of("glow:cave");
        assertEquals(0.1f, cold.temperature());
        assertTrue(cold.hasPrecipitation());
        assertEquals("snow", cold.precipitationAt(0, 64, 0), "온도 0.1 < 0.15 라 눈");
        assertEquals("none", BiomeWeather.of("glow:meadow").precipitationAt(0, 64, 0));
        assertEquals(0.5f, BiomeSpawns.creatureSpawnProbability("glow:cave"));
        var rabbits = BiomeSpawns.mobs("glow:cave", MobCategory.CREATURE);
        assertEquals(List.of(new BiomeSpawns.SpawnerData("minecraft:rabbit", 7, 2, 3)), rabbits);
        assertEquals(new BiomeSpawns.SpawnCost(1.0, 0.12), BiomeSpawns.cost("glow:cave", "minecraft:rabbit"));

        boot.forget("Glow");   // 플러그인 disable 이 부르는 정리
        assertEquals(0.5f, BiomeWeather.of("glow:cave").temperature(), "걷은 뒤에는 모르는 바이옴의 기본값");
        assertTrue(BiomeSpawns.mobs("glow:cave", MobCategory.CREATURE).isEmpty());
        assertFalse(boot.gameRegistries().biome("glow:cave").active(), "인덱스는 유지하고 비활성으로만");
        assertEquals(vanilla().entries(BIOME).size(), set.indexOf(BIOME, "glow:cave"), "인덱스는 그대로");
        assertTrue(boot.gameRegistries().biome("after:late").active(), "다른 플러그인 것은 그대로");
    }

    @Test
    void keyRulesAndFreeze() throws Exception {
        PluginBootstrapper boot = bootstrapper();
        boot.collectGameRegistries(vanilla());
        var store = boot.gameRegistries();

        var r = store.registrar("Glow", "glow", java.util.logging.Logger.getLogger("t"));
        var def = BiomeDefinition.builder().build();
        assertThrows(IllegalStateException.class, () -> r.registerBiome("late", def), "고정 뒤 새 키는 거부(재시작 필요)");
        assertThrows(IllegalArgumentException.class, () -> r.registerBiome("minecraft:plains", def), "남의 namespace");
        // 같은 키 갱신 = 핫 리로드: 다시 활성이 되고 정의가 바뀐다
        store.deactivateOwner("Glow");
        assertFalse(store.biome("glow:cave").active());
        var changed = glowCave().toBuilder().temperature(0.9f).build();
        assertEquals("glow:cave", r.registerBiome("cave", changed));
        assertTrue(store.biome("glow:cave").active());
        assertEquals(0.9f, BiomeWeather.of("glow:cave").temperature());
        r.close();
        assertThrows(IllegalStateException.class, () -> r.registerBiome("cave", changed), "등록기는 훅이 도는 동안만");
    }

    @Test
    void duplicateAndInvalidDefinitionsAreRejectedBeforeFreeze() throws Exception {
        var store = new GameRegistryStore();
        store.begin(vanilla());
        var r = store.registrar("Glow", "glow", java.util.logging.Logger.getLogger("t"));
        r.registerBiome("a", BiomeDefinition.builder().build());
        assertThrows(IllegalArgumentException.class, () -> r.registerBiome("a", BiomeDefinition.builder().build()), "중복");
        assertThrows(IllegalArgumentException.class, () -> r.registerBiome("Bad Key", BiomeDefinition.builder().build()), "대문자·공백");
        assertThrows(IllegalArgumentException.class, () -> r.registerBiome("b",
                BiomeDefinition.builder().ambientParticle("minecraft:dust", 0.1f).build()), "옵션이 필요한 입자");
        assertThrows(IllegalArgumentException.class, () -> r.registerBiome("c",
                BiomeDefinition.builder().ambientParticle("minecraft:nope", 0.1f).build()), "모르는 입자");
        assertThrows(IllegalArgumentException.class, () -> r.registerBiome("d",
                BiomeDefinition.builder().spawner(SpawnCategory.MONSTER, "minecraft:nope", 1, 1, 1).build()), "모르는 몹");
        assertThrows(IllegalArgumentException.class, () -> BiomeDefinition.builder().spawner(SpawnCategory.MONSTER, "minecraft:zombie", 1, 3, 2));
        assertThrows(IllegalArgumentException.class, () -> BiomeDefinition.builder().creatureSpawnProbability(1f).build());
        assertThrows(IllegalArgumentException.class, () -> DimensionTypeDefinition.overworld().heightRange(0, 100).build(), "16의 배수");
        assertThrows(IllegalArgumentException.class, () -> DimensionTypeDefinition.overworld().heightRange(2000, 64).build(), "min+height>2032");
        assertThrows(IllegalArgumentException.class, () -> DimensionTypeDefinition.overworld().heightRange(0, 64).logicalHeight(80).build());
        assertThrows(IllegalArgumentException.class, () -> DimensionTypeDefinition.overworld().coordinateScale(0).build());
        r.registerDimensionType("dim", DimensionTypeDefinition.nether().build());
        assertThrows(IllegalArgumentException.class, () -> r.registerDimensionType("dim", DimensionTypeDefinition.end().build()));
    }

    @Test
    void definitionsAreValueObjects() {
        assertEquals(glowCave(), glowCave());
        assertEquals(glowCave(), glowCave().toBuilder().build());
        assertNotEquals(glowCave(), glowCave().toBuilder().downfall(0.1f).build());
        assertEquals(DimensionTypeDefinition.nether().build(), DimensionTypeDefinition.nether().build().toBuilder().build());
        // 바닐라 값과 같은 프리셋
        var ow = DimensionTypeDefinition.overworld().build();
        assertEquals(-64, ow.minY());
        assertEquals(320, ow.maxY());
        var nether = DimensionTypeDefinition.nether().build();
        assertEquals(18000L, nether.fixedTime().getAsLong());
        assertEquals(128, nether.logicalHeight());
        assertEquals(7, nether.monsterSpawnLightMax());
    }

    @Test
    void worldApiReadsAndSetsPluginBiomes() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        ItemContext.install(new TagRegistries(vanilla()));
        ServerCore core = TestServers.core(tmp, sched);
        PluginBootstrapper boot = bootstrapper();
        RegistrySet set = boot.collectGameRegistries(vanilla());
        core.registries(set);
        host = core.plugins();
        host.pluginsImpl().loadAll(boot);
        core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt"), org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        ChunkPos c00 = new ChunkPos(0, 0);
        core.world().submitAtChunk(c00, r -> r.chunkAtBlocking(c00)).get(30, TimeUnit.SECONDS);
        var w = host.world("minecraft:overworld");
        BlockPos pos = new BlockPos(5, 70, 6);

        String before = w.biomeAt(pos).get(10, TimeUnit.SECONDS).orElseThrow();
        assertTrue(set.indexOf(BIOME, before) >= 0 && set.indexOf(BIOME, before) < vanilla().entries(BIOME).size(), "시작은 바닐라 바이옴: " + before);
        assertTrue(w.setBiome(pos, "glow:cave").get(10, TimeUnit.SECONDS));
        assertEquals("glow:cave", w.biomeAt(pos).get(10, TimeUnit.SECONDS).orElseThrow());
        assertEquals("glow:cave", w.biomeAt(new BlockPos(4, 68, 4)).get(10, TimeUnit.SECONDS).orElseThrow(), "같은 4칸 칸이 한꺼번에 바뀐다");
        assertEquals(before, w.biomeAt(new BlockPos(8, 70, 6)).get(10, TimeUnit.SECONDS).orElseThrow(), "옆 칸은 그대로");
        assertTrue(w.setBiome(pos, "plains").get(10, TimeUnit.SECONDS), "namespace 없으면 minecraft:");
        assertEquals("minecraft:plains", w.biomeAt(pos).get(10, TimeUnit.SECONDS).orElseThrow());
        var ex = assertThrows(java.util.concurrent.ExecutionException.class, () -> w.setBiome(pos, "glow:none").get(10, TimeUnit.SECONDS));
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        assertFalse(w.setBiome(new BlockPos(16 * 300, 70, 0), "glow:cave").get(10, TimeUnit.SECONDS), "로드되지 않은 청크");
        assertTrue(w.biomeAt(new BlockPos(16 * 300, 70, 0)).get(10, TimeUnit.SECONDS).isEmpty());

        boot.forget("Glow");
        var ex2 = assertThrows(java.util.concurrent.ExecutionException.class, () -> w.setBiome(pos, "glow:cave").get(10, TimeUnit.SECONDS));
        assertInstanceOf(IllegalArgumentException.class, ex2.getCause(), "꺼진 플러그인 바이옴은 새로 못 칠한다");
    }
}
