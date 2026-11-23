package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 효과·프로필·속성 묶음: {@code MobEffectInstance}(값 규칙)·플레이어/몹 효과·{@code ResolvableProfile}·{@code AttributeMap}·{@code Util}·{@code WorldData}·
 * {@code BlockableEventLoop#managedBlock}·{@code LevelChunk#setBiome}. 기대값은 Paper 1.21.1 jar(javap)에서 읽은 규칙이다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsEffectProfileAttributeTest {

    FurnaceTestServer server;
    TestPlayer tp;
    CraftPlayer bukkit;
    ServerPlayer handle;
    ServerLevel level;
    org.domi.furnace.api.world.BlockPos at;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Effector");
        server.tick(40);
        bukkit = (CraftPlayer) Compat.player(tp.player());
        handle = bukkit.getHandle();
        level = ((CraftWorld) bukkit.getWorld()).getHandle();
        var loc = tp.player().location();
        at = new org.domi.furnace.api.world.BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T owner(Supplier<T> task) { return server.onRegion(at, task); }

    // ------------------------------------------------------------------ MobEffectInstance 값 규칙

    @Test
    void constructorsDefaultsAndClamp() {
        var e = new MobEffectInstance(MobEffects.MOVEMENT_SPEED);
        assertEquals(0, e.getDuration());
        assertEquals(0, e.getAmplifier());
        assertFalse(e.isAmbient());
        assertTrue(e.isVisible());
        assertTrue(e.showIcon());
        var hidden = new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 2, false, false);
        assertFalse(hidden.isVisible());
        assertFalse(hidden.showIcon(), "5인자 생성자는 아이콘을 입자 표시와 같게");
        assertEquals(255, new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1, 9999).getAmplifier());
        assertEquals(0, new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1, -5).getAmplifier());
        assertTrue(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION).isInfiniteDuration());
        assertTrue(e.is(MobEffects.MOVEMENT_SPEED));
        assertFalse(e.is(MobEffects.MOVEMENT_SLOWDOWN));
    }

    @Test
    void toStringEqualsAndOrdering() {
        var plain = new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100);
        assertEquals("effect.minecraft.speed, Duration: 100", plain.toString());
        assertEquals("effect.minecraft.speed x 3, Duration: infinite, Particles: false, Show Icon: false",
                new MobEffectInstance(MobEffects.MOVEMENT_SPEED, -1, 2, false, false, false).toString());
        assertEquals("effect.minecraft.speed, Duration: 100, Show Icon: false", new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0, false, true, false).toString());
        assertEquals(plain, new MobEffectInstance(plain));
        assertEquals(plain.hashCode(), new MobEffectInstance(plain).hashCode());
        assertFalse(plain.equals(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 101)));
        // 정렬: 환경 아닌 것 → 끝나는 것 → 짧은 것
        var ambientEffect = new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 0, true, true);
        assertTrue(plain.compareTo(ambientEffect) < 0);
        assertTrue(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50).compareTo(plain) < 0);
        assertTrue(plain.compareTo(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, -1)) < 0, "끝나지 않는 것이 뒤");
        assertTrue(plain.endsWithin(100));
        assertFalse(plain.endsWithin(99));
        assertFalse(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, -1).endsWithin(Integer.MAX_VALUE));
        assertEquals(200, plain.mapDuration(d -> d * 2));
        assertEquals(-1, new MobEffectInstance(MobEffects.MOVEMENT_SPEED, -1).mapDuration(d -> 7));
    }

    @Test
    void updateKeepsTheWeakerEffectHidden() {
        // 더 센 새 효과가 더 짧으면 지금 효과가 숨은 효과로 남는다
        var base = new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 0);
        assertTrue(base.update(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 2)));
        assertEquals(2, base.getAmplifier());
        assertEquals(100, base.getDuration());
        // 같은 세기에 더 긴 지속은 지속만 늘린다
        var same = new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1);
        assertTrue(same.update(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 1)));
        assertEquals(300, same.getDuration());
        // 같은 것·더 약하고 짧은 것은 바뀌지 않는다
        assertFalse(same.update(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1)));
        assertFalse(same.update(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 10, 0)));
        // 더 약하고 더 긴 것은 숨은 효과로만 — 지금 효과는 그대로
        var strong = new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 3);
        assertFalse(strong.update(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 900, 0)));
        assertEquals(3, strong.getAmplifier());
        // 표시만 다르면 바뀐 것
        var vis = new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1);
        assertTrue(vis.update(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, false)));
        assertFalse(vis.isVisible());
        assertFalse(vis.showIcon());
    }

    @Test
    void saveAndLoadRoundTrip() {
        var e = new MobEffectInstance(MobEffects.REGENERATION, 600, 2, true, false, true, new MobEffectInstance(MobEffects.REGENERATION, 900, 0));
        var tag = (CompoundTag) e.save();
        assertEquals("minecraft:regeneration", tag.getString("id"));
        assertEquals(2, tag.getByte("amplifier"));
        assertEquals(600, tag.getInt("duration"));
        assertTrue(tag.getBoolean("ambient"));
        assertFalse(tag.getBoolean("show_particles"));
        assertTrue(tag.getBoolean("show_icon"));
        assertTrue(tag.contains("hidden_effect"));
        assertEquals(e, MobEffectInstance.load(tag));
        assertEquals(new MobEffectInstance(MobEffects.REGENERATION, 900, 0), MobEffectInstance.load(tag.getCompound("hidden_effect").copy().merge(idOnly())));
        var bad = new CompoundTag();
        bad.putString("id", "minecraft:no_such_effect");
        assertNull(MobEffectInstance.load(bad));
        assertNull(MobEffectInstance.load(new CompoundTag()));
        // 기본값 칸은 적지 않는다(show_icon 만 늘 적는다)
        var plain = (CompoundTag) new MobEffectInstance(MobEffects.GLOWING, 0).save();
        assertEquals(java.util.Set.of("id", "show_icon"), plain.getAllKeys());
    }

    private static CompoundTag idOnly() {
        var t = new CompoundTag();
        t.putString("id", "minecraft:regeneration");
        return t;
    }

    // ------------------------------------------------------------------ 엔티티·플레이어 효과

    @Test
    void playerEffectsReadAndWriteFromTheOwnerThread() {
        owner(() -> {
            assertTrue(handle.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1)), "새로 걸면 true");
            MobEffectInstance got = handle.getEffect(MobEffects.MOVEMENT_SPEED);
            assertNotNull(got);
            assertEquals(1, got.getAmplifier());
            assertTrue(got.getDuration() <= 200 && got.getDuration() > 190);
            assertTrue(handle.hasEffect(MobEffects.MOVEMENT_SPEED));
            assertFalse(handle.hasEffect(MobEffects.LEVITATION));
            assertNull(handle.getEffect(MobEffects.LEVITATION));
            assertEquals(1, handle.getActiveEffects().size());
            assertSame(MobEffects.MOVEMENT_SPEED, handle.getActiveEffectsMap().keySet().iterator().next());
            assertFalse(handle.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20, 0)), "더 약하고 짧은 효과는 바뀐 것이 없다");
            assertTrue(handle.removeEffect(MobEffects.MOVEMENT_SPEED));
            assertFalse(handle.removeEffect(MobEffects.MOVEMENT_SPEED));
            handle.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400));
            handle.addEffect(new MobEffectInstance(MobEffects.GLOWING, 400));
            assertTrue(handle.removeAllEffects());
            assertFalse(handle.removeAllEffects());
            assertTrue(handle.getActiveEffects().isEmpty());
            return null;
        });
    }

    @Test
    void playerEffectsReadFromAnyThread() throws Exception {
        owner(() -> handle.addEffect(new MobEffectInstance(MobEffects.JUMP, 1000, 0)));
        try {
            MobEffectInstance[] seen = new MobEffectInstance[1];
            Thread t = new Thread(() -> seen[0] = handle.getEffect(MobEffects.JUMP));
            t.start();
            t.join();
            assertNotNull(seen[0], "플레이어 효과 읽기는 어느 스레드나");
            assertEquals(0, seen[0].getAmplifier());
        } finally {
            owner(() -> handle.removeAllEffects());
        }
    }

    @Test
    void livingEntityEffects() {
        var pig = owner(() -> (org.bukkit.craftbukkit.entity.CraftEntity) ((CraftWorld) bukkit.getWorld()).spawnEntity(
                new org.bukkit.Location(bukkit.getWorld(), at.x() + 3.5, at.y() + 1, at.z() + 3.5), org.bukkit.entity.EntityType.PIG));
        LivingEntity h = (LivingEntity) pig.getHandle();
        owner(() -> {
            assertTrue(h.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1)));
            assertEquals(1, h.getEffect(MobEffects.POISON).getAmplifier());
            assertTrue(h.hasEffect(MobEffects.POISON));
            assertTrue(h.removeEffect(MobEffects.POISON));
            assertFalse(h.hasEffect(MobEffects.POISON));
            return null;
        });
        assertThrows(ThreadOwnershipException.class, () -> h.getEffect(MobEffects.POISON), "몹의 효과 읽기는 소유 스레드에서만(Bukkit 과 같다)");
    }

    // ------------------------------------------------------------------ ResolvableProfile

    @Test
    void resolvableProfileRecordRules() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        var props = new PropertyMap();
        props.put("textures", new Property("textures", "abc", "sig"));
        var full = new ResolvableProfile(Optional.of("Alex"), Optional.of(id), props);
        assertEquals("Alex", full.gameProfile().getName());
        assertEquals(id, full.gameProfile().getId());
        assertEquals(1, full.gameProfile().getProperties().values().size());
        assertTrue(full.isResolved());
        assertSame(full, full.resolve().join(), "이미 풀렸으면 자기 자신");
        var nameOnly = new ResolvableProfile(Optional.of("Steve"), Optional.empty(), new PropertyMap());
        assertFalse(nameOnly.isResolved());
        assertEquals(Util.NIL_UUID, nameOnly.gameProfile().getId());
        var idOnly = new ResolvableProfile(Optional.empty(), Optional.of(id), new PropertyMap());
        assertFalse(idOnly.isResolved());
        assertEquals("", idOnly.gameProfile().getName());
        var neither = new ResolvableProfile(Optional.empty(), Optional.empty(), new PropertyMap());
        assertTrue(neither.isResolved(), "이름도 UUID 도 없으면 풀 것이 없다");
        var skin = new ResolvableProfile(Optional.of("S"), Optional.empty(), props);
        assertTrue(skin.isResolved(), "속성이 있으면 풀린 것");
        var viaProfile = new ResolvableProfile(new GameProfile(id, "Alex"));
        assertEquals(Optional.of("Alex"), viaProfile.name());
        assertEquals(Optional.of(id), viaProfile.id());
        assertTrue(viaProfile.isResolved());
    }

    @Test
    @SuppressWarnings("unchecked")
    void profileComponentRoundTrip() {
        var type = (DataComponents.Type<String, ResolvableProfile>) DataComponents.PROFILE;
        assertEquals("minecraft:profile", type.furnaceKey());
        UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        var props = new PropertyMap();
        props.put("textures", new Property("textures", "abc", "sig"));
        var original = new ResolvableProfile(Optional.of("Alex"), Optional.of(id), props);
        ResolvableProfile back = type.fromSnbt(type.toSnbt(original));
        assertEquals(original.name(), back.name());
        assertEquals(original.id(), back.id());
        assertEquals(1, back.properties().values().size());
        var named = type.fromSnbt("\"Notch\"");
        assertEquals(Optional.of("Notch"), named.name());
        assertTrue(named.id().isEmpty());
    }

    // ------------------------------------------------------------------ AttributeMap

    @Test
    void attributeMapIsTheEntityView() {
        AttributeMap map = handle.getAttributes();
        assertTrue(map.hasAttribute(Attributes.MAX_HEALTH));
        assertEquals(20.0, map.getBaseValue(Attributes.MAX_HEALTH));
        assertEquals(20.0, map.getValue(Attributes.MAX_HEALTH));
        assertNotNull(map.getInstance(Attributes.MAX_HEALTH));
        assertFalse(map.hasAttribute(Attributes.FLYING_SPEED), "플레이어는 날개 속도 속성이 없다");
        assertNull(map.getInstance(Attributes.FLYING_SPEED));
        var e = assertThrows(IllegalArgumentException.class, () -> map.getValue(Attributes.FLYING_SPEED));
        assertEquals("Can't find attribute minecraft:generic.flying_speed", e.getMessage());
        ResourceLocation id = ResourceLocation.parse("test:boost");
        var mod = new AttributeModifier(id, 4.0, AttributeModifier.Operation.ADD_VALUE);
        owner(() -> {
            map.addTransientAttributeModifiers(ImmutableMultimap.of(Attributes.MAX_HEALTH, mod));
            assertTrue(map.hasModifier(Attributes.MAX_HEALTH, id));
            assertEquals(4.0, map.getModifierValue(Attributes.MAX_HEALTH, id));
            assertEquals(24.0, map.getValue(Attributes.MAX_HEALTH));
            // 같은 id 는 바꿔 단다(오류 없음)
            map.addTransientAttributeModifiers(ImmutableMultimap.of(Attributes.MAX_HEALTH, new AttributeModifier(id, 6.0, AttributeModifier.Operation.ADD_VALUE)));
            assertEquals(26.0, map.getValue(Attributes.MAX_HEALTH));
            // 엔티티에 없는 속성은 건너뛴다
            map.addTransientAttributeModifiers(ImmutableMultimap.of(Attributes.FLYING_SPEED, mod));
            var missing = assertThrows(IllegalArgumentException.class, () -> map.getModifierValue(Attributes.MAX_HEALTH, ResourceLocation.parse("test:none")));
            assertEquals("Can't find modifier test:none on attribute minecraft:generic.max_health", missing.getMessage());
            map.removeAttributeModifiers(ImmutableMultimap.of(Attributes.MAX_HEALTH, mod));
            assertFalse(map.hasModifier(Attributes.MAX_HEALTH, id));
            assertEquals(20.0, map.getValue(Attributes.MAX_HEALTH));
            return null;
        });
        var synced = new ArrayList<>(map.getSyncableAttributes());
        assertFalse(synced.isEmpty());
        assertTrue(synced.stream().noneMatch(i -> i.getAttribute().is(ResourceLocation.parse("minecraft:generic.attack_damage"))), "공격력은 동기하지 않는다");
    }

    // ------------------------------------------------------------------ Util

    /** 바닐라와 같은 호출 순서를 보는 대본 난수: 부른 bound 를 기록하고 정해 둔 값을 준다. */
    static final class Scripted implements net.minecraft.util.RandomSource {
        final List<Integer> bounds = new ArrayList<>();
        final int[] answers;
        int next;

        Scripted(int... answers) { this.answers = answers; }

        @Override
        public int nextInt(int bound) {
            bounds.add(bound);
            return answers[next++];
        }

        // 아래는 이 시험이 쓰지 않는다
        @Override public net.minecraft.util.RandomSource fork() { throw new UnsupportedOperationException(); }
        @Override public void setSeed(long seed) { throw new UnsupportedOperationException(); }
        @Override public int nextInt() { throw new UnsupportedOperationException(); }
        @Override public long nextLong() { throw new UnsupportedOperationException(); }
        @Override public boolean nextBoolean() { throw new UnsupportedOperationException(); }
        @Override public float nextFloat() { throw new UnsupportedOperationException(); }
        @Override public double nextDouble() { throw new UnsupportedOperationException(); }
        @Override public double nextGaussian() { throw new UnsupportedOperationException(); }
    }

    @Test
    void utilShuffleCallsTheRandomInVanillaOrder() {
        // size 4: i=4,3,2 → nextInt(4), nextInt(3), nextInt(2); 각 단계에서 (i-1) 번과 j 번을 맞바꾼다
        var rnd = new Scripted(0, 0, 1);
        List<String> list = new ArrayList<>(List.of("a", "b", "c", "d"));
        Util.shuffle(list, rnd);
        assertEquals(List.of(4, 3, 2), rnd.bounds);
        // (4,j=0): [d,b,c,a] → (3,j=0): [c,b,d,a] → (2,j=1): 그대로
        assertEquals(List.of("c", "b", "d", "a"), list);
        var rnd2 = new Scripted(2);
        assertEquals("c", Util.getRandom(List.of("a", "b", "c"), rnd2));
        assertEquals(List.of(3), rnd2.bounds);
        assertEquals(Optional.empty(), Util.getRandomSafe(List.of(), new Scripted()));
        assertEquals(7, Util.getRandom(new int[] {5, 6, 7}, new Scripted(2)));
    }

    @Test
    void utilPureHelpers() {
        assertEquals(new UUID(0, 0), Util.NIL_UUID);
        long a = Util.getMillis();
        assertTrue(Util.getNanos() / Util.NANOS_PER_MILLI >= a);
        assertTrue(Math.abs(Util.getEpochMillis() - System.currentTimeMillis()) < 5000);
        assertTrue(Util.getFilenameFormattedDateTime().matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}\\.\\d{2}\\.\\d{2}"));
        assertEquals("block.minecraft.stone", Util.makeDescriptionId("block", ResourceLocation.parse("minecraft:stone")));
        assertEquals("block.foo.a.b", Util.makeDescriptionId("block", ResourceLocation.parse("foo:a/b")));
        assertEquals("block.unregistered_sadface", Util.makeDescriptionId("block", null));
        assertEquals("root", Util.describeError(new RuntimeException("outer", new IllegalStateException("root"))));
        assertEquals("java.lang.IllegalStateException", Util.describeError(new IllegalStateException()));
        assertEquals(List.of(1, 2, 3), Util.copyAndAdd(List.of(1, 2), 3));
        assertEquals(List.of(0, 1, 2), Util.copyAndAdd(0, List.of(1, 2)));
        assertEquals(java.util.Map.of("k", 2), Util.copyAndPut(java.util.Map.of("k", 1), "k", 2));
        assertEquals(3, Util.<Integer>createIndexLookup(List.of(7, 8, 9, 10)).applyAsInt(10));
        var big = new ArrayList<Integer>();
        for (int i = 0; i < 20; i++) big.add(i * 2);
        assertEquals(5, Util.createIndexLookup(big).applyAsInt(10));
        assertEquals(-1, Util.createIndexLookup(big).applyAsInt(11));
        assertEquals("b", Util.findNextInIterable(List.of("a", "b", "c"), "a"));
        assertEquals("a", Util.findNextInIterable(List.of("a", "b", "c"), "c"), "마지막 다음은 처음");
        assertEquals("a", Util.findNextInIterable(List.of("a", "b", "c"), null));
        assertTrue(Util.isSymmetrical(3, 2, List.of(1, 2, 1, 3, 4, 3)));
        assertFalse(Util.isSymmetrical(3, 2, List.of(1, 2, 1, 3, 4, 5)));
        String emoji = "a😀b";
        assertEquals(3, Util.offsetByCodepoints(emoji, 1, 1));
        assertEquals(1, Util.offsetByCodepoints(emoji, 3, -1));
        assertEquals(emoji.length(), Util.offsetByCodepoints(emoji, 0, 99));
        assertEquals(0, Util.offsetByCodepoints(emoji, 4, -99));
    }

    // ------------------------------------------------------------------ WorldData·managedBlock·setBiome

    @Test
    void worldDataIsTheDefaultWorldsLevelData() {
        WorldData data = MinecraftServer.getServer().getWorldData();
        assertNotNull(data.getLevelName(), "서버 월드 이름");
        assertSame(data, data.overworldData(), "서버 월드의 레벨 데이터는 주 레벨 데이터와 같은 객체");
        assertTrue(data.overworldData() instanceof PrimaryLevelData);
        ServerLevelData sld = data.overworldData();
        assertEquals(bukkit.getWorld().isHardcore(), data.isHardcore());
        assertEquals(bukkit.getWorld().hasStorm(), sld.isRaining());
        assertEquals(bukkit.getWorld().getFullTime(), sld.getDayTime());
        assertEquals(bukkit.getWorld().getSeed(), data.worldGenOptions().seed());
        assertTrue(data.isFlatWorld(), "시험 서버의 월드는 슈퍼플랫 생성기(생성기 종류로 본다)");
        assertFalse(data.isDebugWorld());
        assertEquals(WorldData.ANVIL_VERSION_ID, data.getVersion());
        assertFalse(data.wasModded());
        assertTrue(sld.isInitialized());
        var spawn = bukkit.getWorld().getSpawnLocation();
        BlockPos sp = sld.getSpawnPos();
        assertEquals(spawn.getBlockX(), sp.getX());
        assertEquals(spawn.getBlockZ(), sp.getZ());
        assertSame(data, level.serverLevelData.overworldData());
    }

    @Test
    void scratchLevelDataKeepsWhatWasWritten() {
        var settings = ((PrimaryLevelData) MinecraftServer.getServer().getWorldData()).getLevelSettings();
        var mine = new PrimaryLevelData(new net.minecraft.world.level.LevelSettings("mine", net.minecraft.world.level.GameType.CREATIVE, false,
                net.minecraft.world.Difficulty.HARD, true, settings.gameRules(), settings.getDataConfiguration()),
                new net.minecraft.world.level.levelgen.WorldOptions(42L, true, false), PrimaryLevelData.SpecialWorldProperty.NONE, com.mojang.serialization.Lifecycle.stable());
        assertEquals("mine", mine.getLevelName());
        assertEquals(net.minecraft.world.level.GameType.CREATIVE, mine.getGameType());
        assertEquals(net.minecraft.world.Difficulty.HARD, mine.getDifficulty());
        assertTrue(mine.isAllowCommands());
        mine.setDayTime(6000L);
        mine.setRaining(true);
        mine.setSpawn(new BlockPos(1, 2, 3), 90f);
        assertEquals(6000L, mine.getDayTime());
        assertTrue(mine.isRaining());
        assertEquals(new BlockPos(1, 2, 3), mine.getSpawnPos());
        assertEquals(90f, mine.getSpawnAngle());
        assertEquals(42L, mine.worldGenOptions().seed());
    }

    @Test
    void managedBlockWaitsOffTheTickThreadAndRefusesOnIt() throws Exception {
        MinecraftServer s = MinecraftServer.getServer();
        AtomicBoolean flag = new AtomicBoolean();
        Thread flipper = new Thread(() -> {
            try {
                Thread.sleep(30);
            } catch (InterruptedException ignored) {
                // 시험 끝
            }
            flag.set(true);
        });
        flipper.start();
        long t0 = System.nanoTime();
        s.managedBlock(flag::get);   // 시험 스레드는 틱 스레드가 아니다
        assertTrue(flag.get());
        assertTrue((System.nanoTime() - t0) / 1_000_000 >= 25);
        flipper.join();
        s.managedBlock(() -> true);
        assertThrows(ThreadOwnershipException.class, () -> owner(() -> {
            s.managedBlock(() -> false);
            return null;
        }));
        assertTrue(s instanceof net.minecraft.util.thread.BlockableEventLoop<?>);
    }

    @Test
    void serverImplementsTheVanillaInterfaces() {
        MinecraftServer ms = MinecraftServer.getServer();
        assertTrue(ms instanceof net.minecraft.server.ServerInfo && ms instanceof net.minecraft.commands.CommandSource
                && ms instanceof net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter
                && ms instanceof net.minecraft.util.profiling.metrics.ProfilerMeasured && ms instanceof net.minecraft.util.thread.ProcessorHandle<?>);
        assertTrue(ms.acceptsSuccess() && ms.acceptsFailure() && ms.shouldInformAdmins());
        assertFalse(ms.alwaysAccepts());
        assertEquals(bukkit.getServer().getMaxPlayers(), ms.getMaxPlayers());
        // ProcessorHandle#tell 은 전역 스레드로 보낸다 — 한 틱 돌리면 도착한다
        java.util.concurrent.atomic.AtomicBoolean ran = new java.util.concurrent.atomic.AtomicBoolean();
        ms.tell(new net.minecraft.server.TickTask(0, () -> ran.set(true)));
        server.tick(2);
        assertTrue(ran.get());
        // ask 는 메시지 공급자에 응답 창구를 주고 그 응답을 미래로 돌려준다
        var f = net.minecraft.util.thread.ProcessorHandle.<String>of("t", x -> {}).<String>ask(reply -> { reply.tell("pong"); return "ping"; });
        assertEquals("pong", f.getNow(null), "응답 창구에 답을 주면 ask 의 미래가 그 값으로 끝난다");
    }

    @Test
    void levelChunkSetBiomeWritesTheQuartCell() {
        BlockPos bp = new BlockPos(at.x(), at.y(), at.z());
        LevelChunk chunk = level.getChunkAt(bp);
        var reg = net.minecraft.core.RegistryAccess.SERVER.registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        Holder<net.minecraft.world.level.biome.Biome> desert = reg.getHolderOrThrow(Biomes.DESERT);
        int qx = at.x() >> 2, qy = at.y() >> 2, qz = at.z() >> 2;
        owner(() -> {
            chunk.setBiome(qx, qy, qz, desert);
            assertEquals("minecraft:desert", chunk.getNoiseBiome(qx, qy, qz).getRegisteredName());
            return null;
        });
        assertEquals(org.bukkit.block.Biome.DESERT, owner(() -> bukkit.getWorld().getBiome(at.x(), at.y(), at.z())));
        Holder<net.minecraft.world.level.biome.Biome> plains = reg.getHolderOrThrow(Biomes.PLAINS);
        owner(() -> {
            chunk.setBiome(qx, qy, qz, plains);
            return null;
        });
        assertEquals("minecraft:plains", owner(() -> chunk.getNoiseBiome(qx, qy, qz).getRegisteredName()));
    }
}
