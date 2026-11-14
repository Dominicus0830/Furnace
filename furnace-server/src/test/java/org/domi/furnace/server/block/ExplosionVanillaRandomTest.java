// 폭발이 레벨 난수를 바닐라 1.21.1 서버와 같은 횟수·순서로 쓰는가, 부술 칸 순서(섞기 전 HashSet 순서 + Util.shuffle)가 같은가.
// 바닐라 모형은 1.21.1 소스(Explosion.explode·finalizeExplosion, ServerLevel.explode, Util.shuffle, Vec3i.hashCode)를 그대로 옮긴 것이고,
// 난수는 java.util.Random(바닐라 LegacyRandomSource 의 nextFloat·nextInt(bound) 와 같은 LCG)으로 Furnace 구현과 따로 돌린다.
// 바닐라 서버의 폭발은 레벨 난수를 ① 광선마다 nextFloat 한 번 ② interactsWithBlocks 면 Util.shuffle 만 쓴다:
// 소리는 isClientSide 에서만(서버는 ClientboundExplodePacket 에 실어 보냄), 불은 폭발 자신의 난수(Explosion.random)를 쓴다.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.domi.furnace.server.item.ItemContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExplosionVanillaRandomTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    /** 레벨 난수 씨앗을 정하고 서버 소리 재생을 기록하는 시험 레벨. */
    static final class SeededLevel extends BlockPlacementTest.TestLevel {
        final RandomSource rnd;
        final List<String> sounds = new ArrayList<>();

        SeededLevel(long seed) { rnd = RandomSource.create(seed); }

        @Override public RandomSource random() { return rnd; }

        @Override
        public void playSeededSound(BlockActor except, double x, double y, double z, String sound, String source, float volume, float pitch, long seed) {
            sounds.add(sound);
            super.playSeededSound(except, x, y, z, sound, source, volume, pitch, seed);
        }

        @Override
        public void playSeededSound(BlockActor except, int entityId, double x, double y, double z, String sound, String source,
                                    float volume, float pitch, long seed) {
            sounds.add(sound);
            super.playSeededSound(except, entityId, x, y, z, sound, source, volume, pitch, seed);
        }
    }

    /** 바닐라 BlockPos 모형: Vec3i.hashCode·equals. Comparable 이 아닌 것도 BlockPos 와 같다(HashMap 은 BlockPos 를 비교 가능 키로 보지 않는다). */
    record BP(int x, int y, int z) {
        @Override public int hashCode() { return (y + z * 31) * 31 + x; }
        @Override public boolean equals(Object o) { return o instanceof BP b && b.x == x && b.y == y && b.z == z; }
    }

    /**
     * 공중(모든 칸이 공기)에서 터진 바닐라 폭발의 부술 칸 목록(섞은 뒤)을 모형 난수로 만든다. 공기 칸은 기본·윈드 계산기 모두
     * getBlockExplosionResistance 가 Optional.empty() 라 광선 세기가 걸음마다 0.225 씩만 준다. 공기 칸도 toBlow 에 들어간다.
     */
    static List<BP> vanillaToBlow(Random r, double x, double y, double z, float radius) {
        Set<BP> set = new HashSet<>();
        for (int i = 0; i < 16; i++) for (int j = 0; j < 16; j++) for (int k = 0; k < 16; k++) {
            if (i != 0 && i != 15 && j != 0 && j != 15 && k != 0 && k != 15) continue;
            double dx = (double) ((float) i / 15.0F * 2.0F - 1.0F);
            double dy = (double) ((float) j / 15.0F * 2.0F - 1.0F);
            double dz = (double) ((float) k / 15.0F * 2.0F - 1.0F);
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= len;
            dy /= len;
            dz /= len;
            float f = radius * (0.7F + r.nextFloat() * 0.6F);
            double px = x, py = y, pz = z;
            for (; f > 0.0F; f -= 0.22500001F) {
                if (f > 0.0F) set.add(new BP((int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz)));
                px += dx * 0.30000001192092896;
                py += dy * 0.30000001192092896;
                pz += dz * 0.30000001192092896;
            }
        }
        List<BP> list = new ArrayList<>(set);   // ObjectArrayList.addAll(HashSet) = 그 반복 순서
        for (int n = list.size(); n > 1; n--) {   // Util.shuffle
            int j = r.nextInt(n);
            list.set(n - 1, list.set(j, list.get(n - 1)));
        }
        return list;
    }

    private static List<BP> asBp(List<Pos> l) {
        List<BP> out = new ArrayList<>(l.size());
        for (Pos p : l) out.add(new BP(p.x(), p.y(), p.z()));
        return out;
    }

    @Test
    void destroyingExplosion_usesLevelRandomLikeVanillaServer() {
        long seed = 0x5EED_1234L;
        double x = 8.5, y = 100.5, z = 8.5;
        float radius = 4.0F;
        SeededLevel level = new SeededLevel(seed);
        Explosion e = level.explode(null, x, y, z, radius, false, Explosion.BlockInteraction.DESTROY);

        Random model = new Random(seed);
        List<BP> expected = vanillaToBlow(model, x, y, z, radius);
        assertTrue(expected.size() > 1000, "장면이 섞기를 충분히 시험한다");
        assertEquals(expected, asBp(e.toBlow()), "섞은 뒤 부술 칸 순서가 바닐라와 같다");
        // 바닐라 서버는 소리에 레벨 난수를 쓰지 않는다 → 폭발 뒤 다음 난수가 같다
        assertEquals(model.nextInt(), level.random().nextInt(), "폭발 뒤 레벨 난수 상태가 바닐라와 같다");
    }

    @Test
    void serverDoesNotPlayExplosionSoundSeparately() {
        SeededLevel level = new SeededLevel(1L);
        level.explode(null, 8.5, 100.5, 8.5, 3.0F, false, Explosion.BlockInteraction.DESTROY);
        level.explode(null, 8.5, 100.5, 8.5, 3.0F, false, Explosion.BlockInteraction.KEEP);
        // 클라이언트는 ClientboundExplodePacket 의 소리로 재생한다(ClientPacketListener.handleExplosion → finalizeExplosion)
        assertEquals(List.of(), level.sounds, "서버가 따로 소리 패킷을 보내면 클라이언트가 두 번 듣는다");
    }

    @Test
    void keepExplosion_onlyRaysUseLevelRandom() {
        long seed = 42L;
        SeededLevel level = new SeededLevel(seed);
        level.explode(null, 8.5, 100.5, 8.5, 3.0F, false, Explosion.BlockInteraction.KEEP);
        Random model = new Random(seed);
        for (int i = 0; i < 16 * 16 * 16 - 14 * 14 * 14; i++) model.nextFloat();   // 껍질 광선 1352개
        assertEquals(model.nextInt(), level.random().nextInt());
    }

    @Test
    void triggerBlockExplosion_isShuffledLikeVanilla() {
        // 바닐라는 interactsWithBlocks()(KEEP 이 아님)면 TRIGGER_BLOCK(윈드 차지)도 Util.shuffle 한 뒤 onExplosionHit 을 부른다
        long seed = 77L;
        double x = 8.5, y = 100.5, z = 8.5;
        float radius = 1.2F;
        SeededLevel level = new SeededLevel(seed);
        Explosion e = level.explode(null, x, y, z, radius, new Explosion.WindCalculator(null), 0, 0, "minecraft:entity.wind_charge.wind_burst");

        Random model = new Random(seed);
        List<BP> expected = vanillaToBlow(model, x, y, z, radius);
        assertTrue(expected.size() > 1);
        assertEquals(expected, asBp(e.toBlow()));
        assertEquals(model.nextInt(), level.random().nextInt());
    }

    @Test
    void fireExplosion_doesNotUseLevelRandomForFire() {
        // 바닐라 불 붙이기는 폭발 자신의 난수(Explosion.random)를 쓴다 → 레벨 난수는 불 없는 폭발과 같다
        long seed = 9L;
        double x = 8.5, y = 100.5, z = 8.5;
        SeededLevel level = new SeededLevel(seed);
        level.explode(null, x, y, z, 3.0F, true, Explosion.BlockInteraction.DESTROY);
        Random model = new Random(seed);
        vanillaToBlow(model, x, y, z, 3.0F);
        assertEquals(model.nextInt(), level.random().nextInt());
    }

    @Test
    void keepExplosion_clearsToBlowAfterFinalize() {
        // 바닐라 ServerLevel.explode: interactsWithBlocks() 가 거짓이면 clearToBlow() — 패킷도 빈 목록으로 나간다
        SeededLevel level = new SeededLevel(5L);
        Explosion e = level.explode(null, 8.5, 100.5, 8.5, 3.0F, false, Explosion.BlockInteraction.KEEP);
        assertEquals(List.of(), e.toBlow());
    }

    @Test
    void worldBounds_matchVanillaHorizontalEdges() {
        // 바닐라 Level.isInWorldBoundsHorizontal: -30000000 <= x,z < 30000000
        SeededLevel level = new SeededLevel(1L);
        assertTrue(level.isInWorldBounds(new Pos(-30000000, 0, 0)));
        assertTrue(level.isInWorldBounds(new Pos(0, 0, -30000000)));
        assertFalse(level.isInWorldBounds(new Pos(30000000, 0, 0)));
        assertFalse(level.isInWorldBounds(new Pos(0, 0, 30000000)));
        assertFalse(level.isInWorldBounds(new Pos(-30000001, 0, 0)));
        assertTrue(level.isInWorldBounds(new Pos(29999999, 0, 29999999)));
    }
}
