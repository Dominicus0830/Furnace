package org.domi.furnace.compat.nms;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Predicate;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.domi.furnace.api.internal.ChunkInternals;

/**
 * {@code GoalSelector#getAvailableGoals()} 의 살아 있는 집합 보기(몹 목표 설계 M1 3.2). 훑기는 부른 때의 목록(주인 스레드 한 번 왕복 —
 * 바닐라 fastutil 집합처럼 돌면서 빼도 ConcurrentModificationException 이 없다). 빼기({@code remove}·{@code removeIf}·{@code clear}·
 * {@code iterator().remove()})는 주인 스레드에서 서버 집합에서만 뺀다 — 바닐라처럼 돌던 목표는 멈추지 않고 잠근 칸도 남는다(멈추고 빼는 것은 {@code removeGoal}).
 */
public final class NmsGoalSet extends AbstractSet<WrappedGoal> {

    private final Mob mob;
    private final boolean targets;

    public NmsGoalSet(Mob mob, boolean targets) {
        this.mob = mob;
        this.targets = targets;
    }

    private List<WrappedGoal> snapshot() {
        String what = "GoalSelector#getAvailableGoals";
        return NmsGoals.onMob(mob, what, g -> {
            List<Object> all = g.available(targets);
            List<WrappedGoal> out = new ArrayList<>(all.size());
            for (Object w : all) out.add(NmsGoals.wrappedView(mob, g, w));
            return out;
        }, box -> box.locked(what, g -> NmsGoals.sandboxList(mob, box, g, targets)));
    }

    @Override
    public Iterator<WrappedGoal> iterator() {
        List<WrappedGoal> list = snapshot();
        return new Iterator<>() {
            private int next;
            private WrappedGoal last;

            @Override
            public boolean hasNext() { return next < list.size(); }

            @Override
            public WrappedGoal next() {
                if (next >= list.size()) throw new NoSuchElementException();
                return last = list.get(next++);
            }

            @Override
            public void remove() {
                if (last == null) throw new IllegalStateException();
                NmsGoalSet.this.remove(last);
                last = null;
            }
        };
    }

    @Override
    public int size() {
        String what = "GoalSelector#getAvailableGoals";
        return NmsGoals.onMob(mob, what, g -> g.availableSize(targets), box -> box.locked(what, g -> g.availableSize(targets)));
    }

    @Override
    public boolean isEmpty() { return size() == 0; }

    @Override
    public boolean contains(Object o) {
        if (!(o instanceof WrappedGoal w)) return false;
        NmsGoals.Backing b = w.furnaceBacking();
        if (b == null) {
            for (WrappedGoal x : snapshot()) if (x.equals(w)) return true;
            return false;
        }
        String what = "GoalSelector#getAvailableGoals().contains";
        return NmsGoals.onMob(mob, what, g -> b.worldServer() != null && g.inSet(targets, b.worldServer()),
                box -> box.locked(what, g -> b.route() instanceof NmsGoals.InSandbox s && s.box() == box && g.inSet(targets, s.server())));
    }

    @Override
    public boolean add(WrappedGoal w) {
        NmsGoals.Backing b = w.furnaceBacking();
        String what = "GoalSelector#getAvailableGoals().add";
        if (b == null) throw new UnsupportedNmsCall(what, NmsGoals.M2_HINT);
        return NmsGoals.onMob(mob, what, g -> {
            Object server = b.worldServer();
            if (server == null) throw otherMob(what);
            return g.addToSet(targets, server);
        }, box -> box.locked(what, g -> {
            if (!(b.route() instanceof NmsGoals.InSandbox s) || s.box() != box) throw otherMob(what);
            int before = g.availableSize(targets);
            box.record(new ChunkInternals.EntityOp.GoalAddWrapped(targets, b));
            return g.availableSize(targets) != before;
        }));
    }

    private static UnsupportedNmsCall otherMob(String what) {
        return new UnsupportedNmsCall(what, "the wrapped goal belongs to another mob (or to this mob before it was added to a world and did not match)");
    }

    @Override
    public boolean remove(Object o) {
        if (!(o instanceof WrappedGoal w)) return false;
        // 바닐라 집합은 equals(안의 목표가 같은가)로 뺀다
        return removeIf(w::equals);
    }

    @Override
    public boolean removeIf(Predicate<? super WrappedGoal> filter) {
        String what = "GoalSelector#getAvailableGoals().removeIf";
        return NmsGoals.onMob(mob, what, g -> {
            boolean[] any = {false};
            g.removeFromSet(targets, server -> {
                boolean hit = filter.test(NmsGoals.wrappedView(mob, g, server));
                any[0] |= hit;
                return hit;
            });
            return any[0];
        }, box -> removeInSandbox(box, filter));
    }

    /**
     * 넣기 전 몹(M3, 설계 검토 1): 잠금 안에서 목록을 복사하고, 잠금 밖에서 판정 함수를 한 번씩 돌린 뒤, 다시 잠가 빠질 감싼 목표만 기록한다(판정 함수는 재생 때
     * 다시 불리지 않는다).
     */
    public boolean removeInSandbox(NmsGoals.Sandbox box, Predicate<? super WrappedGoal> filter) {
        String what = "GoalSelector#removeIf";
        List<WrappedGoal> list = box.locked(what, g -> NmsGoals.sandboxList(mob, box, g, targets));
        List<Object> hit = new ArrayList<>();
        for (WrappedGoal w : list) if (filter.test(w)) hit.add(w.furnaceBacking());
        if (hit.isEmpty()) return false;
        return box.locked(what, g -> {
            int before = g.availableSize(targets);
            box.record(new ChunkInternals.EntityOp.GoalRemoveFromSet(targets, List.copyOf(hit)));
            return g.availableSize(targets) != before;
        });
    }

    @Override
    public void clear() { removeIf(w -> true); }
}
