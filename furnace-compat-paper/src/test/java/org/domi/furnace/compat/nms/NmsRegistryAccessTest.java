package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.AquaticFeatures;
import net.minecraft.data.worldgen.features.CaveFeatures;
import net.minecraft.data.worldgen.features.EndFeatures;
import net.minecraft.data.worldgen.features.NetherFeatures;
import net.minecraft.data.worldgen.features.PileFeatures;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.data.worldgen.features.VegetationFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.CoralTreeFeature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * N2b-6: FAWE 가 시작 때 쓰는 레지스트리 표면 — {@code registryAccess().registryOrThrow/lookupOrThrow}(바이옴·블록·아이템·설정된/배치된 지형지물·구조물),
 * 바이옴 레지스트리 번호(청크 shim 과 같은 번호)·태그, 월드젠 키 목록(바닐라 데이터와 같은 개수·이름), 나무 종류 판별, 지형지물 키 모음 클래스.
 * 정답은 바닐라 1.21.1 데이터({@code tools/vanilla-data/server-1.21.1}) 파일에서 읽는다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsRegistryAccessTest {

    static final Path DATA = Path.of("../tools/vanilla-data/server-1.21.1/data/minecraft/worldgen");

    FurnaceTestServer server;
    RegistryAccess access;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        server.tick(2);
        access = RegistryAccess.SERVER;
    }

    @AfterAll
    void stop() { server.close(); }

    private static Set<String> dataIds(String dir) throws IOException {
        Path root = DATA.resolve(dir);
        Assumptions.assumeTrue(Files.isDirectory(root), "바닐라 데이터 폴더 없음: " + root);
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> p.toString().endsWith(".json")).map(p -> {
                String rel = root.relativize(p).toString().replace('\\', '/');
                return "minecraft:" + rel.substring(0, rel.length() - 5);
            }).collect(Collectors.toCollection(TreeSet::new));
        }
    }

    private static String json(String dir, String id) throws IOException {
        return Files.readString(DATA.resolve(dir).resolve(id.substring("minecraft:".length()) + ".json"));
    }

    private static final Pattern TYPE = Pattern.compile("\"type\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern FEATURE = Pattern.compile("\"feature\"\\s*:\\s*\"([^\"]+)\"");

    // ------------------------------------------------------------------ registryOrThrow

    @Test
    void registryOrThrowForEveryKeyFaweUses() {
        assertEquals("minecraft:worldgen/biome", access.registryOrThrow(Registries.BIOME).key().location().toString());
        assertNotNull(access.registryOrThrow(Registries.BLOCK).get(ResourceLocation.withDefaultNamespace("stone")));
        assertNotNull(access.registryOrThrow(Registries.ITEM).get(ResourceLocation.withDefaultNamespace("stick")));
        assertTrue(access.registryOrThrow(Registries.CONFIGURED_FEATURE).containsKey(ResourceLocation.withDefaultNamespace("oak")));
        assertTrue(access.registryOrThrow(Registries.PLACED_FEATURE).containsKey(ResourceLocation.withDefaultNamespace("oak_checked")));
        assertTrue(access.registryOrThrow(Registries.STRUCTURE).containsKey(ResourceLocation.withDefaultNamespace("village_plains")));
        assertSame(access.registryOrThrow(Registries.BIOME), access.registryOrThrow(Registries.BIOME), "서버 바이옴이 안 바뀌면 같은 레지스트리 객체");
        assertSame(net.minecraft.core.registries.BuiltInRegistries.BLOCK, access.registryOrThrow(Registries.BLOCK));
    }

    @Test
    void unknownRegistryThrowsLikeVanilla() {
        ResourceKey<Registry<Object>> bogus = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("furnace", "no_such_registry"));
        var e = assertThrows(IllegalStateException.class, () -> access.registryOrThrow(bogus));
        assertEquals("Missing registry: " + bogus, e.getMessage());
        assertTrue(access.registry(bogus).isEmpty());
        var e2 = assertThrows(IllegalStateException.class, () -> access.lookupOrThrow(bogus));
        assertEquals("Registry furnace:no_such_registry not found", e2.getMessage());
        // 바닐라에는 있지만 shim 이 값 타입을 아직 안 만든 레지스트리도 같은 길
        assertThrows(IllegalStateException.class, () -> access.registryOrThrow(Registries.GAME_EVENT));   // 엔티티 종류는 5e-2 에서 생겼다
    }

    @Test
    void lookupOrThrowListsElements() {
        HolderLookup.RegistryLookup<PlacedFeature> placed = access.lookupOrThrow(Registries.PLACED_FEATURE);
        List<Holder.Reference<PlacedFeature>> all = placed.listElements().toList();
        assertEquals(access.registryOrThrow(Registries.PLACED_FEATURE).keySet().size(), all.size());
        Holder.Reference<PlacedFeature> oak = all.stream().filter(h -> h.key().location().getPath().equals("oak_checked")).findFirst().orElseThrow();
        assertSame(oak.value(), placed.getOrThrow(oak.key()).value());
        assertInstanceOf(ConfiguredFeature.class, oak.value().feature().value());
        assertThrows(IllegalStateException.class, () -> placed.getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, ResourceLocation.withDefaultNamespace("nope"))));
        assertEquals(access.registryOrThrow(Registries.BIOME).keySet().size(), access.lookupOrThrow(Registries.BIOME).listElements().count());
    }

    // ------------------------------------------------------------------ 바이옴

    @Test
    void biomeRegistryIsWritableAndNumberedLikeChunkShims() {
        Registry<Biome> reg = access.registryOrThrow(Registries.BIOME);
        assertInstanceOf(WritableRegistry.class, reg, "FAWE 가 (WritableRegistry<Biome>) 로 캐스팅한다");
        assertEquals(64, reg.size(), "바닐라 1.21.1 바이옴 64개");
        assertEquals(NmsChunks.BIOME_IDS.size(), reg.size());
        for (int i = 0; i < reg.size(); i++) {
            Holder<Biome> h = NmsChunks.BIOME_IDS.byId(i);
            Biome b = h.value();
            assertSame(b, reg.byId(i), "번호 " + i);
            assertEquals(i, reg.getId(b));
            assertEquals(h.unwrapKey().orElseThrow().location(), reg.getKey(b));
            assertSame(b, reg.get(reg.getKey(b)));
            assertSame(h, reg.getHolder(i).orElseThrow(), "같은 손잡이 객체(== 비교)");
        }
        List<ResourceLocation> viaStream = reg.stream().map(reg::getKey).toList();
        assertEquals(64, viaStream.size());
        assertTrue(viaStream.contains(ResourceLocation.withDefaultNamespace("cherry_grove")));
        assertTrue(viaStream.contains(ResourceLocation.withDefaultNamespace("the_void")));
        assertEquals(-1, reg.getId(null));
        assertThrows(IllegalStateException.class, () -> ((WritableRegistry<Biome>) reg).register(
                ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("x", "y")), null, net.minecraft.core.RegistrationInfo.BUILT_IN));
        // FAWE getInternalBiomeId
        assertEquals(NmsChunks.biomeId(NmsChunks.biome("minecraft:plains")), reg.getId(reg.get(ResourceLocation.tryParse("minecraft:plains"))));
        // FAWE getRegisteredBiomes → CraftNamespacedKey
        assertEquals("minecraft:plains", CraftNamespacedKey.fromMinecraft(ResourceLocation.withDefaultNamespace("plains")).toString());
        assertEquals(ResourceLocation.fromNamespaceAndPath("a", "b/c"), CraftNamespacedKey.toMinecraft(new org.bukkit.NamespacedKey("a", "b/c")));
    }

    @Test
    void biomeTagsAreServerTags() {
        Registry<Biome> reg = access.registryOrThrow(Registries.BIOME);
        List<TagKey<Biome>> tags = reg.getTagNames().toList();
        assertFalse(tags.isEmpty());
        TagKey<Biome> ocean = TagKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("is_ocean"));
        assertTrue(tags.contains(ocean));
        HolderSet.Named<Biome> named = reg.getTag(ocean).orElseThrow();
        Set<String> names = named.stream().map(h -> h.unwrapKey().orElseThrow().location().toString()).collect(Collectors.toSet());
        assertTrue(names.containsAll(Set.of("minecraft:ocean", "minecraft:deep_ocean", "minecraft:warm_ocean")), names.toString());
        Holder<Biome> oceanHolder = named.stream().filter(h -> h.is(ResourceLocation.withDefaultNamespace("ocean"))).findFirst().orElseThrow();
        assertSame(NmsChunks.biome("minecraft:ocean"), oceanHolder, "태그 안의 손잡이도 청크 shim 과 같은 객체");
    }

    // ------------------------------------------------------------------ 월드젠 키 목록

    @Test
    void configuredFeatureAndStructureKeySetsEqualVanillaData() throws IOException {
        Set<String> cf = dataIds("configured_feature");
        Set<String> got = access.registryOrThrow(Registries.CONFIGURED_FEATURE).keySet().stream().map(ResourceLocation::toString).collect(Collectors.toSet());
        assertEquals(cf, got);
        assertEquals(196, got.size());
        Set<String> st = dataIds("structure");
        Set<String> gotSt = access.registryOrThrow(Registries.STRUCTURE).keySet().stream().map(ResourceLocation::toString).collect(Collectors.toSet());
        assertEquals(st, gotSt);
        assertEquals(34, gotSt.size());
        Set<String> pf = dataIds("placed_feature");
        Set<String> gotPf = access.registryOrThrow(Registries.PLACED_FEATURE).keySet().stream().map(ResourceLocation::toString).collect(Collectors.toSet());
        assertEquals(pf, gotPf);
        assertEquals(233, gotPf.size());
        Structure village = access.registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.withDefaultNamespace("village_plains"));
        assertEquals(TYPE.matcher(json("structure", "minecraft:village_plains")).results().findFirst().orElseThrow().group(1), ((net.minecraft.world.level.levelgen.structure.FurnaceStructure) village).furnaceTypeId());
    }

    @Test
    void treeFilterMatchesVanillaData() throws IOException {
        // 정답: 배치된 지형지물이 가리키는 설정된 지형지물의 type 이 minecraft:tree / minecraft:coral_tree
        Set<String> want = new TreeSet<>();
        for (String id : dataIds("placed_feature")) {
            Matcher f = FEATURE.matcher(json("placed_feature", id));
            if (!f.find()) continue;   // 직접 적은 지형지물(바닐라 1.21.1 에는 없어야 한다 — 아래에서 확인)
            Matcher t = TYPE.matcher(json("configured_feature", f.group(1)));
            if (t.find() && (t.group(1).equals("minecraft:tree") || t.group(1).equals("minecraft:coral_tree"))) want.add(id);
        }
        assertTrue(want.contains("minecraft:oak_checked") && want.contains("minecraft:fancy_oak_checked"), "나무 종류: " + want);
        // FAWE initializeRegistries 의 거름 조건 그대로
        Set<String> got = access.lookupOrThrow(Registries.PLACED_FEATURE).listElements()
                .filter(feature -> {
                    var underlying = feature.value().feature().value().feature();
                    return underlying instanceof TreeFeature || underlying instanceof CoralTreeFeature;
                })
                .map(h -> h.key().location().toString()).collect(Collectors.toCollection(TreeSet::new));
        assertEquals(want, got);
        // 같은 종류는 같은 객체
        var reg = access.registryOrThrow(Registries.CONFIGURED_FEATURE);
        assertSame(reg.get(ResourceLocation.withDefaultNamespace("oak")).feature(), reg.get(ResourceLocation.withDefaultNamespace("birch")).feature());
    }

    @Test
    void placedFeaturesPointAtRegisteredConfiguredFeatures() throws IOException {
        var cf = access.registryOrThrow(Registries.CONFIGURED_FEATURE);
        var pf = access.registryOrThrow(Registries.PLACED_FEATURE);
        for (String id : dataIds("placed_feature")) {
            Matcher f = FEATURE.matcher(json("placed_feature", id));
            assertTrue(f.find(), "직접 적은 지형지물 없음: " + id);
            assertSame(cf.get(ResourceLocation.parse(f.group(1))), pf.get(ResourceLocation.parse(id)).feature().value(), id);
        }
    }

    // ------------------------------------------------------------------ 지형지물 키 모음 클래스

    @Test
    void featureKeyClassesResolveToConfiguredFeatures() {
        Registry<ConfiguredFeature<?, ?>> cf = access.registryOrThrow(Registries.CONFIGURED_FEATURE);
        int total = 0;
        for (Class<?> c : List.of(AquaticFeatures.class, CaveFeatures.class, EndFeatures.class, NetherFeatures.class, PileFeatures.class,
                TreeFeatures.class, VegetationFeatures.class)) {
            int n = 0;
            for (var f : c.getFields()) {
                int m = f.getModifiers();
                if (!Modifier.isPublic(m) || !Modifier.isStatic(m) || !Modifier.isFinal(m) || !f.getType().equals(ResourceKey.class)) continue;
                try {
                    @SuppressWarnings("unchecked")
                    ResourceKey<ConfiguredFeature<?, ?>> key = (ResourceKey<ConfiguredFeature<?, ?>>) f.get(null);
                    assertTrue(key.isFor(Registries.CONFIGURED_FEATURE), f.toString());
                    assertTrue(cf.containsKey(key), "없는 키: " + f.getName() + " → " + key.location());
                    assertNotNull(cf.get(key));
                    n++;
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
            total += n;
            assertTrue(n > 0, c.getName());
        }
        assertEquals(146, total, "바닐라 1.21.1 키 모음 7개의 필드 수");
        assertEquals(37, countKeys(TreeFeatures.class));
        assertEquals("minecraft:warped_forest_vegetation", NetherFeatures.WARPED_FOREST_VEGETION.location().toString(), "바닐라 필드 이름 오타(VEGETION) 그대로");
        // FAWE 가 직접 읽는 것
        List<ResourceKey<ConfiguredFeature<?, ?>>> direct = new ArrayList<>(List.of(
                CaveFeatures.DRIPSTONE_CLUSTER, CaveFeatures.LARGE_DRIPSTONE, CaveFeatures.POINTED_DRIPSTONE, CaveFeatures.GLOW_LICHEN,
                CaveFeatures.CAVE_VINE, CaveFeatures.CAVE_VINE_IN_MOSS, CaveFeatures.MOSS_VEGETATION, CaveFeatures.DRIPLEAF,
                EndFeatures.CHORUS_PLANT, EndFeatures.END_PLATFORM,
                NetherFeatures.SMALL_BASALT_COLUMNS, NetherFeatures.LARGE_BASALT_COLUMNS, NetherFeatures.GLOWSTONE_EXTRA));
        Set<ResourceKey<?>> seen = new HashSet<>();
        for (var k : direct) {
            assertTrue(cf.containsKey(k), k.toString());
            assertTrue(seen.add(k));
        }
    }

    private static int countKeys(Class<?> c) {
        int n = 0;
        for (var f : c.getFields()) if (f.getType().equals(ResourceKey.class)) n++;
        return n;
    }
}
