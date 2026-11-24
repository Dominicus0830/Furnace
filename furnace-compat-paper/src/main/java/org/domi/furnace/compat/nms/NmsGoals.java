package org.domi.furnace.compat.nms;

import java.util.function.Function;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.domi.furnace.api.internal.ChunkInternals;

/**
 * 몹 목표 shim 의 연결(몹 목표 설계 M1, 공유 설계 furnace/designs/mob-goals-shim-design-2026-11-24.md).
 * <ul>
 *   <li>선택기·감싼 목표·목표 shim 은 서버 몹의 것을 본다. 모든 조작은 그 몹의 주인 스레드에서 돈다 — 규칙은 다른 월드 엔티티 조작과 같다
 *       (주인이면 바로, 틱이 아닌 스레드는 주인을 기다림, 다른 region·전역 스레드는 ThreadOwnershipException).
 *   <li>서버 목표 하나에 shim 하나: 처음 볼 때 가장 가까운 바닐라 목표 클래스(예 {@code TemptGoal})의 객체를 만들어 서버 객체에 붙인다 —
 *       {@code instanceof}·{@code removeGoal(w.getGoal())} 가 바닐라처럼 맞는다. 이식한 부모가 없는 몹 안쪽 목표는 {@link ServerGoal}.
 *   <li>플러그인이 상속한 목표는 서버 어댑터로 넣는다(M2a, {@link NmsPluginGoal}). 바닐라 목표 생성자는 서버 목표를 바로 만든다(M2b-1).
 *   <li>월드에 넣기 전 몹(M3, 설계 furnace/designs/mob-goals-m3-detached-design-2026-11-24.md): 선택기는 그 몹의 샌드박스(빈 레벨의 서버 몹)를 보고, 바꾸는
 *       조작은 샌드박스에 먼저 하고 엔티티 조작 기록에 붙인다. 넣을 때 서버가 같은 차례로 재생하고, 스폰 이벤트 전에 shim 의 경로({@link Route})를 진짜 목표로
 *       옮긴다. 짝이 없으면 그 shim 은 쓸 수 없다(경고 한 번).
 * </ul>
 */
public final class NmsGoals {

    private NmsGoals() {}

    /** 서버 목표를 보는 shim 의 경로: 월드 몹의 서버 객체, 넣기 전 몹의 샌드박스 객체, 또는 짝이 없어 닿지 않음. 불변 — 바꿀 때는 volatile 쓰기 한 번. */
    public sealed interface Route permits World, InSandbox, Dead {}

    public record World(Object server) implements Route {}

    public record InSandbox(Sandbox box, Object server) implements Route {}

    public record Dead() implements Route {}

    private static final Dead DEAD = new Dead();

    /**
     * 서버 목표를 보는 shim 의 재료: 몹과 경로. 넣기 전 몹의 것은 목표 조작 기록의 ref 이기도 하다 — 넣을 때 서버가 짝지은 진짜 객체로 {@link #bindWorld} 를
     * 부른다(스폰 이벤트 전). 넣기가 실패하면 샌드박스로 되돌리고, 성공했는데 짝이 없으면 닿지 않음이 된다.
     */
    public static final class Backing implements ChunkInternals.GoalRef {
        private final Mob mob;
        /** 넣기 전 몹의 것이면 그 샌드박스 경로(넣기 실패 때 되돌린다). */
        private volatile InSandbox home;
        private volatile Route route;
        /** 이 재료를 쓰는 shim(진짜 객체에 붙인다). */
        private volatile Object view;
        private volatile boolean warned;

        public Backing(Mob mob, Object worldServer) {
            this.mob = mob;
            this.route = new World(worldServer);
        }

        private Backing(Mob mob) { this.mob = mob; }

        public Mob mob() { return mob; }

        public Route route() { return route; }

        /** 월드 몹의 서버 객체(아니면 null). */
        public Object worldServer() { return route instanceof World w ? w.server() : null; }

        @Override
        public Object bindWorld(Object server) {
            route = new World(server);
            return view;
        }

        /** 넣기가 끝났다: 실패면 샌드박스로 되돌리고, 성공인데 옮기지 못했으면 닿지 않음. */
        void addFinished(boolean added) {
            InSandbox h = home;
            if (h == null) return;
            if (!added) route = h;
            else if (route == h) route = DEAD;
        }

