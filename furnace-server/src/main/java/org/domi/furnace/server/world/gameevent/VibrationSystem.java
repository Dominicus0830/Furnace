// 바닐라 VibrationSystem: 진동 수신(Listener) → 후보 선택(VibrationSelector, 같은 틱 안에서 가까운 것·주파수 높은 것) →
// 이동(거리만큼 틱) → 도착(User.onReceiveVibration). 차단: #occludes_vibration_signals 가 여섯 방향 모두 가로막으면 막힌다.
// 차이: 진동 입자 패킷은 아직 보내지 않는다(받는 플레이어 수만 세어 재전송 표시 상태를 맞춘다). AVOID_VIBRATION 발전 과제 미구현.
package org.domi.furnace.server.world.gameevent;

import java.util.Optional;
import java.util.UUID;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Clip;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Projectile;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.Vec3;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;

public interface VibrationSystem {

    Data getVibrationData();

    User getVibrationUser();

    /** getRedstoneStrengthForDistance(distance, radius). */
    static int getRedstoneStrengthForDistance(float distance, int radius) {
        double d = 15.0 / radius;
        return Math.max(1, 15 - (int) Math.floor(d * distance));
    }

    // ------------------------------------------------------------------ VibrationInfo

    /** 진동 하나: 이벤트·거리·지점·출처 UUID·투사체 주인 UUID(+ 살아 있는 출처 참조). */
    record VibrationInfo(GameEvent gameEvent, float distance, Vec3 pos, UUID uuid, UUID projectileOwnerUuid, Object entity) {

        public static VibrationInfo of(GameEvent event, float distance, Vec3 pos, Object entity) {
            UUID u = uuidOf(entity);
            UUID owner = entity instanceof Projectile p && p.getOwner() != null ? uuidOf(p.getOwner()) : null;
            return new VibrationInfo(event, distance, pos, u, owner, entity);
        }

        /** getEntity(level): 참조가 있으면 그것, 없으면 UUID 로 찾는다. */
        public Optional<Object> getEntity(ChunkLevel level) {
            if (entity != null) return Optional.of(entity);
            return uuid == null ? Optional.empty() : Optional.ofNullable(level.getTargetableByUuid(uuid));
        }

        /** getProjectileOwner(level). */
        public Optional<Object> getProjectileOwner(ChunkLevel level) {
            Optional<Object> e = getEntity(level);
            if (e.isPresent() && e.get() instanceof Projectile p && p.getOwner() != null) return Optional.of(p.getOwner());
            return projectileOwnerUuid == null ? Optional.empty() : Optional.ofNullable(level.getTargetableByUuid(projectileOwnerUuid));
        }

        NbtCompound save() {
            NbtCompound t = new NbtCompound();
            t.putString("game_event", gameEvent.name);
            t.putFloat("distance", distance);
            NbtList p = new NbtList((byte) 6);
            p.add(pos.x());
            p.add(pos.y());
            p.add(pos.z());
            t.putList("pos", p);
            if (uuid != null) t.putIntArray("source", uuidToInts(uuid));
            if (projectileOwnerUuid != null) t.putIntArray("projectile_owner", uuidToInts(projectileOwnerUuid));
            return t;
        }

        /** 코덱 읽기(lenient — 못 읽으면 null). */
        static VibrationInfo load(Object o) {
            if (!(o instanceof NbtCompound t)) return null;
            if (!(t.tags().get("game_event") instanceof String name)) return null;
            GameEvent e = GameEvent.byName(name);
            if (e == null || !t.containsNumber("distance") || !(t.tags().get("pos") instanceof NbtList p) || p.size() != 3) return null;
            float d = t.getFloat("distance");
            if (d < 0.0F) return null;
            Vec3 v = new Vec3(num(p.get(0)), num(p.get(1)), num(p.get(2)));
            return new VibrationInfo(e, d, v, uuidFrom(t.tags().get("source")), uuidFrom(t.tags().get("projectile_owner")), null);
        }
    }

    // ------------------------------------------------------------------ VibrationSelector

    /** 같은 틱에 들어온 후보 중 하나를 고르고, 다음 틱 이후에 내준다. */
    final class VibrationSelector {
        private VibrationInfo candidate;
        private long tick = -1L;
        private boolean present;

        public void addCandidate(VibrationInfo info, long gameTime) {
            if (shouldReplaceVibration(info, gameTime)) {
                candidate = info;
                tick = gameTime;
                present = true;
            }
        }

