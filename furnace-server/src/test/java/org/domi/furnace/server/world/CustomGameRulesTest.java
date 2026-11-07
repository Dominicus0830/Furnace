// D2(불만 표 37번): 플러그인 게임 규칙 — 이름 규칙, 저장된 값으로 시작, 주인이 꺼져도 값은 level.dat 에 남는다.
// 바닐라 규칙만 있으면 저장 결과가 바닐라와 같다(':' 없는 모르는 키는 바닐라처럼 버린다).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CustomGameRulesTest {

    @Test
    void customRulesRoundTripThroughLevelData() {
        GameRules g = GameRules.fromStrings(Map.of("keepInventory", "true", "game:lives", "9", "unknownVanillaLike", "1"));
        assertFalse(g.toStrings().containsKey("unknownVanillaLike"), "':' 없는 모르는 키는 바닐라처럼 버린다");
        assertEquals("9", g.toStrings().get("game:lives"), "등록 전에도 저장 값은 남는다");
        assertThrows(IllegalArgumentException.class, () -> g.set("game:lives", 1), "등록 전에는 없는 규칙");

        g.register("Game", new GameRules.Rule("game:lives", GameRules.Type.INTEGER, 3, 1, 10));
        assertEquals(9, g.getInt("game:lives"), "저장된 값으로 시작");
        g.register("Game", new GameRules.Rule("game:arena", GameRules.Type.BOOLEAN, 1, 0, 1));
        assertTrue(g.getBoolean("game:arena"), "저장 값이 없으면 기본값");
        assertThrows(IllegalArgumentException.class, () -> g.register("Game", new GameRules.Rule("keepInventory", GameRules.Type.BOOLEAN, 0, 0, 1)));
        assertThrows(IllegalArgumentException.class, () -> g.register("Game", new GameRules.Rule("game:bad name", GameRules.Type.BOOLEAN, 0, 0, 1)));
        assertThrows(IllegalArgumentException.class, () -> g.register("Other", new GameRules.Rule("game:lives", GameRules.Type.INTEGER, 0, 0, 1)));

        java.util.List<String> changed = new java.util.ArrayList<>();
        g.onChange((r, v) -> changed.add(r.name() + "=" + v));
        g.set("game:arena", 0);
        assertEquals(java.util.List.of("game:arena=0"), changed, "바닐라 규칙과 같은 변경 알림");
        assertEquals("false", g.toStrings().get("game:arena"));

        assertEquals(2, g.unregisterOwner("Game").size());
        assertNull(g.rule("game:arena"));
        assertEquals("false", g.toStrings().get("game:arena"), "꺼져도 값은 남는다");
        g.register("Game", new GameRules.Rule("game:arena", GameRules.Type.BOOLEAN, 1, 0, 1));
        assertFalse(g.getBoolean("game:arena"), "다시 등록하면 남은 값");
    }

    @Test
    void vanillaOnlyOutputUnchanged() {
        assertEquals(new GameRules().toStrings().keySet(), GameRules.RULES.keySet());
    }
}