        @Override
        public String toString() { return "goal backing of " + mob + " (" + route + ")"; }
    }

    /**
     * 넣기 전 몹의 선택기 경로(엔티티 shim 이 준다). 모든 조작은 {@link #locked} 안에서 — 데이터만 잠그고 플러그인 코드(판정 함수)는 밖에서 돌린다.
     * 넣는 도중이면 {@link #locked} 가 던진다.
     */
    public interface Sandbox {
        /** 잠금 안에서 샌드박스 몹의 선택기 보기로 {@code task}. 넣는 중이면 UnsupportedNmsCall, 넣을 몹이 이미 정해졌으면 {@link Moved}. */
        <T> T locked(String what, Function<ChunkInternals.MobGoals, T> task);

        /** 잠금 안에서만: 목표 조작 하나를 샌드박스에 하고(실패하면 기록하지 않는다) 엔티티 조작 기록에 붙인다. */
        void record(ChunkInternals.EntityOp op);

        /** 잠금 안에서만: ref 의 샌드박스 서버 객체(없으면 null). */
        Object ref(Object ref);

        /** 잠금 안에서만: 넣기 결과를 알려 줄 재료. */
        void track(Backing b);
    }

    /** 넣기 전 몹의 감싼 플러그인 목표인가 — 그 읽기는 샌드박스 잠금 밖에서 플러그인 목표를 바로 부른다(검토 권장 1). */
    public static boolean sandboxPluginWrapped(Backing wrapped, Goal inner) {
        return wrapped != null && inner != null && inner.furnaceBacking() == null && wrapped.route() instanceof InSandbox;
    }

    /** 엔티티 shim 전용: 넣기가 끝났다({@link Backing} 참고). */
    public static void addFinished(Backing b, boolean added) { b.addFinished(added); }

    /** 넣기 전 경로를 쓰려는데 넣을 엔티티가 막 정해졌다(스폰 이벤트 중) — 경로를 다시 읽는다. */
    public static final class Moved extends RuntimeException {
        public Moved() { super(null, null, false, false); }
    }

    /** 생성기 전용 생성자로 실어 보내는 재료(감싼 목표면 우선순위·안의 목표 shim 도). */
    public record Made(Backing backing, int priority, Goal inner) {}

    /** 바닐라 목표 생성자가 만든 서버 목표(M2b) — 그 shim 을 만들며 서버 목표의 보기로 붙인다. */
    public record Fresh(Backing backing) {}

    /** 선택기 shim 의 재료. */
    public record SelectorOf(Mob mob, boolean targets) {}

    public static final String M2_HINT = "plugin-made wrapped goals are not supported yet - mob goals step M2b-2";

    /** M2b-2 로 미룬 바닐라 목표 생성자. */
    public static final String M2B2_HINT = "this vanilla goal constructor is not supported yet - mob goals step M2b-2";

    /**
     * 선택기 {@code mob} 에 넣을 서버 목표(주인 스레드에서만). 서버 목표를 보는 shim 이면 그 서버 목표, {@code Goal} 을 바로 상속한 플러그인 목표면 서버 어댑터(M2a).
     * 바닐라 목표 클래스를 상속한 플러그인 클래스는 서버 목표를 가졌어도 재정의가 무시되므로 거부한다(T-237). 다른 몹의 목표도 거부한다(넣은 뒤 두 몹이 다른
     * region 으로 갈라지면 스레드 규칙이 깨진다 — 보충 설계 2).
     */
    public static Object serverGoal(Mob mob, ChunkInternals.MobGoals g, Goal goal) {
        if (pluginGoal(goal)) return g.adopt(new NmsPluginGoal(goal));
        Backing b = goal.furnaceBacking();
        Route r = b.route();
        if (r instanceof Dead) throw unpaired(b, "GoalSelector#addGoal");
        if (!(r instanceof World w) || (b.mob() != mob && !sameEntity(b.mob(), mob))) throw otherMob(goal);
        return w.server();
    }

