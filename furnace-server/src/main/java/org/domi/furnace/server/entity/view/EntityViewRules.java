// 플레이어별 엔티티 보이기·숨기기와 변장 규칙(바닐라에 없는 플러그인 기능 — 규칙이 하나도 없으면 바닐라와 똑같이 동작한다).
// 대상(엔티티 uuid)마다 불변 스냅샷 하나를 두고 바꿀 때 통째로 갈아 끼운다: 추적기(region 스레드)·연결 이벤트 루프가 락 없이 읽는다.
// 보는 쪽은 서버 Player 객체(접속 한 번)로 묶는다 — 다시 접속하면 규칙이 없어진다(Bukkit hideEntity 와 같다).
// 규칙이 하나도 없으면 active 가 거짓이라 추적기·연결은 volatile 읽기 하나로 끝난다.
package org.domi.furnace.server.entity.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.server.entity.EntityTasks;
import org.domi.furnace.server.entity.Player;

public final class EntityViewRules {

    private EntityViewRules() {}

    /** 한 보는 사람에 대한 규칙. hide/show 는 그 규칙을 건 플러그인(주인)들. appearance 가 null 이면 전역 변장을 따른다. */
    record ViewerRule(Set<Object> hide, Set<Object> show, Appearance appearance, Object appearanceOwner, long epoch) {
        static final ViewerRule EMPTY = new ViewerRule(Set.of(), Set.of(), null, null, 0);

        boolean isEmpty() { return hide.isEmpty() && show.isEmpty() && appearance == null; }
    }

    /**
     * 한 대상의 규칙(불변). epoch 는 변장이 바뀔 때마다 오른다(보는 사람 쪽 epoch 와 더해 "다시 스폰할지"를 가른다 — 둘 다 오르기만 한다).
     * tasks 는 그 엔티티를 가진 region 에서 추적을 다시 하도록 맡길 대기열(플레이어 대상은 null — region 이 매 틱 다시 본다).
     */
    record Target(Set<Object> hiddenByDefault, Map<Player, ViewerRule> viewers, Appearance appearance, Object appearanceOwner,
                  long epoch, EntityTasks tasks, boolean player) {
        boolean isEmpty() { return hiddenByDefault.isEmpty() && viewers.isEmpty() && appearance == null; }
    }

    private static final ConcurrentHashMap<UUID, Target> TARGETS = new ConcurrentHashMap<>();
    private static volatile boolean active;

    /** 규칙이 하나라도 있는가(없으면 모든 조회가 기본값 — 추적기·연결이 먼저 보는 빠른 길). */
    public static boolean active() { return active; }

    private static void updateActive() { active = !TARGETS.isEmpty(); }

    // ------------------------------------------------------------------ 조회(아무 스레드)

    /** viewer 가 target 을 볼 수 있는가(숨긴 플러그인이 하나라도 있으면 못 본다, 기본 숨김이면 보여 준 플러그인이 있어야 본다). */
    public static boolean canSee(Player viewer, UUID target) {
        if (!active) return true;
        Target t = TARGETS.get(target);
        return t == null || canSee(t, viewer);
    }

    private static boolean canSee(Target t, Player viewer) {
        ViewerRule r = t.viewers.get(viewer);
        if (r != null && !r.hide.isEmpty()) return false;
        if (!t.hiddenByDefault.isEmpty()) return r != null && !r.show.isEmpty();
        return true;
    }

    /** viewer 에게 target 이 보일 모습. 변장이 없으면 null. */
    public static Appearance appearanceFor(Player viewer, UUID target) {
        if (!active) return null;
        Target t = TARGETS.get(target);
        if (t == null) return null;
        ViewerRule r = t.viewers.get(viewer);
        Appearance a = r != null && r.appearance != null ? r.appearance : t.appearance;
        return a == null || a.isReal() ? null : a;
    }

    /** 플레이어별로 정한 모습(REAL 포함), 없으면 null. */
    public static Appearance viewerAppearance(Player viewer, UUID target) {
        Target t = TARGETS.get(target);
        ViewerRule r = t == null ? null : t.viewers.get(viewer);
        return r == null ? null : r.appearance;
    }

    /** 전역 변장, 없으면 null. */
    public static Appearance globalAppearance(UUID target) {
        Target t = TARGETS.get(target);
        return t == null ? null : t.appearance;
    }

