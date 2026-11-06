// 통계 시험: 이름 해석(커스텀/종류:값), 포화 덧셈, 모르는 통계 무시, 파일 왕복, 변경분만 award_stats 로 보내고 바이트 모양 확인.
package org.domi.furnace.server.stats;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.netty.buffer.Unpooled;
import java.nio.file.Path;
import java.util.UUID;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.recipe.TestRecipes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerStatsTest {

    @TempDir Path dir;

    @BeforeEach
    void setUp() throws Exception { assumeTrue(TestRecipes.manager() != null, "로컬 바닐라 데이터 없음"); }

    @Test
    void parsesNamesAndAdds() {
        assertEquals(new PlayerStats.Stat("minecraft:custom", "minecraft:interact_with_loom"), PlayerStats.parse("minecraft:interact_with_loom"));
        assertEquals(new PlayerStats.Stat("minecraft:crafted", "minecraft:stick"), PlayerStats.parse("minecraft:crafted:minecraft:stick"));
        assertEquals(new PlayerStats.Stat("minecraft:mined", "minecraft:stone"), PlayerStats.parse("minecraft:mined:stone"));
        var st = new PlayerStats(dir, UUID.randomUUID());
        st.increment("minecraft:crafted:minecraft:stick", 4);
        st.increment("minecraft:crafted:minecraft:stick", 2);
        st.increment("minecraft:play_time", 1);
        st.increment("minecraft:nonsense", 5);                 // 모르는 커스텀 통계
        st.increment("minecraft:crafted:minecraft:not_an_item", 1);
        assertEquals(6, st.get("minecraft:crafted:minecraft:stick"));
        assertEquals(0, st.get("minecraft:nonsense"));
        st.set("minecraft:leave_game", Integer.MAX_VALUE);
        st.increment("minecraft:leave_game", 10);
        assertEquals(Integer.MAX_VALUE, st.get("minecraft:leave_game"), "포화 덧셈");
    }

    @Test
    void savesLoadsAndSendsOnlyDirty() {
        UUID id = UUID.randomUUID();
        var st = new PlayerStats(dir, id);
        st.increment("minecraft:crafted:minecraft:stick", 3);
        st.increment("minecraft:interact_with_loom", 1);
        st.save();
        var back = new PlayerStats(dir, id);
        assertEquals(3, back.get("minecraft:crafted:minecraft:stick"));
        assertEquals(1, back.get("minecraft:interact_with_loom"));
        back.markAllDirty();
        var p = back.takeDirtyPacket();
        assertEquals(2, p.stats().size());
        var buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(2, Types.readVarInt(buf));
        buf.release();
        assertEquals(0, back.takeDirtyPacket().stats().size(), "보낸 뒤엔 변경분이 없다");
    }
}
