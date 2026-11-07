// 변경 원인 사슬 — "누가·무엇이 이 변경을 일으켰나". 맨 앞(direct)이 가장 가까운 원인, 맨 뒤(root)가 가장 근본 원인이다.
// 예: 플레이어가 부싯돌로 TNT 를 켜고 그 TNT 가 다른 TNT 를 터뜨려 블록이 부서지면 EntityExplodeEvent 의 사슬은
//     [minecraft:tnt explode] ← [minecraft:tnt explode] ← [minecraft:player ignite name=Steve].
// 불변이고 고리는 스냅샷이라 어느 스레드에서 읽어도, 비동기 로그로 넘겨도 안전하다. 모르는 원인은 빈 사슬(NONE)이지 null 이 아니다.
// 꼬리를 나눠 쓰는 연결 목록이라 사슬을 한 단계 늘리는 비용은 고리 하나다. 길이는 MAX_LENGTH 로 묶인다(TNT 대포 같은 긴 연쇄) —
// 넘치면 가까운 쪽 절반과 root 만 남기고 truncated() 가 참이 된다.
package org.domi.furnace.api.event.cause;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.Location;

public final class CauseChain implements Iterable<CauseEntry> {

    /** 사슬 최대 길이. */
    public static final int MAX_LENGTH = 32;

    /** 원인을 모를 때. */
    public static final CauseChain NONE = new CauseChain(null, null, 0, false);

    private final CauseEntry head;
    private final CauseChain parent;
    private final int size;
    private final boolean truncated;

    private CauseChain(CauseEntry head, CauseChain parent, int size, boolean truncated) {
        this.head = head;
        this.parent = parent;
        this.size = size;
        this.truncated = truncated;
    }

    /** 고리 하나짜리 사슬. */
    public static CauseChain of(CauseEntry direct) { return of(direct, NONE); }

    /** direct 를 맨 앞에 두고 그 뒤에 parent(더 근본 원인들, null 이면 없음)를 잇는다. */
    public static CauseChain of(CauseEntry direct, CauseChain parent) {
        Objects.requireNonNull(direct, "direct");
        CauseChain p = parent == null ? NONE : parent;
        if (p.size >= MAX_LENGTH) p = p.compact();
        return new CauseChain(direct, p, p.size + 1, p.truncated);
    }

    /**
     * 엔티티 핸들로 고리를 만들고 그 엔티티의 {@link Entity#origin()} 을 뒤에 잇는다. 플레이어면 PLAYER 고리(이름 포함).
     * 핸들이 가진 값만 읽으므로 어느 스레드에서나 부를 수 있다.
     */
    public static CauseChain of(Entity entity, String action) {
        Objects.requireNonNull(entity, "entity");
        Location l = entity.location();
        String world = l == null ? null : l.world();
        BlockPos pos = l == null ? null : new BlockPos((int) Math.floor(l.x()), (int) Math.floor(l.y()), (int) Math.floor(l.z()));
        if (entity instanceof Player p) return of(CauseEntry.player(p.uniqueId(), p.name(), world, pos, action));
        return of(CauseEntry.entity(entity.type(), entity.uniqueId(), world, pos, action), entity.origin());
    }

    /** 가까운 쪽 MAX_LENGTH/2 - 1 개 + root. */
    private CauseChain compact() {
        List<CauseEntry> keep = new ArrayList<>(MAX_LENGTH / 2);
        CauseChain c = this;
        for (int i = 0; i < MAX_LENGTH / 2 - 1; i++, c = c.parent) keep.add(c.head);
        CauseChain out = new CauseChain(root().orElseThrow(), NONE, 1, true);
        for (int i = keep.size() - 1; i >= 0; i--) out = new CauseChain(keep.get(i), out, out.size + 1, true);
        return out;
    }

    public boolean isEmpty() { return size == 0; }

    public int size() { return size; }

    /** 중간 고리가 잘렸는가(MAX_LENGTH 를 넘은 연쇄). root 는 늘 남는다. */
    public boolean truncated() { return truncated; }

    /** 가장 가까운 원인. */
    public Optional<CauseEntry> direct() { return Optional.ofNullable(head); }

    /** 이 사슬에서 direct 를 뺀 나머지(더 근본 원인들). */
    public CauseChain parent() { return parent == null ? NONE : parent; }

    /** 가장 근본 원인. */
    public Optional<CauseEntry> root() {
        if (head == null) return Optional.empty();
        CauseChain c = this;
        while (c.parent.head != null) c = c.parent;
        return Optional.of(c.head);
    }

    /** 가까운 쪽부터 처음 맞는 고리. */
    public Optional<CauseEntry> first(Predicate<? super CauseEntry> test) {
        for (CauseChain c = this; c.head != null; c = c.parent) if (test.test(c.head)) return Optional.of(c.head);
        return Optional.empty();
    }

    /** 가까운 쪽부터 처음 나오는 이 종류 고리. */
    public Optional<CauseEntry> first(CauseEntry.Kind kind) { return first(e -> e.kind() == kind); }

    /** 가장 가까운 플레이어(책임자를 찾을 때 — 화살을 쏜 플레이어, TNT 를 켠 플레이어). */
    public Optional<CauseEntry> player() { return first(CauseEntry.Kind.PLAYER); }

    /** 가까운 쪽부터 모든 고리(새 목록, 고칠 수 없음). */
    public List<CauseEntry> entries() {
        List<CauseEntry> out = new ArrayList<>(size);
        for (CauseEntry e : this) out.add(e);
        return Collections.unmodifiableList(out);
    }

    @Override
    public Iterator<CauseEntry> iterator() {
        return new Iterator<>() {
            private CauseChain c = CauseChain.this;

            @Override
            public boolean hasNext() { return c.head != null; }

            @Override
            public CauseEntry next() {
                if (c.head == null) throw new NoSuchElementException();
                CauseEntry e = c.head;
                c = c.parent;
                return e;
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CauseChain other) || other.size != size || other.truncated != truncated) return false;
        Iterator<CauseEntry> a = iterator(), b = other.iterator();
        while (a.hasNext()) if (!a.next().equals(b.next())) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int h = Boolean.hashCode(truncated);
        for (CauseEntry e : this) h = 31 * h + e.hashCode();
        return h;
    }

    /** "minecraft:tnt explode ← minecraft:player ignite name=Steve" 꼴. */
    @Override
    public String toString() {
        if (head == null) return "(원인 없음)";
        StringBuilder sb = new StringBuilder();
        for (CauseEntry e : this) {
            if (!sb.isEmpty()) sb.append(" ← ");
            sb.append(e);
        }
        return truncated ? sb.append(" (잘림)").toString() : sb.toString();
    }
}