    /** 모습이 바뀐 횟수(보는 사람별) — 바뀌면 그 사람에게 지웠다 다시 스폰한다. */
    public static long epochFor(Player viewer, UUID target) {
        if (!active) return 0;
        Target t = TARGETS.get(target);
        if (t == null) return 0;
        ViewerRule r = t.viewers.get(viewer);
        return t.epoch + (r == null ? 0 : r.epoch);
    }

    public static boolean isHiddenByDefault(UUID target) {
        Target t = TARGETS.get(target);
        return t != null && !t.hiddenByDefault.isEmpty();
    }

    /** 시험용: 남은 대상 수. */
    public static int targetCount() { return TARGETS.size(); }

    // ------------------------------------------------------------------ 바꾸기(아무 스레드) — 바뀌었으면 true

    /** 결과: 바뀌었는가, 그리고 다시 추적할 때 쓸 대기열·플레이어 대상 여부. */
    public record Change(UUID target, boolean changed, EntityTasks tasks, boolean player) {}

    private interface Edit { Target apply(Target t); }

    private static Change edit(UUID target, EntityTasks tasks, boolean player, Edit edit) {
        boolean[] changed = new boolean[1];
        Target[] after = new Target[1];
        TARGETS.compute(target, (k, old) -> {
            Target base = old != null ? old : new Target(Set.of(), Map.of(), null, null, 0, null, player);
            if (tasks != null && base.tasks != tasks) base = withTasks(base, tasks);
            Target next = edit.apply(base);
            changed[0] = next != base;
            after[0] = next;
            return next.isEmpty() ? null : next;
        });
        updateActive();
        Target t = after[0];
        return new Change(target, changed[0], t == null ? tasks : t.tasks, t == null ? player : t.player);
    }

    private static Target withTasks(Target t, EntityTasks tasks) {
        return new Target(t.hiddenByDefault, t.viewers, t.appearance, t.appearanceOwner, t.epoch, tasks, t.player);
    }

    private static Target withViewer(Target t, Player viewer, ViewerRule r) {
        Map<Player, ViewerRule> m = new HashMap<>(t.viewers);
        if (r.isEmpty()) m.remove(viewer);
        else m.put(viewer, r);
        return new Target(t.hiddenByDefault, Map.copyOf(m), t.appearance, t.appearanceOwner, t.epoch, t.tasks, t.player);
    }

    private static Set<Object> plus(Set<Object> s, Object o) {
        if (s.contains(o)) return s;
        Set<Object> n = new HashSet<>(s);
        n.add(o);
        return Set.copyOf(n);
    }

    private static Set<Object> minus(Set<Object> s, Object o) {
        if (!s.contains(o)) return s;
        Set<Object> n = new HashSet<>(s);
        n.remove(o);
        return Set.copyOf(n);
    }

    /** owner 가 viewer 에게서 target 을 숨긴다(기본 숨김 대상에 대해 그 owner 가 보여 준 것도 거둔다). */
    public static Change hide(Object owner, Player viewer, UUID target, EntityTasks tasks, boolean player) {
        return edit(target, tasks, player, t -> {
            ViewerRule r = t.viewers.getOrDefault(viewer, ViewerRule.EMPTY);
            ViewerRule n = new ViewerRule(plus(r.hide, owner), minus(r.show, owner), r.appearance, r.appearanceOwner, r.epoch);
            return n.equals(r) ? t : withViewer(t, viewer, n);
        });
    }

    /** owner 가 숨긴 것을 거두고, 기본 숨김 대상이면 그 owner 이름으로 보여 준다. */
    public static Change show(Object owner, Player viewer, UUID target, EntityTasks tasks, boolean player) {
        return edit(target, tasks, player, t -> {
            ViewerRule r = t.viewers.getOrDefault(viewer, ViewerRule.EMPTY);
            Set<Object> show = t.hiddenByDefault.isEmpty() ? minus(r.show, owner) : plus(r.show, owner);
            ViewerRule n = new ViewerRule(minus(r.hide, owner), show, r.appearance, r.appearanceOwner, r.epoch);
            return n.equals(r) ? t : withViewer(t, viewer, n);
        });
    }

