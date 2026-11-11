// A13-02·05·06 수정 시험(재현 시험을 옮긴 것): clone move 가 주크박스 음반을 떨어뜨리지 않음, type=!x 뒤 양성 type 거절, 스폰 범위 오류 키.
package org.domi.furnace.server.audit;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class A13CommandFixTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    Player steve;
    final List<String> out = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        core.world().awaitChunk(new ChunkPos(0, 0));
        core.addConsoleListener(t -> out.add(t.getString()));
        steve = player(10, "Steve");
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private Player player(int id, String name) {
        var p = new Player(id, UUID.nameUUIDFromBytes(name.getBytes()), name, new ConcurrentEmbeddedChannel());
        p.applyMove(0.5, 70, 0.5, 0, 0, true);
        core.world().playerOnline(p);
        return p;
    }

    private int run(String cmd) throws Exception {
        out.clear();
        return core.commands().performPrefixedCommandAsync(CommandSource.console(core).withEntity(steve), cmd).get(10, TimeUnit.SECONDS).result();
    }

    /** A13-02: 바닐라 clone move 는 원본을 비우기 전 Clearable.tryClear 로 주크박스 음반을 지워 떨어뜨리지 않는다(복제 방지). */
    @Test
    void cloneMoveJukeboxDoesNotDropRecord() throws Exception {
        run("setblock 0 70 0 jukebox{RecordItem:{id:\"minecraft:music_disc_13\",count:1}}");
        assertEquals(1, run("clone 0 70 0 0 70 0 10 70 10 replace move"), out.toString());
        assertEquals(0, run("execute if entity @e[type=item]"), "음반이 떨어지면 목적지 주크박스와 합쳐 둘이 된다(복제)");
    }

    /** A13-05: type=!x 다음 type=y 는 바닐라가 inapplicable 오류로 거절한다. */
    @Test
    void selectorTypeAfterInvertedTypeIsRejected() throws Exception {
        run("summon cow 0 70 0");
        run("tag @e[type=!pig,type=cow] list");
        assertTrue(out.stream().anyMatch(s -> s.contains("isn't applicable here")), out.toString());
    }

    /** A13-06: /setworldspawn·/spawnpoint 의 범위 밖 오류는 argument.pos.outofbounds("That position is outside the allowed boundaries.") 다. */
    @Test
    void spawnableBoundsErrorKey() throws Exception {
        run("setworldspawn 40000000 70 0");
        assertTrue(out.stream().anyMatch(s -> s.contains("outside the allowed boundaries")), out.toString());
    }

    /** A13-02: 음반이 목적지로 옮겨 가고 원본에서는 사라진다(복제 없음). */
    @Test
    void cloneMoveJukeboxMovesRecord() throws Exception {
        run("setblock 0 70 0 jukebox{RecordItem:{id:\"minecraft:music_disc_13\",count:1}}");
        run("clone 0 70 0 0 70 0 10 70 10 replace move");
        assertEquals(1, run("execute if data block 10 70 10 RecordItem"), out.toString());
        assertEquals(0, run("execute if data block 0 70 0 RecordItem"));
    }

    /** A13-02: 독서대도 clone move 로 책이 떨어지지 않는다. */
    @Test
    void cloneMoveLecternDoesNotDropBook() throws Exception {
        run("setblock 0 70 0 lectern{Book:{id:\"minecraft:writable_book\",count:1}}");
        assertEquals(1, run("clone 0 70 0 0 70 0 10 70 10 replace move"), out.toString());
        assertEquals(0, run("execute if entity @e[type=item]"), "책이 떨어지면 복제가 된다");
    }

    /** A13-05: type=!x 다음 type=#태그 도 거절. */
    @Test
    void selectorTagTypeAfterInvertedTypeIsRejected() throws Exception {
        run("tag @e[type=!pig,type=#minecraft:skeletons] list");
        assertTrue(out.stream().anyMatch(s -> s.contains("isn't applicable here")), out.toString());
    }

    /** A13-05: type=!x 두 번과 type=y 다음은 거절. */
    @Test
    void selectorInvertedTypesStillAllowed() throws Exception {
        run("tag @e[type=!pig,type=!cow] list");
        assertTrue(out.stream().noneMatch(s -> s.contains("isn't applicable here")), out.toString());
        run("tag @e[type=cow,type=!pig] list");   // 양성 뒤에는 무엇이든 거절(이미 바닐라와 같음)
        assertTrue(out.stream().anyMatch(s -> s.contains("isn't applicable here")), out.toString());
    }
}