        private boolean shouldReplaceVibration(VibrationInfo info, long gameTime) {
            if (!present) return true;
            if (gameTime != tick) return false;
            if (info.distance() < candidate.distance()) return true;
            if (info.distance() > candidate.distance()) return false;
            return info.gameEvent().frequency() > candidate.gameEvent().frequency();
        }

        public Optional<VibrationInfo> chosenCandidate(long gameTime) {
            if (!present) return Optional.empty();
            return tick < gameTime ? Optional.of(candidate) : Optional.empty();
        }

        public void startOver() {
            present = false;
            candidate = null;
            tick = -1L;
        }

        NbtCompound save() {
            NbtCompound t = new NbtCompound();
            if (present && candidate != null) t.putCompound("event", candidate.save());
            t.putLong("tick", present ? tick : -1L);
            return t;
        }

        static VibrationSelector load(NbtCompound t) {
            VibrationSelector s = new VibrationSelector();
            VibrationInfo info = VibrationInfo.load(t.tags().get("event"));
            if (info != null) {
                s.candidate = info;
                s.tick = t.containsNumber("tick") ? ((Number) t.tags().get("tick")).longValue() : -1L;
                s.present = true;
            }
            return s;
        }
    }

    // ------------------------------------------------------------------ Data

    /** VibrationSystem.Data: 지금 이동 중인 진동·선택기·남은 이동 틱·입자 재전송 표시. NBT 키 "listener". */
    final class Data {
        public static final String NBT_TAG_KEY = "listener";
        VibrationInfo currentVibration;
        private int travelTimeInTicks;
        final VibrationSelector selectionStrategy;
        private boolean reloadVibrationParticle;

        public Data() { this(null, new VibrationSelector(), 0, false); }

        private Data(VibrationInfo current, VibrationSelector selector, int travel, boolean reload) {
            this.currentVibration = current;
            this.travelTimeInTicks = travel;
            this.selectionStrategy = selector;
            this.reloadVibrationParticle = reload;
        }

        public VibrationSelector getSelectionStrategy() { return selectionStrategy; }

        public VibrationInfo getCurrentVibration() { return currentVibration; }

        public void setCurrentVibration(VibrationInfo v) { currentVibration = v; }

        public int getTravelTimeInTicks() { return travelTimeInTicks; }

        public void setTravelTimeInTicks(int t) { travelTimeInTicks = t; }

        public void decrementTravelTime() { travelTimeInTicks = Math.max(0, travelTimeInTicks - 1); }

        public boolean shouldReloadVibrationParticle() { return reloadVibrationParticle; }

        public void setReloadVibrationParticle(boolean v) { reloadVibrationParticle = v; }

        /** 코덱 쓰기: event(있으면)·selector·event_delay. */
        public NbtCompound save() {
            NbtCompound t = new NbtCompound();
            if (currentVibration != null) t.putCompound("event", currentVibration.save());
            t.putCompound("selector", selectionStrategy.save());
            t.putInt("event_delay", travelTimeInTicks);
            return t;
        }

        /** 코덱 읽기: 읽으면 재전송 표시를 켠다. selector 가 없으면 실패(null — 호출자는 기존 값을 둔다). */
        public static Data load(NbtCompound t) {
            if (t == null || !(t.tags().get("selector") instanceof NbtCompound sel)) return null;
            VibrationInfo cur = VibrationInfo.load(t.tags().get("event"));
            int delay = t.containsNumber("event_delay") ? t.getIntOr0("event_delay") : 0;
            if (delay < 0) delay = 0;
            return new Data(cur, VibrationSelector.load(sel), delay, true);
        }
    }

    // ------------------------------------------------------------------ User

    interface User {
        int getListenerRadius();

        PositionSource getPositionSource();

        boolean canReceiveVibration(ChunkLevel level, Pos pos, GameEvent event, GameEventContext ctx);

        void onReceiveVibration(ChunkLevel level, Pos pos, GameEvent event, Object sourceEntity, Object projectileOwner, float distance);

        default String getListenableEvents() { return GameEvent.VIBRATIONS; }

        default boolean canTriggerAvoidVibration() { return false; }

        default boolean requiresAdjacentChunksToBeTicking() { return false; }

        default int calculateTravelTimeInTicks(float distance) { return (int) Math.floor(distance); }

