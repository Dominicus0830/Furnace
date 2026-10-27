// 스컬크 촉매·퍼짐 시험: 전하 커서 한 번 갱신의 난수 호출 순서(방향 섞기 6 → 이웃 섞기 18)와 결과, 같은 시드 재현,
// 촉매의 entity_die 구독(경험치 → 커서, bloom 8틱), 커서 저장 왕복.
package org.domi.furnace.server.block.types;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.block.entity.BlockEntities;
import org.domi.furnace.server.block.entity.SculkCatalystBlockEntity;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SculkSpreaderTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static final class Lvl extends BlockPlacementTest.TestLevel {
        long time = 100;
        final LevelServices svc = new LevelServices.Delegating(LevelServices.DEFAULT) {
            @Override public long gameTime() { return time; }
        };
        @Override protected LevelServices services() { return svc; }

        void step() {
            time++;
            runScheduledTicks();
            tickBlockEntities(k -> true);
        }
    }

    /** 슈퍼플랫: 풀 블록 윗면이 y=-61, 그 위 -60 은 공기. */
    static final Pos AIR = new Pos(3, -60, 3), ROOT = new Pos(0, -60, 0);

    @Test
    void firstUpdatePlacesVeinThenSculkAndConsumesRandomInOrder() {
        Lvl l = new Lvl();
        long seed = 12345L;
        SculkSpreader sp = SculkSpreader.createLevelSpreader();
        sp.addCursors(AIR, 5);
        RandomSource r = RandomSource.create(seed);
        sp.updateCursors(l, ROOT, r, true);
        // 공기 칸(DEFAULT, facings 없음) → 같은 칸 맥(아래 면) → 맥 동작: 섞은 방향으로 아래 풀을 스컬크로, 전하 -1 → 이웃 섞기.
        assertTrue(B.is(l.getBlockState(AIR.below()), B.id("sculk")), "풀이 스컬크로");
        assertEquals(1, sp.getCursors().size());
        var c = sp.getCursors().get(0);
        assertEquals(4, c.getCharge());
        assertEquals(1, c.getUpdateDelay());
        // 난수 소비 대조: Direction.allShuffled(nextInt 6..2) 다음 Util.shuffledCopy(18칸, nextInt 18..2) 뿐이다.
        RandomSource ref = RandomSource.create(seed);
        for (int i = 6; i > 1; i--) ref.nextInt(i);
        for (int i = 18; i > 1; i--) ref.nextInt(i);
        assertEquals(ref.nextLong(), r.nextLong(), "난수 호출 순서가 같다");
        // 커서는 이웃의 SculkBehaviour 블록(맥·스컬크)으로 옮겨 갔다.
        Pos p = c.getPos();
        assertNotEquals(AIR, p);
        assertTrue(SculkBehaviour.isSculkBehaviour(l.getBlockState(p)), "옮긴 칸은 스컬크·맥");
        assertNotNull(c.getFacingData());
    }

    @Test
    void sameSeedReproducesSpreadAndChargeDecays() {
        String[] a = runSpread(777L), b = runSpread(777L);
        assertArrayEquals(a, b);
    }

    private static String[] runSpread(long seed) {
        Lvl l = new Lvl();
        SculkSpreader sp = SculkSpreader.createLevelSpreader();
        sp.addCursors(AIR, 30);
        RandomSource r = RandomSource.create(seed);
        int sculk = 0;
        for (int t = 0; t < 400 && !sp.getCursors().isEmpty(); t++) sp.updateCursors(l, ROOT, r, true);
        assertTrue(sp.getCursors().isEmpty(), "전하는 결국 다 쓴다");
        StringBuilder sb = new StringBuilder();
        for (int x = -8; x <= 14; x++)
            for (int z = -8; z <= 14; z++)
                for (int y = -62; y <= -58; y++) {
                    int s = l.getBlockState(new Pos(x, y, z));
                    if (B.is(s, B.id("sculk"))) sculk++;
                    if (!B.isAir(s) && y >= -61) sb.append(x).append(',').append(y).append(',').append(z).append('=').append(s).append(';');
                }
        assertTrue(sculk > 1, "여러 칸이 스컬크가 됐다: " + sculk);
        return new String[] {sb.toString(), Long.toString(r.nextLong())};
    }

    @Test
    void addCursorsSplitsByMaxChargeAndCapsAt32() {
        SculkSpreader sp = SculkSpreader.createLevelSpreader();
        sp.addCursors(ROOT, 2500);
        assertEquals(3, sp.getCursors().size());
        assertEquals(1000, sp.getCursors().get(0).getCharge());
        assertEquals(500, sp.getCursors().get(2).getCharge());
        sp.addCursors(ROOT, 100_000);
        assertEquals(32, sp.getCursors().size());
    }

    @Test
    void catalystConsumesDeathExperienceAndBlooms() {
        Lvl l = new Lvl();
        l.setBlock(ROOT, B.defaultState(B.id("sculk_catalyst")), 3);
        var be = assertInstanceOf(SculkCatalystBlockEntity.class, l.getBlockEntity(ROOT));
        Cow cow = new Cow(l, l.nextEntityId());
        cow.moveTo(2.5, -60.0, 0.5, 0, 0);
        l.addFreshEntity(cow);
        cow.die(new Entity.Damage("minecraft:generic", false, false, false));
        assertTrue(cow.wasExperienceConsumed(), "촉매가 경험치를 가져갔다");
        var cursors = be.getSculkSpreader().getCursors();
        assertEquals(1, cursors.size());
        assertEquals(new Pos(2, -60, 0), cursors.get(0).getPos());
        int charge = cursors.get(0).getCharge();
        assertTrue(charge >= 1 && charge <= 3, "소 경험치 1..3: " + charge);
        var bloom = B.prop("bloom");
        assertTrue(bloom.is(l.getBlockState(ROOT)), "bloom 켜짐");
        for (int i = 0; i < 7; i++) l.step();
        assertTrue(bloom.is(l.getBlockState(ROOT)));
        l.step();
        assertFalse(bloom.is(l.getBlockState(ROOT)), "8틱 뒤 꺼짐");

        // 반경 8 밖은 듣지 않는다.
        Cow far = new Cow(l, l.nextEntityId());
        far.moveTo(9.5, -60.0, 0.5, 0, 0);
        l.addFreshEntity(far);
        far.die(new Entity.Damage("minecraft:generic", false, false, false));
        assertFalse(far.wasExperienceConsumed());
    }

    @Test
    void cursorsSaveRoundTrip() {
        Lvl l = new Lvl();
        l.setBlock(ROOT, B.defaultState(B.id("sculk_catalyst")), 3);
        var be = (SculkCatalystBlockEntity) l.getBlockEntity(ROOT);
        be.getSculkSpreader().addCursors(AIR, 7);
        be.getSculkSpreader().addCursors(new Pos(1, -60, 1), 1200);
        be.getSculkSpreader().updateCursors(l, ROOT, RandomSource.create(5L), true);
        var tag = be.saveWithFullMetadata();
        var copy = (SculkCatalystBlockEntity) BlockEntities.loadStatic(ROOT, l.getBlockState(ROOT), tag);
        assertNotNull(copy);
        var a = be.getSculkSpreader().getCursors();
        var b = copy.getSculkSpreader().getCursors();
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).getPos(), b.get(i).getPos());
            assertEquals(a.get(i).getCharge(), b.get(i).getCharge());
            assertEquals(a.get(i).getDecayDelay(), b.get(i).getDecayDelay());
            assertEquals(a.get(i).getUpdateDelay(), b.get(i).getUpdateDelay());
            assertEquals(a.get(i).getFacingData(), b.get(i).getFacingData());
        }
        assertTrue(a.size() >= 2, "7 + 1000 + 200 (합쳐질 수 있다)");
    }

    /** 사용자 버그 C3: 촉매 근처에서 몹이 죽은 뒤 블록 엔티티 틱만으로 스컬크가 땅으로 번진다(전하만큼). */
    @Test
    void mobDeathNearCatalystSpreadsSculkOverTicks() {
        Lvl l = new Lvl();
        l.setBlock(ROOT.below(), B.defaultState(B.id("sculk_catalyst")), 3);
        var be = (SculkCatalystBlockEntity) l.getBlockEntity(ROOT.below());
        org.domi.furnace.server.entity.Zombie z = new org.domi.furnace.server.entity.Zombie(l, l.nextEntityId());
        z.moveTo(3.5, -60.0, 0.5, 0, 0);
        l.addFreshEntity(z);
        z.die(new Entity.Damage("minecraft:generic", false, false, false));
        assertTrue(z.wasExperienceConsumed());
        int charge = be.getSculkSpreader().getCursors().stream().mapToInt(SculkSpreader.ChargeCursor::getCharge).sum();
        assertEquals(5, charge, "좀비 경험치 5");
        for (int t = 0; t < 600; t++) l.step();
        assertTrue(be.getSculkSpreader().getCursors().isEmpty(), "전하를 다 썼다");
        int sculk = 0;
        for (int x = -10; x <= 10; x++)
            for (int zz = -10; zz <= 10; zz++)
                for (int y = -64; y <= -58; y++) if (B.is(l.getBlockState(new Pos(x, y, zz)), B.id("sculk"))) sculk++;
        System.out.println("C3 sculk=" + sculk);
        assertTrue(sculk >= 3, "땅이 여러 칸 스컬크가 됐다: " + sculk);
    }
}
