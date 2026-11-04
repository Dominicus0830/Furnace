// 플레이어 낙하 위치(FallLocation)와 다락문 사다리: 매달린 칸을 저장해 맞은 순간의 블록으로 이름을 정하는가,
// 열린 다락문 바로 아래가 같은 방향 사다리면 매달리는가(바닐라 LivingEntity.trapdoorUsableAsLadder).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.GameMode;
import org.domi.furnace.server.world.PlayerProbe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlayerFallLocationTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static int state(String name) { return B.defaultState(name); }

    private static int with(int s, String prop, String value) { return B.prop(prop).with(s, value); }

    private static int trapdoor(boolean open, String facing) {
        return with(with(state("minecraft:oak_trapdoor"), "open", Boolean.toString(open)), "facing", facing);
    }

    private static int ladder(String facing) { return with(state("minecraft:ladder"), "facing", facing); }

    /** 칸 → 블록 상태(없으면 공기). */
    private static final class Grid implements PlayerProbe.Blocks {
        final Map<Long, Integer> blocks = new HashMap<>();

        void set(int x, int y, int z, int s) { blocks.put(key(x, y, z), s); }

        static long key(int x, int y, int z) { return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF); }

        @Override
        public int stateAt(int x, int y, int z) { return blocks.getOrDefault(key(x, y, z), state("minecraft:air")); }
    }

    private static final PlayerProbe PROBE = new PlayerProbe(BlockRegistry.load("blocks/1.21.1.txt"));

    @Test
    void openTrapdoorAboveSameFacingLadderIsClimbable() {
        Grid g = new Grid();
        g.set(0, 10, 0, ladder("north"));
        g.set(0, 11, 0, trapdoor(true, "north"));
        assertEquals("ladder", PROBE.climbable(g, 0.5, 11.2, 0.5), "열린 다락문 + 같은 방향 사다리 → ladder");

        g.set(0, 11, 0, trapdoor(false, "north"));
        assertNull(PROBE.climbable(g, 0.5, 11.2, 0.5), "닫힌 다락문은 아니다");

        g.set(0, 11, 0, trapdoor(true, "east"));
        assertNull(PROBE.climbable(g, 0.5, 11.2, 0.5), "방향이 다르면 아니다");

        g.set(0, 11, 0, trapdoor(true, "north"));
        g.set(0, 10, 0, state("minecraft:stone"));
        assertNull(PROBE.climbable(g, 0.5, 11.2, 0.5), "아래가 사다리가 아니면 아니다");

        g.set(0, 10, 0, ladder("west"));
        g.set(0, 11, 0, with(with(state("minecraft:iron_trapdoor"), "open", "true"), "facing", "west"));
        assertEquals("ladder", PROBE.climbable(g, 0.5, 11.2, 0.5), "철 다락문도 TrapDoorBlock");
        g.set(0, 11, 0, with(with(state("minecraft:waxed_oxidized_copper_trapdoor"), "open", "true"), "facing", "west"));
        assertEquals("ladder", PROBE.climbable(g, 0.5, 11.2, 0.5), "구리 다락문도 TrapDoorBlock");

        g.set(0, 11, 0, state("minecraft:vine"));
        assertEquals("vines", PROBE.climbable(g, 0.5, 11.2, 0.5), "#climbable 은 그대로");
    }

    private Player player;
    private PlayerVitalsTest.TestEnv env;

    /** 사다리 칸 (0,100,0) 에 매달렸다가 아래 칸으로 떨어져 땅에 닿아 죽는다. */
    private void climbThenFall(String climbName, PlayerProbe.Blocks lookup) {
        player = new Player(7, UUID.randomUUID(), "Steve", new EmbeddedChannel());
        env = new PlayerVitalsTest.TestEnv();
        PlayerVitals v = player.vitals();
        v.env(env);
        v.loadGameTypes(null, GameMode.SURVIVAL, false);
        v.setBlockLookup(lookup);
        player.applyMove(0.5, 100.2, 0.5, 0, 0, false);
        v.tick(new PlayerVitals.Surroundings(true, false, false, false, climbName, false, -64));
        double y = 100.2;
        boolean ground = false;
        while (y > 60) {
            double ny = Math.max(60, y - 1);
            v.onMove(0, ny - y, 0, ground, false, false, false, false, false, null, "minecraft:air", false);
            y = ny;
        }
        v.onMove(0, 0, 0, false, true, false, false, false, false, null, "minecraft:stone", false);
        assertTrue(v.isDeadOrDying());
    }

    @Test
    void ladderStillThereIsLadder() {
        climbThenFall("ladder", (x, y, z) -> x == 0 && y == 100 && z == 0 ? ladder("north") : state("minecraft:air"));
        assertEquals("death.fell.accident.ladder", env.broadcasts.get(0).translationKey());
    }

    /** 바닐라는 맞은 순간 그 칸의 지금 블록을 본다: 사다리가 부서져 공기면 other_climbable. */
    @Test
    void brokenLadderBecomesOtherClimbable() {
        climbThenFall("ladder", (x, y, z) -> state("minecraft:air"));
        assertEquals("death.fell.accident.other_climbable", env.broadcasts.get(0).translationKey());
    }

    /** 매달렸던 칸에 지금 덩굴이 있으면 vines(이름은 저장하지 않고 칸으로 다시 본다). */
    @Test
    void replacedBlockDecidesName() {
        climbThenFall("ladder", (x, y, z) -> x == 0 && y == 100 && z == 0 ? state("minecraft:vine") : state("minecraft:air"));
        assertEquals("death.fell.accident.vines", env.broadcasts.get(0).translationKey());
    }

    /** 칸을 읽을 수 없으면(주인 region 밖) 매달린 순간의 이름으로 물러선다. */
    @Test
    void unreadableFallsBackToRecordedName() {
        climbThenFall("ladder", (x, y, z) -> -1);
        assertEquals("death.fell.accident.ladder", env.broadcasts.get(0).translationKey());
    }

    /** 다락문 칸에서 떨어지면 ladder(FallLocation 표가 #trapdoors 를 사다리로 친다). */
    @Test
    void trapdoorCellIsLadder() {
        climbThenFall("ladder", (x, y, z) -> x == 0 && y == 100 && z == 0 ? trapdoor(true, "north") : state("minecraft:air"));
        assertEquals("death.fell.accident.ladder", env.broadcasts.get(0).translationKey());
    }
}
