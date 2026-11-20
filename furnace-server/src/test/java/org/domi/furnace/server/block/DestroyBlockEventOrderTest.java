// T-224: Level#destroyBlock 의 BlockDestroyEvent 순서. 구독자가 없으면 바닐라(효과 → 떨굼 안에서 경험치), 있으면 Paper 1.21.1 Level#destroyBlock
// (경험치 먼저 getExpDrop → 이벤트 → 효과 블록(불이 아니면 2001) → 떨굼(dropExperience=false) → popExperience → setBlock → block_destroy).
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.plugin.GameEvents;
import org.junit.jupiter.api.Test;

class DestroyBlockEventOrderTest {

    private static final int SPAWNER = B.defaultState("minecraft:spawner"),
            DIRT = B.defaultState("minecraft:dirt"), FIRE = B.defaultState("minecraft:fire"), AIR = B.defaultState("minecraft:air"),
            WATER = B.defaultState("minecraft:water");
    private static final Pos P = new Pos(3, -55, 3);
    private static final long SEED = 20261120L;

    /** 이벤트 쪽을 흉내 낸다: 받은 값을 적고 answer 로 답한다(null 이면 구독자 없음). */
    static final class Level extends BlockPlacementTest.TestLevel {
        final List<String> log = new ArrayList<>();
        java.util.function.Function<int[], GameEvents.DestroyOutcome> answer;
        /** 레벨 난수를 고정 씨앗으로 — 경험치 표본을 미리 계산해 비교한다. */
        RandomSource rnd = RandomSource.create(SEED);

        @Override
        public RandomSource random() { return rnd; }

        @Override
        public GameEvents gameEvents() {
            if (answer == null) return super.gameEvents();
            return new GameEvents() {
                @Override public boolean wantsBlockDestroy() { return true; }
                @Override public DestroyOutcome blockDestroy(String dimension, Pos pos, int oldState, int newState, int exp, boolean willDrop) {
                    log.add("event:" + BL.describe(oldState) + ">" + BL.describe(newState) + ":xp" + exp + ":drop" + willDrop);
                    return answer.apply(new int[] {oldState, newState, exp, willDrop ? 1 : 0});
                }
            };
        }

        @Override
        public void levelEvent(BlockActor except, int type, Pos pos, int data) {
            log.add("level:" + type + ":" + BL.describe(data));
            super.levelEvent(except, type, pos, data);
        }

        @Override
        public void popExperience(Pos pos, int amount) { log.add("xp:" + amount); }

        @Override
        public void gameEventFrom(String event, Pos pos, Object entity, int state) {
            log.add("game:" + event);
            super.gameEventFrom(event, pos, entity, state);
        }
    }

    private static final Blocks BL = Blocks.get();

    /** 같은 씨앗으로 스포너 경험치(15 + nextInt(15) + nextInt(15))를 미리 계산한다. */
    private static int spawnerXp() {
        var r = RandomSource.create(SEED);
        return 15 + r.nextInt(15) + r.nextInt(15);
    }

    private static Level withSpawner() {
        Level l = new Level();
        l.setBlock(P, SPAWNER, 3);
        l.log.clear();
        l.rnd = RandomSource.create(SEED);   // 놓기가 난수를 썼더라도 부수기 직전부터 같은 씨앗
        return l;
    }

    @Test
    void withoutSubscriberKeepsVanillaOrder() {
        Level l = withSpawner();
        int xp = spawnerXp();
        assertTrue(l.destroyBlock(P, true));
        assertEquals(List.of("level:2001:minecraft:spawner", "xp:" + xp, "game:minecraft:block_destroy"), l.log);
        assertEquals(AIR, l.getBlockState(P));
    }

    @Test
    void withSubscriberSamplesExperienceFirstLikePaper() {
        Level l = withSpawner();
        int xp = spawnerXp();
        l.answer = a -> new GameEvents.DestroyOutcome(a[0], true, a[3] == 1, a[2]);
        assertTrue(l.destroyBlock(P, true));
        assertEquals(List.of("event:minecraft:spawner>minecraft:air:xp" + xp + ":droptrue", "level:2001:minecraft:spawner", "xp:" + xp,
                "game:minecraft:block_destroy"), l.log, "떨굼은 경험치 없이(dropExperience=false), 뽑아 둔 경험치를 그 뒤에 낸다");
    }