        default boolean isValidVibration(GameEvent event, GameEventContext ctx) {
            if (!event.is(getListenableEvents())) return false;
            Object e = ctx.sourceEntity();
            if (e != null) {
                if (isSpectator(e)) return false;
                if (isSteppingCarefully(e) && event.is(GameEvent.IGNORE_VIBRATIONS_SNEAKING)) {
                    // TODO(발전 과제): canTriggerAvoidVibration 이고 플레이어면 avoid_vibration 기준
                    return false;
                }
                if (e instanceof Entity en && en.dampensVibrations()) return false;
            }
            return ctx.affectedState() < 0 || !B.isTag(ctx.affectedState(), "minecraft:dampens_vibrations");
        }

        default void onDataChanged() {}
    }

    // ------------------------------------------------------------------ Listener

    final class Listener implements GameEventListener {
        private final VibrationSystem system;

        public Listener(VibrationSystem system) { this.system = system; }

        @Override
        public PositionSource getListenerSource() { return system.getVibrationUser().getPositionSource(); }

        @Override
        public int getListenerRadius() { return system.getVibrationUser().getListenerRadius(); }

        @Override
        public boolean handleGameEvent(ChunkLevel level, GameEvent event, GameEventContext ctx, Vec3 pos) {
            Data data = system.getVibrationData();
            User user = system.getVibrationUser();
            if (data.getCurrentVibration() != null) return false;
            if (!user.isValidVibration(event, ctx)) return false;
            Optional<Vec3> lp = user.getPositionSource().getPosition(level);
            if (lp.isEmpty()) return false;
            Vec3 v = lp.get();
            if (!user.canReceiveVibration(level, containing(pos), event, ctx)) return false;
            if (isOccluded(level, pos, v)) return false;
            scheduleVibration(level, data, event, ctx, pos, v);
            return true;
        }

        /** forceScheduleVibration: 검사 없이 후보로(조각 감지기를 밟을 때). */
        public void forceScheduleVibration(ChunkLevel level, GameEvent event, GameEventContext ctx, Vec3 pos) {
            system.getVibrationUser().getPositionSource().getPosition(level)
                    .ifPresent(v -> scheduleVibration(level, system.getVibrationData(), event, ctx, pos, v));
        }

        private static void scheduleVibration(ChunkLevel level, Data data, GameEvent event, GameEventContext ctx, Vec3 pos, Vec3 listenerPos) {
            double dx = pos.x() - listenerPos.x(), dy = pos.y() - listenerPos.y(), dz = pos.z() - listenerPos.z();
            data.selectionStrategy.addCandidate(VibrationInfo.of(event, (float) Math.sqrt(dx * dx + dy * dy + dz * dz), pos, ctx.sourceEntity()),
                    level.gameTime());
        }