    /** owner 가 target 을 기본으로 숨긴다(hidden=true) / 그 숨김을 거둔다. */
    public static Change setHiddenByDefault(Object owner, UUID target, boolean hidden, EntityTasks tasks, boolean player) {
        return edit(target, tasks, player, t -> {
            Set<Object> s = hidden ? plus(t.hiddenByDefault, owner) : minus(t.hiddenByDefault, owner);
            if (s == t.hiddenByDefault) return t;
            return new Target(s, t.viewers, t.appearance, t.appearanceOwner, t.epoch, t.tasks, t.player);
        });
    }

    /** 전역(viewer == null) 또는 플레이어별 변장. appearance 가 null 이면 지운다(플레이어별 REAL 은 "진짜 모습"). */
    public static Change setAppearance(Object owner, UUID target, Player viewer, Appearance appearance, EntityTasks tasks, boolean player) {
        return edit(target, tasks, player, t -> {
            if (viewer == null) {
                if (t.appearance == appearance) return t;
                return new Target(t.hiddenByDefault, t.viewers, appearance, appearance == null ? null : owner, t.epoch + 1, t.tasks, t.player);
            }
            ViewerRule r = t.viewers.getOrDefault(viewer, ViewerRule.EMPTY);
            if (r.appearance == appearance) return t;
            return withViewer(t, viewer, new ViewerRule(r.hide, r.show, appearance, appearance == null ? null : owner, r.epoch + 1));
        });
    }

    /** 플러그인이 꺼졌다: 그 플러그인이 건 규칙을 모두 거둔다. 바뀐 대상들을 돌려준다(부르는 쪽이 다시 추적시킨다). */
    public static List<Change> removeOwner(Object owner) {
        List<Change> out = new ArrayList<>();
        for (UUID id : List.copyOf(TARGETS.keySet())) {
            Change c = edit(id, null, false, t -> {
                Set<Object> hbd = minus(t.hiddenByDefault, owner);
                Appearance a = t.appearanceOwner == owner ? null : t.appearance;
                long epoch = t.appearanceOwner == owner ? t.epoch + 1 : t.epoch;
                Map<Player, ViewerRule> m = new HashMap<>();
                boolean viewersChanged = false;
                for (var e : t.viewers.entrySet()) {
                    ViewerRule r = e.getValue();
                    boolean mine = r.appearanceOwner == owner;
                    ViewerRule n = new ViewerRule(minus(r.hide, owner), minus(r.show, owner), mine ? null : r.appearance,
                            mine ? null : r.appearanceOwner, mine ? r.epoch + 1 : r.epoch);
                    if (n.hide != r.hide || n.show != r.show || mine) viewersChanged = true;
                    if (!n.isEmpty()) m.put(e.getKey(), n);
                }
                if (hbd == t.hiddenByDefault && a == t.appearance && !viewersChanged) return t;
                return new Target(hbd, Map.copyOf(m), a, a == null ? null : t.appearanceOwner, epoch, t.tasks, t.player);
            });
            if (c.changed()) out.add(c);
        }
        return out;
    }

    /**
     * 플레이어가 나갔다: 그가 보는 쪽으로 걸린 규칙을 모두 버리고, 그가 대상인 변장도 지운다(다시 접속하면 새 엔티티다).
     * 그를 숨긴 규칙(대상 쪽)은 남긴다 — 다른 사람의 접속 동안은 계속 숨겨져야 한다.
     */
    public static void playerGone(Player p) {
        if (!active) return;
        for (UUID id : List.copyOf(TARGETS.keySet())) {
            TARGETS.computeIfPresent(id, (k, t) -> {
                boolean self = id.equals(p.uuid());
                if (!t.viewers.containsKey(p) && !(self && (t.appearance != null || hasViewerAppearance(t)))) return t;
                Map<Player, ViewerRule> m = new HashMap<>(t.viewers);
                m.remove(p);
                if (self) m.replaceAll((v, r) -> new ViewerRule(r.hide, r.show, null, null, r.epoch + 1));
                m.values().removeIf(ViewerRule::isEmpty);
                Target n = new Target(t.hiddenByDefault, Map.copyOf(m), self ? null : t.appearance, self ? null : t.appearanceOwner,
                        self ? t.epoch + 1 : t.epoch, t.tasks, t.player);
                return n.isEmpty() ? null : n;
            });
        }
        updateActive();
    }

    private static boolean hasViewerAppearance(Target t) {
        for (ViewerRule r : t.viewers.values()) if (r.appearance != null) return true;
        return false;
    }

    /** 시험용: 모두 지운다. */
    public static void clearForTest() {
        TARGETS.clear();
        updateActive();
    }
}