    @Test
    void subscriberDrawsExperienceEvenWithoutDrops() {
        // Paper 는 drop 이 거짓이어도 getExpDrop(…, true) 를 부른다 — 레벨 난수 두 번
        Level vanilla = withSpawner(), paper = withSpawner();
        paper.answer = a -> new GameEvents.DestroyOutcome(a[0], true, a[3] == 1, a[2]);
        assertTrue(vanilla.destroyBlock(P, false));
        assertTrue(paper.destroyBlock(P, false));
        assertEquals(List.of("level:2001:minecraft:spawner", "game:minecraft:block_destroy"), vanilla.log, "바닐라는 경험치를 뽑지 않는다");
        assertEquals(List.of("event:minecraft:spawner>minecraft:air:xp" + spawnerXp() + ":dropfalse", "level:2001:minecraft:spawner",
                "game:minecraft:block_destroy"), paper.log);
        var fresh = RandomSource.create(SEED);
        fresh.nextInt(15);
        fresh.nextInt(15);
        long expected = fresh.nextLong();
        assertEquals(expected, paper.random().nextLong(), "구독자가 있으면 난수 두 번을 쓴 뒤");
        assertNotEquals(expected, vanilla.random().nextLong(), "구독자가 없으면 난수를 쓰지 않는다");
    }

    @Test
    void cancelLeavesBlockAndDoesNothing() {
        Level l = withSpawner();
        l.answer = a -> null;
        assertFalse(l.destroyBlock(P, true));
        assertEquals(SPAWNER, l.getBlockState(P));
        assertEquals(1, l.log.size(), "이벤트뿐: 효과·떨굼·게임 이벤트 없음 — " + l.log);
        assertNotNull(l.getBlockEntity(P), "블록 엔티티도 그대로");
    }

    @Test
    void outcomeChangesEffectDropsAndExperience() {
        Level l = withSpawner();
        l.answer = a -> new GameEvents.DestroyOutcome(DIRT, true, true, 7);
        assertTrue(l.destroyBlock(P, false));
        assertEquals("level:2001:minecraft:dirt", l.log.get(1), "효과는 바꾼 블록");
        assertEquals("xp:7", l.log.get(2), "떨굼을 켜면 바꾼 경험치를 낸다");

        l = withSpawner();
        l.answer = a -> new GameEvents.DestroyOutcome(a[0], false, false, 99);
        assertTrue(l.destroyBlock(P, true));
        assertEquals(2, l.log.size(), "효과 끄기·떨굼 끄기: 이벤트와 게임 이벤트만 — " + l.log);

        l = withSpawner();
        l.answer = a -> new GameEvents.DestroyOutcome(FIRE, true, true, 0);
        assertTrue(l.destroyBlock(P, true));
        assertEquals(List.of("game:minecraft:block_destroy"), l.log.subList(1, l.log.size()), "불 효과는 내지 않고, 경험치 0 은 구슬 없음");
        assertEquals(AIR, l.getBlockState(P));
    }

    @Test
    void eventSeesFluidLeftoverAndBlockEndsAsIt() {
        Level l = new Level();
        int slab = B.defaultState("minecraft:oak_slab");
        int wet = B.prop("waterlogged").with(slab, true);
        l.setBlock(P, wet, 3);
        l.log.clear();
        l.answer = a -> new GameEvents.DestroyOutcome(a[0], true, a[3] == 1, a[2]);
        assertTrue(l.destroyBlock(P, false));
        assertTrue(l.log.get(0).startsWith("event:minecraft:oak_slab[") && l.log.get(0).contains(">minecraft:water[level=0]:xp0"), l.log.get(0));
        assertEquals(WATER, l.getBlockState(P));
    }
}
