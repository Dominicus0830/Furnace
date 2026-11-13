// T-055 결과 동일 장면: 예약 틱(블록·유체)이 많이 도는 장면을 틱마다 해시해 금 파일(scheduled-ticks-scene)로 굳힌다.
// 금 파일은 예약 틱 저장소를 청크별 컨테이너로 바꾸기 전(기준 커밋 dec5d2c7)의 BlockTicks 로 만들었다 — 바뀌면 실행 순서가 바뀐 것이다.
// 장면: 물·용암 흐름(청크 경계를 건넘, 만나면 돌), 레드스톤(관측기 시계·중계기 지연 1~4·비교기·램프·피스톤)을 켜고 끄기,
// 몇 틱 동안 한 청크를 틱 범위 밖으로(미뤄 둔 예약), 주기적으로 한 청크의 예약을 저장·꺼냈다가 다시 걸기(청크 내리기·다시 올리기 흉내).
// 틱마다: 블록 상태 해시 + 예약 수. 25틱마다: 모든 청크의 저장 목록(block_ticks·fluid_ticks 순서 그대로). 끝: 레벨 난수 다음 값.
package org.domi.furnace.server.block;

import java.util.function.LongPredicate;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.identity.SceneHash;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ScheduledTickSceneGoldenTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static final int Y = -60;
    static final int TICKS = 300;

    @Test
    void scheduledTickSceneMatchesGolden() {
        SceneHash.assertGolden("scheduled-ticks-scene", ScheduledTickSceneGoldenTest::scene);
    }

    static int st(String name) { return B.defaultState(name); }

    static int with(int state, String prop, String value) { return B.prop(prop).with(state, value); }

    static void scene(SceneHash.Recorder rec) {
        BlockPlacementTest.TestLevel level = new BlockPlacementTest.TestLevel();
        ((LegacyRandomSource) level.random()).setSeed(0x7055L);
        // 물: 청크 (0,0)·(1,0) 경계 근처. 용암: 물과 만나도록 동쪽에(돌·조약돌이 생긴다)
        level.setBlock(new Pos(13, Y, 6), st("minecraft:water"), 3);
        level.setBlock(new Pos(22, Y, 6), st("minecraft:lava"), 3);
        level.setBlock(new Pos(4, Y, 26), st("minecraft:water"), 3);
        // 물이 더 많이(예약이 청크 여럿에 몰리게): 청크 경계 x=16·z=16·z=32 근처
        for (int[] w : new int[][] {{31, 15}, {17, 30}, {40, 40}, {2, 40}, {30, 28}}) level.setBlock(new Pos(w[0], Y, w[1]), st("minecraft:water"), 3);
        level.setBlock(new Pos(36, Y, 20), st("minecraft:lava"), 3);
        // 레드스톤: 서쪽 청크들(-2..-1)에서. 관측기 시계 — 서로 마주 보는 관측기 둘
        level.setBlock(new Pos(-28, Y, -28), with(st("minecraft:observer"), "facing", "east"), 3);
        level.setBlock(new Pos(-27, Y, -28), with(st("minecraft:observer"), "facing", "west"), 3);
        level.setBlock(new Pos(-29, Y, -28), st("minecraft:redstone_lamp"), 3);
        level.setBlock(new Pos(-26, Y, -28), st("minecraft:redstone_lamp"), 3);
        // 켜고 끄는 원천(redstone_block 자리) → 동쪽으로 중계기 사슬(지연 1~4)이 청크 경계 x=-16 을 건너 램프·피스톤으로
        Pos source = new Pos(-30, Y, -10);
        for (int i = 0; i < 12; i++) {
            int x = -29 + i * 2;
            int rep = with(with(st("minecraft:repeater"), "facing", "west"), "delay", Integer.toString(1 + i % 4));   // 입력은 서쪽(facing 반대)
            level.setBlock(new Pos(x, Y, -10), rep, 3);
            level.setBlock(new Pos(x + 1, Y, -10), st("minecraft:redstone_wire"), 3);
            level.setBlock(new Pos(x + 1, Y, -9), st("minecraft:redstone_lamp"), 3);
        }
        // 가지: 비교기·관측기·피스톤
        level.setBlock(new Pos(-29, Y, -11), with(st("minecraft:comparator"), "facing", "south"), 3);
        level.setBlock(new Pos(-29, Y, -12), st("minecraft:redstone_lamp"), 3);
        level.setBlock(new Pos(-20, Y, -11), with(st("minecraft:observer"), "facing", "south"), 3);
        level.setBlock(new Pos(-20, Y, -12), with(st("minecraft:sticky_piston"), "facing", "north"), 3);
        level.setBlock(new Pos(-20, Y, -13), st("minecraft:stone"), 3);
        level.setBlock(new Pos(-8, Y, -11), with(st("minecraft:piston"), "facing", "north"), 3);
        level.setBlock(new Pos(-8, Y, -12), st("minecraft:slime_block"), 3);
        level.setBlock(new Pos(-8, Y, -13), st("minecraft:stone"), 3);
        // 우선순위가 다른 예약이 같은 틱에 몰리게: 같은 원천에서 남쪽으로도 짧은 중계기 둘
        level.setBlock(new Pos(-30, Y, -9), with(with(st("minecraft:repeater"), "facing", "north"), "delay", "2"), 3);
        level.setBlock(new Pos(-30, Y, -8), st("minecraft:redstone_lamp"), 3);

        int deferChunkX = -2, deferChunkZ = -1;   // 원천·사슬 앞쪽이 든 청크
        long deferKey = ChunkPredicateMemo.chunkKey(deferChunkX, deferChunkZ);
        for (int t = 0; t < TICKS; t++) {
            if (t % 13 == 0) level.setBlock(source, st(t % 26 == 0 ? "minecraft:redstone_block" : "minecraft:air"), 3);
            if (t == 150) level.setBlock(new Pos(10, Y, 26), st("minecraft:lava"), 3);
            if (t % 50 == 25) {   // 청크 (1,0) 을 내렸다 다시 올리는 흉내: 저장하며 꺼내고 곧바로 다시 건다
                NbtCompound root = new NbtCompound();
                level.saveChunkTicks(1, 0, root, true);
                level.loadChunkTicks(root);
            }
            boolean deferred = t >= 100 && t < 112;
            LongPredicate ticking = deferred ? k -> k != deferKey : null;
            level.runScheduledTicks(ticking);
            level.runBlockEvents(ticking);
            level.tickBlockEntities(ticking == null ? k -> true : ticking);
            rec.line("tick " + t + " blocks", blocksHash(level));
            rec.text("tick " + t + " scheduled " + level.blockTicks().size());
            if (t % 25 == 24) rec.line("tick " + t + " saved", savedHash(level));
        }
        rec.text("random next " + level.random().nextLong());
    }

    static IdentityHash blocksHash(BlockPlacementTest.TestLevel level) {
        IdentityHash h = new IdentityHash();
        for (int x = -32; x < 48; x++)
            for (int z = -32; z < 48; z++)
                for (int y = Y - 1; y <= Y + 3; y++) h.add(level.getBlockState(new Pos(x, y, z)));
        return h;
    }

    /** 모든 청크의 저장 목록(꺼내지 않음) — 목록 순서와 남은 틱·우선순위까지. */
    static IdentityHash savedHash(BlockPlacementTest.TestLevel level) {
        IdentityHash h = new IdentityHash();
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                NbtCompound root = new NbtCompound();
                level.saveChunkTicks(cx, cz, root, false);
                for (String key : new String[] {"block_ticks", "fluid_ticks"}) {
                    h.add(key);
                    for (Object o : ((NbtList) root.tags().get(key)).items()) {
                        NbtCompound e = (NbtCompound) o;
                        h.add(e.getString("i")).add(e.getIntOr0("x")).add(e.getIntOr0("y")).add(e.getIntOr0("z"))
                                .add(e.getIntOr0("t")).add(e.getIntOr0("p"));
                    }
                }
            }
        }
        return h;
    }
}
