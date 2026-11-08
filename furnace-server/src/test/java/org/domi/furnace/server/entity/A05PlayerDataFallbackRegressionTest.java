// A05-02 회귀 시험(재현: docs/audit/findings/A05-repro/A05PlayerDataFallbackReproTest.java.txt): 바닐라 PlayerDataStorage.load(1.21.1 eri.b)처럼
// <uuid>.dat 이 없거나 깨졌으면 <uuid>.dat_old 를 읽는다. 저장(PlayerData.write)은 ".dat → .dat_old 이동" 뒤 "임시 → .dat 이동" 이라
// 두 이동 사이에서 꺼지면 .dat 이 없고 .dat_old 만 남는다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class A05PlayerDataFallbackRegressionTest {

    @TempDir Path world;

    private Path dat(UUID id) { return world.resolve("playerdata").resolve(id + ".dat"); }

    private Path old(UUID id) { return world.resolve("playerdata").resolve(id + ".dat_old"); }

    private static ItemStack[] slots() { return new ItemStack[46]; }

    /** 저장 두 번: .dat = x=200, .dat_old = x=100. */
    private void twoSaves(UUID id) {
        PlayerData.save(world, id, 100, 64, 0, 0f, 0f, true, slots());
        PlayerData.save(world, id, 200, 64, 0, 0f, 0f, true, slots());
        assertTrue(Files.exists(dat(id)) && Files.exists(old(id)));
    }

    @Test
    void crashBetweenTheTwoMoves_vanillaReadsDatOld() throws Exception {
        UUID id = UUID.randomUUID();
        twoSaves(id);
        // 세 번째 저장 도중 첫 이동(.dat → .dat_old)까지만 되고 꺼졌다(임시 파일 이동 전) — 그 상태를 그대로 만든다
        Files.move(dat(id), old(id), StandardCopyOption.REPLACE_EXISTING);
        assertFalse(Files.exists(dat(id)));
        PlayerData.Snapshot s = PlayerData.load(world, id);
        System.out.println("[A05-02] 이동 사이 종료 뒤 load: " + (s == null ? "null(새 플레이어)" : "x=" + s.x()));
        assertNotNull(s, "바닐라는 .dat 이 없으면 .dat_old(x=200) 를 읽는다 — Furnace 는 새 플레이어로 시작");
        assertEquals(200, s.x());
    }

    @Test
    void corruptDat_vanillaReadsDatOld() throws Exception {
        UUID id = UUID.randomUUID();
        twoSaves(id);
        Files.write(dat(id), new byte[] {0x1f, (byte) 0x8b, 8, 0, 0, 0});   // 잘린 gzip
        PlayerData.Snapshot s = PlayerData.load(world, id);
        System.out.println("[A05-02] .dat 손상 뒤 load: " + (s == null ? "null(새 플레이어)" : "x=" + s.x()));
        assertNotNull(s, "바닐라는 .dat 이 깨지면 백업 후 .dat_old(x=100) 를 읽는다 — Furnace 는 새 플레이어로 시작");
        assertEquals(100, s.x());
    }

    @Test
    void afterFallback_laterSavesKeepTheLastGoodCopy() throws Exception {
        UUID id = UUID.randomUUID();
        twoSaves(id);
        Files.move(dat(id), old(id), StandardCopyOption.REPLACE_EXISTING);   // .dat_old = x=200 (마지막 좋은 사본)
        PlayerData.Snapshot s = PlayerData.load(world, id);
        assertNotNull(s, "새 플레이어로 시작하지 않는다");
        // 되살린 데이터로 놀다 자동 저장 두 번 — 첫 저장은 .dat 이 없어 .dat_old 를 그대로 두고, 두 번째부터 이어진 데이터가 .dat_old 가 된다
        PlayerData.save(world, id, s.x(), 64, 0, 0f, 0f, true, slots());
        assertEquals(200, posX(old(id)), "첫 저장이 마지막 좋은 사본을 지우지 않는다");
        PlayerData.save(world, id, s.x() + 1, 64, 0, 0f, 0f, true, slots());
        assertEquals(200, posX(old(id)));
        assertEquals(201, posX(dat(id)));
    }

    @Test
    void corruptDat_isQuarantinedSoTheNextSaveDoesNotRotateItOverDatOld() throws Exception {
        UUID id = UUID.randomUUID();
        twoSaves(id);                                                         // .dat x=200, .dat_old x=100
        Files.write(dat(id), new byte[] {0x1f, (byte) 0x8b, 8, 0, 0, 0});   // 잘린 gzip
        // load 없이 바로 저장이 와도(오프라인 PDC 편집·탈것 저장 등) 깨진 .dat 이 .dat_old(x=100)를 덮지 않는다
        PlayerData.save(world, id, 300, 64, 0, 0f, 0f, true, slots());
        assertEquals(300, posX(dat(id)));
        assertEquals(100, posX(old(id)), "마지막 좋은 사본이 깨진 파일로 바뀌면 안 된다");
        assertTrue(Files.exists(world.resolve("playerdata").resolve(id + ".dat.corrupt")), "깨진 원본은 .corrupt 로 남는다");
    }

    @Test
    void bothCorrupt_startsFreshAndKeepsBothOriginals() throws Exception {
        UUID id = UUID.randomUUID();
        twoSaves(id);
        Files.write(dat(id), new byte[] {1, 2, 3});
        Files.write(old(id), new byte[] {4, 5, 6});
        assertNull(PlayerData.load(world, id), "둘 다 못 읽으면 새 플레이어(바닐라와 같다)");
        assertTrue(Files.exists(world.resolve("playerdata").resolve(id + ".dat.corrupt")));
        assertTrue(Files.exists(world.resolve("playerdata").resolve(id + ".dat_old.corrupt")));
    }

    @Test
    void onlyDatOld_countsAsKnownPlayer() throws Exception {
        UUID id = UUID.randomUUID();
        twoSaves(id);
        Files.move(dat(id), old(id), StandardCopyOption.REPLACE_EXISTING);
        assertTrue(PlayerData.exists(world, id), "hasPlayedBefore — load 가 .dat_old 를 읽으므로");
        assertEquals(java.util.List.of(id), PlayerData.knownIds(world));
        assertEquals(200, (Double) PlayerData.readRoot(world, id).getList("Pos").get(0));
    }

    private static double posX(Path f) throws Exception {
        try (var in = Files.newInputStream(f)) {
            return (Double) org.domi.furnace.server.net.io.nbt.NbtIo.readGzip(in).getList("Pos").get(0);
        }
    }
}