        /** distanceBetweenInBlocks. */
        public static float distanceBetweenInBlocks(Pos a, Pos b) {
            double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
            return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        /** isOccluded: 칸 가운데끼리, 시작점을 여섯 방향으로 1e-5 씩 옮긴 선이 모두 #occludes_vibration_signals 에 막히면 참. */
        public static boolean isOccluded(ChunkLevel level, Vec3 from, Vec3 to) {
            double fx = Math.floor(from.x()) + 0.5, fy = Math.floor(from.y()) + 0.5, fz = Math.floor(from.z()) + 0.5;
            double tx = Math.floor(to.x()) + 0.5, ty = Math.floor(to.y()) + 0.5, tz = Math.floor(to.z()) + 0.5;
            double eps = 1.0E-5F;
            for (int d = 0; d < 6; d++) {   // Direction.values(): DOWN UP NORTH SOUTH WEST EAST
                double sx = fx + Dir.DX[d] * eps, sy = fy + Dir.DY[d] * eps, sz = fz + Dir.DZ[d] * eps;
                if (!Clip.isBlockInLine(level, sx, sy, sz, tx, ty, tz, s -> B.isTag(s, "minecraft:occludes_vibration_signals"))) return false;
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ Ticker

    final class Ticker {
        private Ticker() {}

        public static void tick(ChunkLevel level, Data data, User user) {
            if (data.currentVibration == null) trySelectAndScheduleVibration(level, data, user);
            if (data.currentVibration != null) {
                boolean changed = data.getTravelTimeInTicks() > 0;
                tryReloadVibrationParticle(level, data, user);
                data.decrementTravelTime();
                if (data.getTravelTimeInTicks() <= 0) changed = receiveVibration(level, data, user, data.currentVibration);
                if (changed) user.onDataChanged();
            }
        }

        private static void trySelectAndScheduleVibration(ChunkLevel level, Data data, User user) {
            data.getSelectionStrategy().chosenCandidate(level.gameTime()).ifPresent(info -> {
                data.setCurrentVibration(info);
                data.setTravelTimeInTicks(user.calculateTravelTimeInTicks(info.distance()));
                var vib = user.getPositionSource().toVibration(data.getTravelTimeInTicks());
                if (vib != null) level.sendParticles(vib, info.pos().x(), info.pos().y(), info.pos().z(), 1, 0.0, 0.0, 0.0, 0.0);
                else level.countParticleViewers(info.pos().x(), info.pos().y(), info.pos().z());
                user.onDataChanged();
                data.getSelectionStrategy().startOver();
            });
        }

        private static void tryReloadVibrationParticle(ChunkLevel level, Data data, User user) {
            if (!data.shouldReloadVibrationParticle()) return;
            if (data.currentVibration == null) {
                data.setReloadVibrationParticle(false);
                return;
            }
            Vec3 p = data.currentVibration.pos();
            Vec3 dest = user.getPositionSource().getPosition(level).orElse(p);
            int t = data.getTravelTimeInTicks();
            int total = user.calculateTravelTimeInTicks(data.currentVibration.distance());
            double f = 1.0 - (double) t / total;
            double x = p.x() + f * (dest.x() - p.x()), y = p.y() + f * (dest.y() - p.y()), z = p.z() + f * (dest.z() - p.z());
            var vib = user.getPositionSource().toVibration(t);
            int viewers = vib != null ? level.sendParticles(vib, x, y, z, 1, 0.0, 0.0, 0.0, 0.0) : level.countParticleViewers(x, y, z);
            if (viewers > 0) data.setReloadVibrationParticle(false);
        }

        private static boolean receiveVibration(ChunkLevel level, Data data, User user, VibrationInfo info) {
            Pos from = containing(info.pos());
            Pos to = user.getPositionSource().getPosition(level).map(VibrationSystem::containing).orElse(from);
            if (user.requiresAdjacentChunksToBeTicking() && !areAdjacentChunksTicking(level, to)) return false;
            user.onReceiveVibration(level, from, info.gameEvent(), info.getEntity(level).orElse(null), info.getProjectileOwner(level).orElse(null),
                    Listener.distanceBetweenInBlocks(from, to));
            data.setCurrentVibration(null);
            return true;
        }

        /** 둘레 3×3 청크가 모두 로드돼 있는가. TODO(청크 틱 단계): 바닐라는 블록 틱 대상(shouldTickBlocksAt)인지도 본다. */
        private static boolean areAdjacentChunksTicking(ChunkLevel level, Pos pos) {
            int cx = pos.x() >> 4, cz = pos.z() >> 4;
            for (int x = cx - 1; x <= cx + 1; x++) {
                for (int z = cz - 1; z <= cz + 1; z++) {
                    if (level.listenerChunk(x, z) == null) return false;
                }
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ 도움

    static Pos containing(Vec3 v) { return new Pos((int) Math.floor(v.x()), (int) Math.floor(v.y()), (int) Math.floor(v.z())); }

    private static UUID uuidOf(Object e) {
        if (e instanceof Entity en) return en.uuid();
        if (e instanceof Player p) return p.uuid();
        return null;
    }

    private static UUID uuidOf(Targetable t) { return uuidOf((Object) t); }

    private static int[] uuidToInts(UUID u) {
        long m = u.getMostSignificantBits(), l = u.getLeastSignificantBits();
        return new int[] {(int) (m >> 32), (int) m, (int) (l >> 32), (int) l};
    }

    private static UUID uuidFrom(Object o) {
        if (!(o instanceof int[] u) || u.length != 4) return null;
        return new UUID((long) u[0] << 32 | u[1] & 0xFFFFFFFFL, (long) u[2] << 32 | u[3] & 0xFFFFFFFFL);
    }

    private static double num(Object o) { return o instanceof Number n ? n.doubleValue() : 0.0; }

    private static boolean isSpectator(Object e) {
        if (e instanceof Player p) return p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR;
        return e instanceof Entity en && en.isSpectator();
    }

    /** isSteppingCarefully = isShiftKeyDown(플레이어는 웅크리기). */
    private static boolean isSteppingCarefully(Object e) {
        if (e instanceof Player p) return p.sneaking();
        return e instanceof Entity en && en.isSteppingCarefully();
    }
}
