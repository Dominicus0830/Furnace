// 플레이어별 엔티티 보이기·숨기기와 변장 — Furnace.server().entityViews(). 실제 월드 엔티티·플레이어에 쓴다(가상 엔티티는 VirtualEntity 의 show/hide).
// 규칙은 그 규칙을 건 플러그인 이름으로 남고, 플러그인이 꺼지면 함께 거둔다. 보는 쪽 규칙은 그 접속 동안만 산다(다시 접속하면 없다 — Bukkit 과 같다).
// 모든 메서드는 아무 스레드에서 불러도 된다. 바뀐 것은 다음 틱 안에 화면에 반영된다(엔티티를 가진 region 이 추적을 다시 한다).
package org.domi.furnace.api.entity;

import java.util.Optional;
import java.util.UUID;
import org.domi.furnace.api.plugin.FurnacePlugin;

public interface EntityViews {

    // ------------------------------------------------------------------ 보이기·숨기기

    /**
     * viewer 에게서 target 을 숨긴다(Bukkit hideEntity). 플레이어를 숨기면 탭 목록에서도 빠진다(Bukkit hidePlayer 와 같다).
     * 여러 플러그인이 숨기면 모두가 다시 보여야 보인다. 자기 자신은 숨길 수 없다(무시).
     */
    void hide(FurnacePlugin plugin, Player viewer, Entity target);

    /** plugin 이 건 숨김을 거둔다. target 이 기본 숨김이면 plugin 이름으로 viewer 에게 보여 준다. */
    void show(FurnacePlugin plugin, Player viewer, Entity target);

    /** viewer 가 지금 규칙상 target 을 볼 수 있는가(거리·청크와는 따로 — 규칙만). */
    boolean canSee(Player viewer, UUID target);

    default boolean canSee(Player viewer, Entity target) { return canSee(viewer, target.uniqueId()); }

    /**
     * false 면 target 은 {@link #show} 받은 사람에게만 보인다(Bukkit setVisibleByDefault). 이 플러그인의 설정만 바꾼다 —
     * 기본 숨김을 건 플러그인이 하나라도 있으면 기본 숨김이다.
     */
    void setVisibleByDefault(FurnacePlugin plugin, Entity target, boolean visible);

    boolean isVisibleByDefault(UUID target);

    default boolean isVisibleByDefault(Entity target) { return isVisibleByDefault(target.uniqueId()); }

    // ------------------------------------------------------------------ 변장

    /** 모든 사람에게(본인 화면은 바뀌지 않는다) target 을 이 모습으로 보인다. 앞의 전역 변장을 바꾼다. */
    void disguise(FurnacePlugin plugin, Entity target, Disguise disguise);

    /** viewer 에게만 이 모습. 전역 변장보다 앞선다 — {@link Disguise#none()} 이면 그 사람에게는 진짜 모습. */
    void disguise(FurnacePlugin plugin, Entity target, Player viewer, Disguise disguise);

    /** 전역 변장을 지운다(플레이어별 변장은 남는다). */
    void undisguise(Entity target);

    /** viewer 에게 걸린 플레이어별 변장을 지운다(전역 변장이 있으면 그것으로 돌아간다). */
    void undisguise(Entity target, Player viewer);

    /** 전역 변장. */
    Optional<Disguise> disguise(UUID target);

    /** viewer 에게 실제로 보이는 변장(플레이어별 → 전역, none 이면 비어 있음). */
    Optional<Disguise> disguiseSeenBy(UUID target, Player viewer);
}