    /** 플러그인이 {@code Goal} 을 바로 상속한 목표인가(서버 어댑터로 넣는다). 바닐라 목표를 상속했거나 서버 목표가 없는 shim 은 던진다. */
    private static boolean pluginGoal(Goal goal) {
        Class<?> c = goal.getClass();
        boolean shim = c == ServerGoal.class || c.getName().startsWith("net.minecraft.");
        if (!shim) {
            Class<?> vanilla = vanillaAncestor(c);
            if (vanilla != Goal.class) {
                throw new UnsupportedNmsCall("GoalSelector#addGoal",
                        "goal " + c.getName() + " extends the vanilla goal " + vanilla.getName() + " - plugin classes that extend vanilla goals are task T-237");
            }
            return true;
        }
        if (goal.furnaceBacking() == null) throw new UnsupportedNmsCall("GoalSelector#addGoal", M2_HINT);
        return false;
    }

    private static UnsupportedNmsCall otherMob(Goal goal) {
        return new UnsupportedNmsCall("GoalSelector#addGoal",
                "the goal " + goal.getClass().getName() + " was made for another mob - a goal can only be added to the mob it was made for");
    }

    private static boolean sameEntity(Mob a, Mob b) {
        var ha = a.furnaceHandleOrNull();
        var hb = b.furnaceHandleOrNull();
        return ha != null && hb != null && ha.uniqueId().equals(hb.uniqueId());
    }

    /** 가장 가까운 바닐라(shim) 조상 클래스 — 플러그인 클래스를 건너뛴다. */
    private static Class<?> vanillaAncestor(Class<?> c) {
        while (!c.getName().startsWith("net.minecraft.")) c = c.getSuperclass();
        return c;
    }

    /** 몹의 행동·대상 선택기 shim(몹 shim 생성자에서). */
    public static GoalSelector selector(Mob mob, boolean targets) {
        return NmsKinds.create("net.minecraft.world.entity.ai.goal.GoalSelector", GoalSelector.class, new SelectorOf(mob, targets));
    }

    /** 생성기 전용 생성자의 재료에서 서버 재료(목표 shim 사슬 맨 위 {@code Goal} 이 부른다). */
    public static Backing backing(ShimCtor tag) {
        if (tag.payload() instanceof Made m) return m.backing();
        if (tag.payload() instanceof Fresh f) return f.backing();
        throw new IllegalArgumentException("goal shim made without a server goal: " + tag.payload());
    }

    public static Made made(ShimCtor tag) {
        if (tag.payload() instanceof Made m) return m;
        throw new IllegalArgumentException("goal shim made without a server goal: " + tag.payload());
    }

    public static SelectorOf selectorOf(ShimCtor tag) {
        if (tag.payload() instanceof SelectorOf s) return s;
        throw new IllegalArgumentException("goal selector shim made without a mob: " + tag.payload());
    }

    /** 몹의 주인 스레드에서 서버 선택기 조작 하나(월드 몹만 — 넣기 전 몹은 {@link #onMob}). */
    public static <T> T on(Mob mob, String what, Function<ChunkInternals.MobGoals, T> task) {
        return onMob(mob, what, task, box -> { throw notInWorld(what); });
    }

    /**
     * 선택기 조작 하나: 월드 몹(또는 스폰 이벤트 중인 넣을 몹)이면 주인 스레드에서 {@code world}, 넣기 전 몹이면 아무 스레드에서 {@code sandbox}
     * (그 안의 잠금은 {@link Sandbox#locked}).
     */
    public static <T> T onMob(Mob mob, String what, Function<ChunkInternals.MobGoals, T> world, Function<Sandbox, T> sandbox) {
        return mob.furnaceGoals(what, h -> {
            ChunkInternals.MobGoals g = NmsChunks.bridge().mobGoals(h);
            if (g == null) throw new UnsupportedNmsCall(what, "entity " + h.uniqueId() + " is not a mob on the server");
            return world.apply(g);
        }, sandbox);
    }

    static UnsupportedNmsCall notInWorld(String what) {
        return new UnsupportedNmsCall(what, "the mob is not in a world yet (made with EntityType#create) - goal selectors only tick in a world");
    }

    /** 서버 목표의 shim(주인 스레드에서만). 같은 서버 목표는 같은 객체. */
    public static Goal goalView(Mob mob, ChunkInternals.MobGoals g, Object server) {
        Object v = g.view(server);
        if (v instanceof Goal goal) return goal;
        Goal made = create(g.vanillaClass(server), new Made(new Backing(mob, server), 0, null));
        g.setView(server, made);
        return made;
    }

