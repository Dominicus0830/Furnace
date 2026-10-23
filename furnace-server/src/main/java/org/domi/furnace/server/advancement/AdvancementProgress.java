// 바닐라 AdvancementProgress — 조건 이름 → 달성 시각(HashMap, 순서가 패킷·저장 파일에 그대로 나온다)과 요구사항.
package org.domi.furnace.server.advancement;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AdvancementProgress implements Comparable<AdvancementProgress> {

    /** 바닐라 CriterionProgress — 달성 시각(없으면 미달성). */
    static final class CriterionProgress {
        Instant obtained;

        CriterionProgress() {}

        CriterionProgress(Instant obtained) { this.obtained = obtained; }

        boolean isDone() { return obtained != null; }
    }

    final HashMap<String, CriterionProgress> criteria;
    private List<List<String>> requirements = List.of();

    AdvancementProgress() { this.criteria = new HashMap<>(); }

    AdvancementProgress(HashMap<String, CriterionProgress> criteria) { this.criteria = criteria; }

    /** 바닐라 update: 없어진 조건은 지우고, 빠진 조건은 fastutil 집합 순서로 채운다. */
    void update(List<List<String>> reqs) {
        List<String> names = FastutilOrder.names(reqs);
        java.util.Set<String> set = new java.util.HashSet<>(names);
        criteria.entrySet().removeIf(e -> !set.contains(e.getKey()));
        for (String n : names) criteria.putIfAbsent(n, new CriterionProgress());
        requirements = reqs;
    }

    public boolean isDone() { return Advancement.test(requirements, this::criterionDone); }

    private boolean criterionDone(String n) {
        CriterionProgress c = criteria.get(n);
        return c != null && c.isDone();
    }

    public boolean hasProgress() {
        for (CriterionProgress c : criteria.values()) if (c.isDone()) return true;
        return false;
    }

    boolean grant(String n) {
        CriterionProgress c = criteria.get(n);
        if (c != null && !c.isDone()) {
            c.obtained = Instant.now();
            return true;
        }
        return false;
    }

    boolean revoke(String n) {
        CriterionProgress c = criteria.get(n);
        if (c != null && c.isDone()) {
            c.obtained = null;
            return true;
        }
        return false;
    }

    CriterionProgress criterion(String n) { return criteria.get(n); }

    /** 바닐라 getRemainingCriteria — 진행 맵(HashMap) 순서. */
    public List<String> remainingCriteria() {
        List<String> l = new ArrayList<>();
        for (Map.Entry<String, CriterionProgress> e : criteria.entrySet()) if (!e.getValue().isDone()) l.add(e.getKey());
        return l;
    }

    /** 바닐라 getCompletedCriteria. */
    public List<String> completedCriteria() {
        List<String> l = new ArrayList<>();
        for (Map.Entry<String, CriterionProgress> e : criteria.entrySet()) if (e.getValue().isDone()) l.add(e.getKey());
        return l;
    }

    Instant firstProgressDate() {
        Instant min = null;
        for (CriterionProgress c : criteria.values()) if (c.obtained != null && (min == null || c.obtained.isBefore(min))) min = c.obtained;
        return min;
    }

    @Override
    public int compareTo(AdvancementProgress o) {
        Instant a = firstProgressDate(), b = o.firstProgressDate();
        if (a == null && b != null) return 1;
        if (a != null && b == null) return -1;
        return a == null ? 0 : a.compareTo(b);
    }
}
