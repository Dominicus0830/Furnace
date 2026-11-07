// E2 두뇌 API 봇 시험 플러그인: /br <동작> <몹 종류> [인자] — 부른 플레이어 16칸 안의 그 종류 몹 하나에 editAi 로 두뇌 API 를 쓰고 [BR] 줄로 남긴다.
package probe;

import java.util.Set;
import java.util.UUID;
import org.domi.furnace.api.entity.Mob;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.entity.ai.MobAi;
import org.domi.furnace.api.entity.ai.brain.ActivityKey;
import org.domi.furnace.api.entity.ai.brain.BrainBehavior;
import org.domi.furnace.api.entity.ai.brain.BrainSensor;
import org.domi.furnace.api.entity.ai.brain.GlobalBlockPos;
import org.domi.furnace.api.entity.ai.brain.MemoryKey;
import org.domi.furnace.api.entity.ai.brain.MobBrain;
import org.domi.furnace.api.entity.ai.brain.SensorKey;
import org.domi.furnace.api.plugin.FurnacePlugin;

public final class BrainProbe extends FurnacePlugin {

    static final MemoryKey<Integer> TICKS = MemoryKey.plugin("brprobe", "ticks", Integer.class);
    static final MemoryKey<UUID> SEEN = MemoryKey.plugin("brprobe", "seen", UUID.class);

    private void log(String s) { logger().info("[BR] " + s); }

    @Override
    public void onEnable() {
        server().commands().register(this, "br", (s, a) -> {
            Player p = (Player) s;
            String op = a[0], type = a[1].contains(":") ? a[1] : "minecraft:" + a[1];
            server().world(p.location().world()).nearbyEntities(p.location(), 16).thenAccept(list -> {
                Mob mob = list.stream().filter(e -> e.type().equals(type) && e instanceof Mob).map(e -> (Mob) e).findFirst().orElse(null);
                if (mob == null) {
                    log(op + " none");
                    return;
                }
                mob.editAi(ai -> {
                    run(op, a, ai, p.uniqueId());
                    return null;
                }).whenComplete((r, e) -> {
                    if (e != null) log(op + " err=" + e);
                });
            });
        });
    }

    private void run(String op, String[] a, MobAi ai, UUID player) {
        MobBrain b = ai.brain().orElse(null);
        if (op.equals("info")) {
            log("info usesBrain=" + ai.usesBrain() + " brain=" + (b != null));
            if (b != null) log("info activities=" + b.activities() + " core=" + b.coreActivities() + " sensors=" + b.sensors().size() + " memories=" + b.memories().size());
            return;
        }
        if (b == null) {
            log(op + " nobrain");
            return;
        }
        try {
            switch (op) {
                case "home" -> log("home set=" + b.setMemory(MemoryKey.HOME, GlobalBlockPos.of(ai.location().world(), 1, 2, 3)) + " read=" + b.memory(MemoryKey.HOME).orElse(null));
                case "force" -> {
                    b.forceActivity(ActivityKey.parse(a[2]));
                    log("force active=" + b.activeActivities());
                }
                case "active" -> log("active " + b.activeActivities() + " forced=" + b.forcedActivity().map(Object::toString).orElse("none"));
                case "release" -> log("release " + b.releaseActivity() + " active=" + b.activeActivities());
                case "behave" -> {
                    b.registerMemory(TICKS);
                    b.setMemory(TICKS, 0);
                    b.addBehavior(this, ActivityKey.CORE, 99, new BrainBehavior() {
                        @Override public String name() { return "brprobe:count"; }
                        @Override public boolean shouldContinue(MobAi mob, MobBrain brain) { return true; }
                        @Override public void tick(MobAi mob, MobBrain brain) { brain.setMemory(TICKS, brain.memory(TICKS).orElse(0) + 1); }
                    });
                    b.addSensor(this, new BrainSensor() {
                        @Override public SensorKey key() { return SensorKey.of("brprobe", "seen"); }
                        @Override public int scanRate() { return 10; }
                        @Override public Set<MemoryKey<?>> memories() { return Set.of(SEEN); }
                        @Override public void sense(MobAi mob, MobBrain brain) { brain.setMemory(SEEN, player); }
                    });
                    log("behave ok behaviors=" + b.behaviors().stream().filter(i -> i.plugin()).count() + " sensors=" + b.sensors());
                }
                case "count" -> log("count ticks=" + b.memory(TICKS).orElse(-1) + " seen=" + b.memory(SEEN).map(u -> u.equals(player)).orElse(false));
                case "anger" -> log("anger angry=" + b.setMemory(MemoryKey.ANGRY_AT, player) + " target=" + b.setMemory(MemoryKey.ATTACK_TARGET, player)
                        + " targetApi=" + ai.target().map(u -> u.equals(player)).orElse(false));
                default -> log(op + " unknown");
            }
        } catch (RuntimeException e) {
            log(op + " err=" + e);
        }
    }
}
