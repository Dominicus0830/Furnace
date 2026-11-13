package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-072: Block.getType 빠른 길(상태 번호별 공유 BlockData + 종류 → Material 표)이 옛 길(문자열 → Material.matchMaterial)과 모든 블록 상태에서 같은 값을 준다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlockTypeTableTest {

    FurnaceTestServer server;
    BlockPos pos;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        server.tick(3);
        pos = new BlockPos(5, 200, 5);
        server.loadChunk(pos.chunkX(), pos.chunkZ());
    }

    @AfterAll
    void stop() { server.close(); }

    /** 모든 상태 번호: 표를 거친 Compat.material == matchMaterial(없으면 둘 다 같은 UnsupportedFeature). 두 번째 호출(표 적중)도 같다. */
    @Test
    void materialTableMatchesMatchMaterialForEveryState() {
        var types = server.api().blockTypes();
        int n = types.stateCount();
        assertTrue(n > 26_000, "1.21.1 은 26684 개 상태");
        int checked = 0, unsupported = 0;
        for (int i = 0; i < n; i++) {
            String type = types.byStateId(i).orElseThrow().type();
            Material want = Material.matchMaterial(type);
            for (int pass = 0; pass < 2; pass++) {
                if (want == null) {
                    assertThrows(UnsupportedFeature.class, () -> Compat.material(type), type);
                } else {
                    assertSame(want, Compat.material(type), "상태 " + i + " " + type + " 회차 " + pass);
                }
            }
            if (want == null) unsupported++;
            checked++;
        }
        assertEquals(n, checked);
        System.out.println("T-072 material table: states=" + checked + " unsupported=" + unsupported);
    }

    /** 표 밖 철자(matchMaterial 이 받아주는 것)도 옛 결과와 같다 — 표에는 담기지 않고 매번 같은 값을 준다. */
    @Test
    void alternateSpellingsStillResolve() {
        for (String s : new String[] {"STONE", "minecraft:STONE", " stone", "minecraft:stone", "stone"}) {
            Material want = Material.matchMaterial(s);
            assertNotNull(want, s);
            assertSame(want, Compat.material(s), s);
            assertSame(want, Compat.material(s), s);
        }
        assertThrows(UnsupportedFeature.class, () -> Compat.material("minecraft:not_a_block"));
        assertThrows(UnsupportedFeature.class, () -> Compat.material("minecraft:not_a_block"));
    }

    /** 실제 세상 길: 모든 상태를 한 칸에 놓고 읽는다 — Block.getType() == matchMaterial(Furnace Block.type()), getBlockData 의 종류도 같다. */
    @Test
    void getTypeMatchesTheOldPathAfterWritingEveryState() {
        var types = server.api().blockTypes();
        int n = types.stateCount();
        var dim = "minecraft:overworld";
        int[] mismatches = {0};
        int[] placed = {0};
        server.onRegion(pos, () -> {
            var furnaceBlock = server.api().world(dim).block(pos);
            org.bukkit.block.Block bukkit = Compat.block(dim, pos);
            for (int i = 0; i < n; i++) {
                var data = types.byStateId(i).orElseThrow();
                try {
                    if (!furnaceBlock.set(data)) continue;
                } catch (RuntimeException e) {
                    continue;   // 놓을 수 없는 상태(데이터 팩·특수 블록)는 건너뛴다
                }
                placed[0]++;
                String readType = furnaceBlock.type();   // 옛 길의 입력 — 놓은 직후 세상에서 읽은 값
                Material want = Material.matchMaterial(readType);
                if (want == null) {
                    try {
                        bukkit.getType();
                        mismatches[0]++;
                    } catch (UnsupportedFeature expected) {
                        // 옛 길과 같다
                    }
                } else if (bukkit.getType() != want || ((CompatBlockData) bukkit.getBlockData()).getMaterial() != want) {
                    mismatches[0]++;
                }
            }
            furnaceBlock.set(org.domi.furnace.api.world.BlockData.AIR);
            return null;
        });
        System.out.println("T-072 getType over world: placed=" + placed[0] + " of " + n + ", mismatches=" + mismatches[0]);
        assertTrue(placed[0] > 20_000, "대부분의 상태를 놓아 봤다: " + placed[0]);
        assertEquals(0, mismatches[0]);
    }
}
