// 게임 규칙(불만 표 37번 "커스텀 게임 규칙") — Furnace.server().gameRules().
// 플러그인 규칙: 이름은 "<플러그인 namespace>:<이름>"(이름은 영문·숫자·_), 불리언·정수만. /gamerule 로 보고 바꿀 수 있고
// level.dat 의 GameRules 에 함께 저장된다. 클라 화면에 따로 보이는 것은 없다(명령 자동 완성에만 나온다).
// 규칙을 등록한 플러그인이 꺼지면 /gamerule 에서 빠지지만, 저장된 값은 남아 다시 등록하면 그 값으로 돌아온다.
// 바닐라 규칙도 이 보기로 읽고 바꿀 수 있다(바닐라와 같은 효과 — 예: doDaylightCycle). 아무 스레드에서 불러도 된다.
package org.domi.furnace.api.world;

import java.util.Collection;
import java.util.Optional;
import org.domi.furnace.api.plugin.FurnacePlugin;

public interface GameRuleRegistry {

    /** 불리언 규칙을 등록한다. 이미 있는 이름이면 IllegalArgumentException. 저장된 값이 있으면 그 값으로 시작한다. */
    GameRule<Boolean> registerBoolean(FurnacePlugin owner, String name, boolean defaultValue);

    /** 정수 규칙(명령 인자 범위 min~max). 저장된 값이 범위 밖이어도 그대로 둔다(바닐라 정수 규칙과 같다). */
    GameRule<Integer> registerInt(FurnacePlugin owner, String name, int defaultValue, int min, int max);

    /** 값. 없는 규칙이면 IllegalArgumentException. */
    <T> T get(GameRule<T> rule);

    /** 값을 바꾼다(/gamerule 과 같은 효과·알림). 없는 규칙이면 IllegalArgumentException. */
    <T> void set(GameRule<T> rule, T value);

    /** 이름으로 찾는다(바닐라·플러그인). */
    Optional<GameRule<?>> find(String name);

    /** 모든 규칙 이름(바닐라 이름 순 → 플러그인 이름 순). */
    Collection<String> names();
}