    /** 서버 감싼 목표의 shim(주인 스레드에서만). 같은 서버 객체는 같은 객체. 우선순위·안의 목표는 바뀌지 않아 만들 때 담는다. */
    public static WrappedGoal wrappedView(Mob mob, ChunkInternals.MobGoals g, Object server) {
        Object v = g.view(server);
        if (v instanceof WrappedGoal w) return w;
        Goal inner = goalView(mob, g, g.goal(server));
        WrappedGoal made = NmsKinds.create("net.minecraft.world.entity.ai.goal.WrappedGoal", WrappedGoal.class,
                new Made(new Backing(mob, server), g.priority(server), inner));
        g.setView(server, made);
        return made;
    }

    private static final ClassValue<Boolean> CONCRETE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) { return !java.lang.reflect.Modifier.isAbstract(c.getModifiers()); }
    };

    private static Goal create(String vanillaClass, Made made) {
        try {
            Class<?> c = Class.forName(vanillaClass, false, NmsGoals.class.getClassLoader());
            if (Goal.class.isAssignableFrom(c) && CONCRETE.get(c)) return NmsKinds.create(vanillaClass, Goal.class, made);
        } catch (ClassNotFoundException | IllegalStateException notAShim) {
            // 바닐라 목표 클래스 shim 이 없거나 생성기 전용 생성자가 없다 — 아래의 일반 목표로
        }
        return new ServerGoal(ShimCtor.carrying(made));
    }

    /** 이식한 바닐라 부모가 없는(또는 추상 부모뿐인) 서버 목표의 shim — 바닐라 {@code Goal} 로 보인다. */
    public static final class ServerGoal extends Goal {
        ServerGoal(ShimCtor tag) { super(tag); }

        @Override
        public boolean canUse() { return NmsGoals.canUse(this); }
    }

    // ------------------------------------------------------------------ 넣기 전 몹(M3)

    /**
     * 넣기 전 몹의 선택기 목록(잠금 안에서). 처음 보는 감싼 목표는 shim 을 만들고 {@link ChunkInternals.EntityOp.GoalPin} 으로 묶는다 — 넣을 때 진짜 몹의 같은
     * 자리 목표가 같은 바닐라 클래스·우선순위·깃발이면 그 목표로 옮긴다.
     */
    public static java.util.List<WrappedGoal> sandboxList(Mob mob, Sandbox box, ChunkInternals.MobGoals g, boolean targets) {
        java.util.List<Object> all = g.available(targets);
        java.util.List<WrappedGoal> out = new java.util.ArrayList<>(all.size());
        for (int i = 0; i < all.size(); i++) out.add(sandboxWrapped(mob, box, g, targets, i, all.get(i)));
        return out;
    }

    private static WrappedGoal sandboxWrapped(Mob mob, Sandbox box, ChunkInternals.MobGoals g, boolean targets, int index, Object server) {
        if (g.view(server) instanceof WrappedGoal w) return w;
        Object innerServer = g.goal(server);
        Goal inner;
        Object goalRef;
        boolean plugin = false;
        if (g.view(innerServer) instanceof Goal v) {
            plugin = v.furnaceBacking() == null;
            inner = v;
            // 플러그인 목표의 어댑터는 그 플러그인 객체가 ref(바닐라처럼 같은 객체로 찾는다)
            goalRef = v.furnaceBacking() != null ? v.furnaceBacking() : v;
        } else {
            Backing bg = sandboxBacking(mob, box, innerServer);
            inner = create(g.vanillaClass(innerServer), new Made(bg, 0, null));
            bg.view = inner;
            g.setView(innerServer, inner);
            goalRef = bg;
        }
        Backing bw = sandboxBacking(mob, box, server);
        WrappedGoal made = NmsKinds.create("net.minecraft.world.entity.ai.goal.WrappedGoal", WrappedGoal.class, new Made(bw, g.priority(server), inner));
        bw.view = made;
        g.setView(server, made);
        box.record(new ChunkInternals.EntityOp.GoalPin(targets, index, bw, goalRef, g.vanillaClass(innerServer), g.priority(server),
                plugin ? -1 : g.flags(innerServer)));   // 플러그인 목표의 깃발은 플러그인 코드 — 잠금 안에서 부르지 않는다
        return made;
    }

    private static Backing sandboxBacking(Mob mob, Sandbox box, Object server) {
        Backing b = new Backing(mob);
        b.home = new InSandbox(box, server);
        b.route = b.home;
        box.track(b);
        return b;
    }

    /** 넣기 전 몹의 선택기에 넣을 목표의 ref(잠금 안에서). 플러그인 목표는 어댑터를 만드는 기록을 먼저 붙인다. */
    public static Object sandboxGoalRef(Mob mob, Sandbox box, Goal goal) {
        if (pluginGoal(goal)) {
            Object key = new Object();
            box.record(new ChunkInternals.EntityOp.GoalAdopt(key, new NmsPluginGoal(goal)));
            return key;
        }
        Backing b = goal.furnaceBacking();
        if (b.route() instanceof Dead) throw unpaired(b, "GoalSelector#addGoal");
        if (!(b.route() instanceof InSandbox s) || s.box() != box) throw otherMob(goal);
        return b;
    }

    /** 넣기 전 몹의 선택기에서 {@code removeGoal} 의 ref(잠금 안에서) — 이 몹 것이 아니면 null(바닐라는 못 찾으면 아무것도 안 한다). */
    public static Object sandboxRemoveRef(Sandbox box, Goal goal) {
        Backing b = goal.furnaceBacking();
        if (b == null) return goal;   // 플러그인 목표 객체 — 그것을 감싼 어댑터를 서버가 찾는다
        return b.route() instanceof InSandbox s && s.box() == box && box.ref(b) != null ? b : null;
    }

    // ------------------------------------------------------------------ 경로별 목표 몸통

    @FunctionalInterface
    private interface GoalCall<T> {
        T apply(ChunkInternals.MobGoals g, Object server);
    }

    /** shim 재료의 지금 경로로 목표 조작 하나. {@code mutates} 면 넣기 전 몹에서는 UnsupportedNmsCall(월드에서만 의미가 있다). */
    private static <T> T call(Backing b, String what, boolean mutates, GoalCall<T> task) {
        for (int tries = 0; ; tries++) {
            Route r = b.route();
            switch (r) {
                case World w -> {
                    return onMob(b.mob(), what, g -> task.apply(g, w.server()), box -> {
                        throw new UnsupportedNmsCall(what, "adding the mob of this goal to a world failed meanwhile - call it again");
                    });
                }
                case Dead d -> throw unpaired(b, what);
                case InSandbox s -> {
                    if (mutates) throw notInWorld(what);
                    try {
                        return s.box().locked(what, g -> task.apply(g, s.server()));
                    } catch (Moved moved) {
                        // 스폰 이벤트 중: 짝지은 재료는 이미 월드 경로다. 그대로면 짝이 없다(경로가 바뀌었으면 다시 읽는다 — 실패한 넣기 뒤 곧 다시 넣는 경우)
                        if (b.route() == r || tries > 100) throw unpaired(b, what);
                    }
                }
            }
        }
    }

    private static UnsupportedNmsCall unpaired(Backing b, String what) {
        if (!b.warned) {
            b.warned = true;
            org.slf4j.LoggerFactory.getLogger("Furnace").warn(org.domi.furnace.compat.i18n.CompatLang.t("compat.nms.goal-unpaired", String.valueOf(b.view)));
        }
        return new UnsupportedNmsCall(what, "this goal object was taken before its mob was added to a world and the goal list of the added mob differed"
                + " - get the goal again from the selector");
    }

    private static Backing need(Goal self, String what) {
        Backing b = self.furnaceBacking();
        if (b == null) throw new UnsupportedNmsCall(what, M2_HINT);
        return b;
    }

    /** {@code Goal#canUse} 의 생성 구체 클래스 몸통(@each). 넣기 전 몹은 샌드박스(빈 레벨)에서 판정한다(user-todo G6). */
    public static boolean canUse(Goal self) { return call(need(self, "Goal#canUse"), "Goal#canUse", false, ChunkInternals.MobGoals::canUse); }

    public static boolean canContinueToUse(Backing b) { return call(b, "Goal#canContinueToUse", false, ChunkInternals.MobGoals::canContinueToUse); }

    public static boolean isInterruptable(Backing b) { return call(b, "Goal#isInterruptable", false, ChunkInternals.MobGoals::isInterruptable); }

    public static void start(Backing b) {
        call(b, "Goal#start", true, (g, s) -> {
            g.start(s);
            return null;
        });
    }

    public static void stop(Backing b) {
        call(b, "Goal#stop", true, (g, s) -> {
            g.stop(s);
            return null;
        });
    }

    public static boolean requiresUpdateEveryTick(Backing b) { return call(b, "Goal#requiresUpdateEveryTick", false, ChunkInternals.MobGoals::requiresUpdateEveryTick); }

    public static void tick(Backing b) {
        call(b, "Goal#tick", true, (g, s) -> {
            g.tickGoal(s);
            return null;
        });
    }

    /** 서버 깃발 비트({@code GoalControl} 순서: MOVE·LOOK·JUMP·TARGET). */
    public static int flags(Backing b) { return call(b, "Goal#getFlags", false, ChunkInternals.MobGoals::flags); }

    public static void setFlags(Backing b, java.util.EnumSet<Goal.Flag> flags) {
        int m = 0;
        for (Goal.Flag f : flags) {
            int c = control(f);
            if (c >= 0) m |= 1 << c;
        }
        int mask = m;
        call(b, "Goal#setFlags", true, (g, s) -> {
            g.setFlags(s, mask);
            return null;
        });
    }

    /** 깃발 → {@code GoalControl} 순서 번호. Paper 의 UNKNOWN_BEHAVIOR 는 서버 목표가 쓰지 않는 깃발이라 -1. */
    public static int control(Goal.Flag f) {
        return switch (f) {
            case MOVE -> 0;
            case LOOK -> 1;
            case JUMP -> 2;
            case TARGET -> 3;
            default -> -1;
        };
    }

    private static final Goal.Flag[] BY_CONTROL = {Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP, Goal.Flag.TARGET};

    public static Goal.Flag flagOfControl(int control) { return BY_CONTROL[control]; }

    public static boolean isRunning(Backing b) { return call(b, "WrappedGoal#isRunning", false, ChunkInternals.MobGoals::isRunning); }

    /** 두 서버 감싼 목표가 같은 경로(같은 월드 몹 쪽, 또는 같은 샌드박스)면 서버 판정, 아니면 null(부르는 쪽이 바닐라 식으로 계산한다). */
    public static Boolean canBeReplacedBy(Backing b, Backing other) {
        Route r = b.route(), o = other.route();
        if (r instanceof World && o instanceof World x) {
            return call(b, "WrappedGoal#canBeReplacedBy", false, (g, srv) -> g.canBeReplacedBy(srv, x.server()));
        }
        if (r instanceof InSandbox s && o instanceof InSandbox x && s.box() == x.box()) {
            return call(b, "WrappedGoal#canBeReplacedBy", false, (g, srv) -> g.canBeReplacedBy(srv, x.server()));
        }
        return null;
    }

    // ------------------------------------------------------------------ 바닐라 목표 생성자(M2b)

    /**
     * 바닐라 목표 생성자의 몸통: 생성자 몹의 주인 스레드에서 서버 목표를 바로 만든다(바닐라 생성자의 즉시 부작용·검사 예외가 그 시점에 난다).
     * 넣기 전 몹이면 샌드박스에서 만들고 기록한다(넣을 때 같은 차례로 다시 만든다 — 부작용 순서가 같다). {@code args} 는 몹 다음 매개변수 — 엔티티 클래스는
     * {@link #targets} 로 옮긴다.
     */
    public static ShimCtor vanilla(Mob mob, String vanillaClass, Object... args) {
        String what = vanillaClass + "#<init>";
        Object[] a = args.clone();
        for (int i = 0; i < a.length; i++) if (a[i] instanceof Class<?> c) a[i] = targets(what, c);
        Backing made;
        try {
            made = onMob(mob, what, g -> {
                Object server = g.vanillaGoal(vanillaClass, a);
                if (server == null) throw new UnsupportedNmsCall(what, M2B2_HINT);
                return new Backing(mob, server);
            }, box -> box.locked(what, g -> {
                Backing b = new Backing(mob);
                try {
                    box.record(new ChunkInternals.EntityOp.GoalCreate(b, vanillaClass, java.util.Collections.unmodifiableList(java.util.Arrays.asList(a))));
                } catch (UnsupportedOperationException none) {
                    throw new UnsupportedNmsCall(what, M2B2_HINT);
                }
                b.home = new InSandbox(box, box.ref(b));
                b.route = b.home;
                box.track(b);
                return b;
            }));
        } catch (org.domi.furnace.api.concurrent.ThreadOwnershipException e) {
            throw new org.domi.furnace.api.concurrent.ThreadOwnershipException(e.getMessage() + " - make vanilla goals in the region of their mob");
        }
        return ShimCtor.carrying(new Fresh(made));
    }

    /** M2b-2 로 미룬 바닐라 목표 생성자의 몸통(늘 던진다 — super 인자 자리에서 부른다). */
    public static ShimCtor unsupported(String what) { throw new UnsupportedNmsCall(what, M2B2_HINT); }

    /** 생성자로 만든 목표 shim 을 그 서버 목표의 보기로 붙인다(목표 shim 사슬 맨 위 {@code Goal} 생성자가 부른다 — 아직 어느 선택기에도 없다). */
    public static void bindFresh(Goal self, ShimCtor tag) {
        if (!(tag.payload() instanceof Fresh f)) return;
        Backing b = f.backing();
        b.view = self;
        call(b, "Goal#<init>", false, (g, s) -> {
            g.setView(s, self);
            return null;
        });
    }

    /**
     * 바닐라 목표 생성자의 아이템 판정 매개변수(M2b-2, 예 TemptGoal). 정확하지 않은 {@code Ingredient} 는 아이템 종류 집합으로 바꿔 서버가 바로 판정한다
     * (바닐라 Ingredient#test 와 같다 — 종류만 본다, 비었으면 빈 스택만). 그 밖의 판정(람다·정확한 Ingredient)은 서버가 주인 스레드에서 그 판정을 스택 사본으로 부른다.
     */
    public static ChunkInternals.GoalItems items(java.util.function.Predicate<net.minecraft.world.item.ItemStack> test) {
        java.util.Objects.requireNonNull(test, "items");
        if (test instanceof net.minecraft.world.item.crafting.Ingredient ing && !ing.exact) {
            java.util.Set<String> ids = new java.util.TreeSet<>();
            for (net.minecraft.world.item.ItemStack s : ing.getItems()) ids.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
            return new ChunkInternals.GoalItems(java.util.Set.copyOf(ids), null, null);
        }
        String owner;
        try {
            owner = org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(test.getClass()).getName();
        } catch (IllegalArgumentException | IllegalStateException | ClassCastException notAPlugin) {
            owner = "?";
        }
        return new ChunkInternals.GoalItems(java.util.Set.of(), api -> test.test(api.isEmpty() ? net.minecraft.world.item.ItemStack.EMPTY
                : net.minecraft.world.item.ItemStack.fromFurnace(api, api.amount())), owner);
    }

    private static final String PLAYER_CLASS = "net.minecraft.server.level.ServerPlayer";

    /** 클래스 → 서버 대상(플레이어만, 또는 플레이어 없는 종류 집합). 플레이어와 다른 종류가 함께 맞으면(사실상 LivingEntity) 거부. */
    private static final ClassValue<Object> TARGETS = new ClassValue<>() {
        @Override
        protected Object computeValue(Class<?> c) {
            boolean players = isAncestorOf(c, PLAYER_CLASS);
            java.util.Set<String> types = new java.util.TreeSet<>();
            for (var e : NmsKinds.entityClasses().entrySet()) if (isAncestorOf(c, e.getValue())) types.add(e.getKey());
            if (players && !types.isEmpty()) {
                return "the entity class " + c.getName() + " matches players and other entities together - not supported (players are searched separately on this server)";
            }
            return new ChunkInternals.GoalTargets(players, java.util.Set.copyOf(types));
        }
    };

    private static boolean isAncestorOf(Class<?> c, String name) {
        try {
            return c.isAssignableFrom(Class.forName(name, false, NmsGoals.class.getClassLoader()));
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** 바닐라 목표 생성자의 엔티티 클래스 매개변수(보충 설계 3). */
    public static ChunkInternals.GoalTargets targets(String what, Class<?> c) {
        Object t = TARGETS.get(c);
        if (t instanceof ChunkInternals.GoalTargets g) return g;
        throw new UnsupportedNmsCall(what, (String) t);
    }
}
