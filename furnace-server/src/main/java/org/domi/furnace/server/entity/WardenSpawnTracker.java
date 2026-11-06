// 바닐라 WardenSpawnTracker: 플레이어마다 경고 단계(0..4)·마지막 경고 뒤 틱·재경고 대기. 12000틱마다 한 단계 내려간다.
// tryWarn: 48칸 안에 워든이 없으면, 16칸 안 생존(관전자 아님) 플레이어들의 추적기 중 단계가 가장 높은 것을 올려 모두에게 복사한다.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class WardenSpawnTracker {
    public static final int MAX_WARNING_LEVEL = 4;
    private static final double PLAYER_SEARCH_RADIUS = 16.0;
    private static final int WARNING_CHECK_DIAMETER = 48;
    private static final int DECREASE_WARNING_LEVEL_EVERY_INTERVAL = 12000;
    private static final int WARNING_LEVEL_INCREASE_COOLDOWN = 200;

    private int ticksSinceLastWarning;
    private int warningLevel;
    private int cooldownTicks;

    public WardenSpawnTracker() {}

    public WardenSpawnTracker(int ticksSinceLastWarning, int warningLevel, int cooldownTicks) {
        this.ticksSinceLastWarning = ticksSinceLastWarning;
        this.warningLevel = warningLevel;
        this.cooldownTicks = cooldownTicks;
    }

    public void tick() {
        if (ticksSinceLastWarning >= DECREASE_WARNING_LEVEL_EVERY_INTERVAL) {
            decreaseWarningLevel();
            ticksSinceLastWarning = 0;
        } else {
            ticksSinceLastWarning++;
        }
        if (cooldownTicks > 0) cooldownTicks--;
    }

    public void reset() {
        ticksSinceLastWarning = 0;
        warningLevel = 0;
        cooldownTicks = 0;
    }

    public static OptionalInt tryWarn(ChunkLevel level, Pos pos, Player player) {
        if (hasNearbyWarden(level, pos)) return OptionalInt.empty();
        List<Player> players = getNearbyPlayers(level, pos);
        if (!players.contains(player)) players.add(player);
        for (Player p : players) if (p.wardenSpawnTracker().onCooldown()) return OptionalInt.empty();
        WardenSpawnTracker best = null;
        for (Player p : players) {   // Stream.max(BinaryOperator.maxBy): 같으면 앞의 것
            WardenSpawnTracker t = p.wardenSpawnTracker();
            if (best == null || t.warningLevel > best.warningLevel) best = t;
        }
        if (best == null) return OptionalInt.empty();
        best.increaseWarningLevel();
        for (Player p : players) p.wardenSpawnTracker().copyData(best);
        return OptionalInt.of(best.warningLevel);
    }

    private boolean onCooldown() { return cooldownTicks > 0; }

    private static boolean hasNearbyWarden(ChunkLevel level, Pos pos) {
        double cx = pos.x() + 0.5, cy = pos.y() + 0.5, cz = pos.z() + 0.5, h = WARNING_CHECK_DIAMETER / 2.0;
        AABB box = new AABB(cx - h, cy - h, cz - h, cx + h, cy + h, cz + h);
        return !level.getEntitiesOfClass(Warden.class, box, e -> true).isEmpty();
    }

    /** ServerLevel.getPlayers(16칸 안 && 살아 있음 && 관전자 아님) — 플레이어 목록 순서. */
    private static List<Player> getNearbyPlayers(ChunkLevel level, Pos pos) {
        double cx = pos.x() + 0.5, cy = pos.y() + 0.5, cz = pos.z() + 0.5;
        List<Player> out = new ArrayList<>();
        level.forEachPlayer(p -> {
            double dx = p.x() - cx, dy = p.y() - cy, dz = p.z() - cz;
            if (dx * dx + dy * dy + dz * dz < PLAYER_SEARCH_RADIUS * PLAYER_SEARCH_RADIUS && !p.isDisconnected()
                    && !p.vitals().isDeadOrDying() && p.gameMode() != org.domi.furnace.server.world.GameMode.SPECTATOR) out.add(p);
        });
        return out;
    }

    private void increaseWarningLevel() {
        if (!onCooldown()) {
            ticksSinceLastWarning = 0;
            cooldownTicks = WARNING_LEVEL_INCREASE_COOLDOWN;
            setWarningLevel(warningLevel + 1);
        }
    }

    private void decreaseWarningLevel() { setWarningLevel(warningLevel - 1); }

    public void setWarningLevel(int v) { warningLevel = Math.clamp(v, 0, MAX_WARNING_LEVEL); }

    public int getWarningLevel() { return warningLevel; }

    private void copyData(WardenSpawnTracker o) {
        warningLevel = o.warningLevel;
        cooldownTicks = o.cooldownTicks;
        ticksSinceLastWarning = o.ticksSinceLastWarning;
    }

    public NbtCompound save() {
        NbtCompound t = new NbtCompound();
        t.putInt("ticks_since_last_warning", ticksSinceLastWarning);
        t.putInt("warning_level", warningLevel);
        t.putInt("cooldown_ticks", cooldownTicks);
        return t;
    }

    public static WardenSpawnTracker load(NbtCompound t) {
        return new WardenSpawnTracker(t.getIntOr0("ticks_since_last_warning"), t.getIntOr0("warning_level"), t.getIntOr0("cooldown_ticks"));
    }
}
