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
 *   <li>플러그인이 상속한 목표는 서버 어댑터로 넣는다(M2a, {@link NmsPluginGoal}). 바닐라 목표 생성자·플러그인이 만든 감싼 목표는 M2b — 지금은 UnsupportedNmsCall.
 * </ul>
 */
public final class NmsGoals {

    private NmsGoals() {}

    /** 서버 목표를 보는 shim 의 재료: 몹과 서버 객체(목표 또는 감싼 목표). */
    public record Backing(Mob mob, Object server) {}

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
        Class<?> c = goal.getClass();
        boolean shim = c == ServerGoal.class || c.getName().startsWith("net.minecraft.");
        if (!shim) {
            Class<?> vanilla = vanillaAncestor(c);
            if (vanilla != Goal.class) {
                throw new UnsupportedNmsCall("GoalSelector#addGoal",
                        "goal " + c.getName() + " extends the vanilla goal " + vanilla.getName() + " - plugin classes that extend vanilla goals are task T-237");
            }
            return g.adopt(new NmsPluginGoal(goal));
        }
        Backing b = goal.furnaceBacking();
        if (b == null) throw new UnsupportedNmsCall("GoalSelector#addGoal", M2_HINT);
        if (b.mob() != mob && !sameEntity(b.mob(), mob)) {
            throw new UnsupportedNmsCall("GoalSelector#addGoal", "the goal " + c.getName() + " was made for another mob - a goal can only be added to the mob it was made for");
        }
        return b.server();
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

    /** 몹의 주인 스레드에서 서버 선택기 조작 하나. */
    public static <T> T on(Mob mob, String what, Function<ChunkInternals.MobGoals, T> task) {
        return mob.furnaceOnOwner(what, h -> {
            ChunkInternals.MobGoals g = NmsChunks.bridge().mobGoals(h);
            if (g == null) throw new UnsupportedNmsCall(what, "entity " + h.uniqueId() + " is not a mob on the server");
            return task.apply(g);
        });
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

    // ------------------------------------------------------------------ 목표 몸통(서버 목표를 보는 shim)

    private static Backing need(Goal self, String what) {
        Backing b = self.furnaceBacking();
        if (b == null) throw new UnsupportedNmsCall(what, M2_HINT);
        return b;
    }

    /** {@code Goal#canUse} 의 생성 구체 클래스 몸통(@each). */
    public static boolean canUse(Goal self) {
        Backing b = need(self, "Goal#canUse");
        return on(b.mob(), "Goal#canUse", g -> g.canUse(b.server()));
    }

    public static boolean canContinueToUse(Backing b) { return on(b.mob(), "Goal#canContinueToUse", g -> g.canContinueToUse(b.server())); }

    public static boolean isInterruptable(Backing b) { return on(b.mob(), "Goal#isInterruptable", g -> g.isInterruptable(b.server())); }

    public static void start(Backing b) {
        on(b.mob(), "Goal#start", g -> {
            g.start(b.server());
            return null;
        });
    }

    public static void stop(Backing b) {
        on(b.mob(), "Goal#stop", g -> {
            g.stop(b.server());
            return null;
        });
    }

    public static boolean requiresUpdateEveryTick(Backing b) { return on(b.mob(), "Goal#requiresUpdateEveryTick", g -> g.requiresUpdateEveryTick(b.server())); }

    public static void tick(Backing b) {
        on(b.mob(), "Goal#tick", g -> {
            g.tickGoal(b.server());
            return null;
        });
    }

    /** 서버 깃발 비트({@code GoalControl} 순서: MOVE·LOOK·JUMP·TARGET). */
    public static int flags(Backing b) { return on(b.mob(), "Goal#getFlags", g -> g.flags(b.server())); }

    public static void setFlags(Backing b, java.util.EnumSet<Goal.Flag> flags) {
        int m = 0;
        for (Goal.Flag f : flags) {
            int c = control(f);
            if (c >= 0) m |= 1 << c;
        }
        int mask = m;
        on(b.mob(), "Goal#setFlags", g -> {
            g.setFlags(b.server(), mask);
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

    public static boolean isRunning(Backing b) { return on(b.mob(), "WrappedGoal#isRunning", g -> g.isRunning(b.server())); }

    public static boolean canBeReplacedBy(Backing b, Backing other) {
        return on(b.mob(), "WrappedGoal#canBeReplacedBy", g -> g.canBeReplacedBy(b.server(), other.server()));
    }

    // ------------------------------------------------------------------ 바닐라 목표 생성자(M2b)

    /**
     * 바닐라 목표 생성자의 몸통: 생성자 몹의 주인 스레드에서 서버 목표를 바로 만든다(바닐라 생성자의 즉시 부작용·검사 예외가 그 시점에 난다).
     * {@code args} 는 몹 다음 매개변수 — 엔티티 클래스는 {@link #targets} 로 옮긴다.
     */
    public static ShimCtor vanilla(Mob mob, String vanillaClass, Object... args) {
        String what = vanillaClass + "#<init>";
        Object[] a = args.clone();
        for (int i = 0; i < a.length; i++) if (a[i] instanceof Class<?> c) a[i] = targets(what, c);
        Object server;
        try {
            server = on(mob, what, g -> g.vanillaGoal(vanillaClass, a));
        } catch (org.domi.furnace.api.concurrent.ThreadOwnershipException e) {
            throw new org.domi.furnace.api.concurrent.ThreadOwnershipException(e.getMessage() + " - make vanilla goals in the region of their mob");
        }
        if (server == null) throw new UnsupportedNmsCall(what, M2B2_HINT);
        return ShimCtor.carrying(new Fresh(new Backing(mob, server)));
    }

    /** M2b-2 로 미룬 바닐라 목표 생성자의 몸통(늘 던진다 — super 인자 자리에서 부른다). */
    public static ShimCtor unsupported(String what) { throw new UnsupportedNmsCall(what, M2B2_HINT); }

    /** 생성자로 만든 목표 shim 을 그 서버 목표의 보기로 붙인다(목표 shim 사슬 맨 위 {@code Goal} 생성자가 부른다 — 아직 어느 선택기에도 없다). */
    public static void bindFresh(Goal self, ShimCtor tag) {
        if (!(tag.payload() instanceof Fresh f)) return;
        on(f.backing().mob(), "Goal#<init>", g -> {
            g.setView(f.backing().server(), self);
            return null;
        });
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
