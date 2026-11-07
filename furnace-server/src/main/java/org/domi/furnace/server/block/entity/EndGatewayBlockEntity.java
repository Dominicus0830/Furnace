// 바닐라 TheEndGatewayBlockEntity: 엔드 관문의 나이(빔 연출)·순간이동 쿨다운·출구 위치. 출구가 없으면 엔드에서 처음 쓸 때
// 중심에서 먼 쪽(1024 블록 밖)의 첫 비지 않은 청크를 찾아 엔드 돌 위(없으면 작은 섬을 만들어)에 출구 관문을 세운다.
// 출구 찾기는 청크를 차례로 올려야 하므로 서버에서는 EndGatewayBlock 이 비동기로 나눠 부르고, 계산 자체는 여기 정적 함수가 한다.
package org.domi.furnace.server.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.ChunkColumn;

public class EndGatewayBlockEntity extends EndPortalBlockEntity {

    public static final String THE_END = "minecraft:the_end";
    /** SPAWN_TIME·COOLDOWN_TIME·ATTENTION_INTERVAL·GATEWAY_HEIGHT_ABOVE_SURFACE. */
    public static final int SPAWN_TIME = 200, COOLDOWN_TIME = 40, ATTENTION_INTERVAL = 2400, GATEWAY_HEIGHT_ABOVE_SURFACE = 10;
    private static final int END_GATEWAY = B.id("end_gateway");

    private long age;
    private int teleportCooldown;
    private Pos exitPortal;
    private boolean exactTeleport;
    /** 출구를 만드는 중이면 그 결과를 기다리는 쪽들(저장하지 않는다, 이 칸 region 스레드 전용). */
    private List<Consumer<Pos>> pendingExit;

    public EndGatewayBlockEntity(Pos pos, int state) { super("minecraft:end_gateway", pos, state); }

    @Override public boolean isValid(int state) { return B.block(state) == END_GATEWAY; }

    @Override public boolean ticking() { return true; }

    @Override public boolean hasUpdatePacket() { return true; }

    /** getUpdateTag: saveCustomOnly(Age·exit_portal·ExactTeleport — 클라이언트가 빔을 그린다). */
    @Override public NbtCompound updateTag() { return saveCustomOnly(); }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        tag.putLong("Age", age);
        if (exitPortal != null) tag.putIntArray("exit_portal", new int[] {exitPortal.x(), exitPortal.y(), exitPortal.z()});
        if (exactTeleport) tag.putBoolean("ExactTeleport", true);
    }

    @Override
    public void load(NbtCompound tag) {
        age = tag.getLong("Age");
        // NbtUtils.readBlockPos(tag, "exit_portal").filter(Level::isInSpawnableBounds).ifPresent(p -> exitPortal = p)
        if (tag.tags().get("exit_portal") instanceof int[] a && a.length == 3) {
            Pos p = new Pos(a[0], a[1], a[2]);
            if (isInSpawnableBounds(p)) exitPortal = p;
        }
        exactTeleport = tag.getBoolean("ExactTeleport");
    }

    /** Level.isInSpawnableBounds: y 가 ±2천만 안, x·z 가 ±3천만 안. */
    static boolean isInSpawnableBounds(Pos p) {
        return p.y() >= -20000000 && p.y() < 20000000
                && p.x() >= -30000000 && p.z() >= -30000000 && p.x() < 30000000 && p.z() < 30000000;
    }

    public long age() { return age; }

    public int teleportCooldown() { return teleportCooldown; }

    public Pos exitPortal() { return exitPortal; }

    public boolean exactTeleport() { return exactTeleport; }

    /** isSpawning: 생긴 지 200틱 안(빔이 솟는 중). */
    public boolean isSpawning() { return age < SPAWN_TIME; }

    /** isCoolingDown: 순간이동(또는 주의 빔) 뒤 40틱. */
    public boolean isCoolingDown() { return teleportCooldown > 0; }

    /** portalTick: 나이를 먹고, 쿨다운 중이면 줄이고 아니면 2400틱마다 쿨다운(빔 번쩍임). 두 상태가 바뀌면 setChanged. */
    @Override
    public void tick(BlockLevel level) {
        boolean spawning = isSpawning(), cooling = isCoolingDown();
        age++;
        if (cooling) {
            teleportCooldown--;
        } else if (age % ATTENTION_INTERVAL == 0L) {
            triggerCooldown(level);
        }
        if (spawning != isSpawning() || cooling != isCoolingDown()) setChanged(level);
    }

    /** triggerCooldown: 쿨다운 40, 블록 이벤트 (1, 0)(클라이언트 빔), setChanged. */
    public void triggerCooldown(BlockLevel level) {
        teleportCooldown = COOLDOWN_TIME;
        level.blockEvent(pos, B.block(level.getBlockState(pos)), 1, 0);
        setChanged(level);
    }

    /** triggerEvent(1, _): 쿨다운 40. */
    @Override
    public boolean triggerEvent(int type, int data) {
        if (type == 1) {
            teleportCooldown = COOLDOWN_TIME;
            return true;
        }
        return super.triggerEvent(type, data);
    }

    /** setExitPosition(pos, exact) + setChanged. */
    public void setExitPosition(BlockLevel level, Pos exit, boolean exact) {
        exactTeleport = exact;
        exitPortal = exit;
        if (level != null) setChanged(level);
    }

    // ------------------------------------------------------------------ 목적지(getPortalPosition)

    /**
     * getPortalPosition 의 동기판(목적 청크를 같은 스레드에서 쓸 수 있는 시험 레벨): 출구가 없고 엔드면 출구를 만들어 기록한다.
     * 출구가 있으면 정확 이동이면 그 칸, 아니면 findExitPosition. 돌려주는 것은 블록 칸(호출자가 바닥 가운데로 바꾼다). 없으면 null.
     */
    public Pos getPortalPosition(ChunkLevel level) {
        if (exitPortal == null && THE_END.equals(level.dimension())) {
            ExitWalk walk = new ExitWalk(pos);
            while (!walk.feed(isChunkEmpty(level, walk.chunkX(), walk.chunkZ()))) { }
            Pos exit = buildExit(level, walk, pos);
            setExitPosition(level, exit, exactTeleport);
        }
        if (exitPortal == null) return null;
        return exactTeleport ? exitPortal : findExitPosition(level, exitPortal);
    }

    /**
     * 서버판: 출구를 k 로 넘긴다(이 칸 region 스레드에서 부르고, k 도 그 스레드에서 불린다). 출구가 있으면 바로, 엔드가 아니면 null,
     * 만드는 중이면 그 결과를 기다리고, 아니면 maker 로 만들기 시작한다(maker 가 결과를 이 칸 region 스레드에서 finishExit 로 돌려준다).
     */
    public void withExit(ChunkLevel level, Consumer<Pos> k, Runnable maker) {
        if (exitPortal != null) {
            k.accept(exitPortal);
        } else if (!THE_END.equals(level.dimension())) {
            k.accept(null);
        } else if (pendingExit != null) {
            pendingExit.add(k);
        } else {
            pendingExit = new ArrayList<>();
            pendingExit.add(k);
            maker.run();
        }
    }

    /** 만든 출구(실패면 null)를 기록하고 기다리던 쪽에 넘긴다. self 는 지금 그 칸의 블록 엔티티(다시 불러왔으면 다른 객체, 없으면 null). */
    public void finishExit(ChunkLevel level, EndGatewayBlockEntity self, Pos exit) {
        if (self != null && exit != null && self.exitPortal == null) self.setExitPosition(level, exit, self.exactTeleport);
        List<Consumer<Pos>> ks = pendingExit;
        pendingExit = null;
        Pos result = self != null && self.exitPortal != null ? self.exitPortal : exit;
        if (ks != null) for (Consumer<Pos> k : ks) k.accept(result);
    }

    /** findExitPosition: (pos + (0, 2, 0)) 둘레 5 안에서 기반암이 아닌 가장 높은 충돌 블록의 위 칸. */
    public static Pos findExitPosition(ChunkLevel level, Pos pos) {
        return findTallestBlock(level, pos.offset(0, 2, 0), 5, false).above();
    }

    /**
     * findExitPortalXZPosTentative: 관문 (x, z) 방향 단위 벡터 v, w = v·1024 에서 시작해 w 청크가 비어 있지 않은 동안 16번까지 v·16 씩
     * 안쪽으로, 그다음 비어 있는 동안 16번까지 바깥쪽으로. 청크를 하나씩 봐야 하므로 걸음마다 feed 로 그 청크가 비었는지 받는다.
     */
    public static final class ExitWalk {
        private final double vx, vz;
        private double wx, wz;
        private int phase = 1, left = 16;

        public ExitWalk(Pos gateway) {
            // new Vec3(x, 0, z).normalize() — 길이 1e-5 미만이면 ZERO
            double x = gateway.x(), z = gateway.z();
            double len = Math.sqrt(x * x + 0.0 * 0.0 + z * z);
            if (len < 1.0E-5) {
                vx = 0.0;
                vz = 0.0;
            } else {
                vx = x / len;
                vz = z / len;
            }
            wx = vx * 1024.0;
            wz = vz * 1024.0;
        }

        public double x() { return wx; }

        public double z() { return wz; }

        /** getChunk(level, w): floor(w / 16). */
        public int chunkX() { return (int) Math.floor(wx / 16.0); }

        public int chunkZ() { return (int) Math.floor(wz / 16.0); }

        /** 지금 w 의 청크가 비었는가를 받아 한 걸음. 끝났으면 참(w 가 결과). */
        public boolean feed(boolean empty) {
            if (phase == 1) {
                if (!empty && left-- > 0) {
                    wx = wx + vx * -16.0;
                    wz = wz + vz * -16.0;
                    return false;
                }
                phase = 2;
                left = 16;
            }
            if (empty && left-- > 0) {
                wx = wx + vx * 16.0;
                wz = wz + vz * 16.0;
                return false;
            }
            return true;
        }
    }

    /** isChunkEmpty: 그 청크의 가장 높은 비지 않은 구역 번호가 -1(불러오지 못한 청크도 빈 것으로 본다). */
    public static boolean isChunkEmpty(ChunkLevel level, int cx, int cz) {
        ChunkColumn col = level.listenerChunk(cx, cz);
        return col == null || highestFilledSectionIndex(col) == -1;
    }

    /** ChunkAccess.getHighestFilledSectionIndex. */
    static int highestFilledSectionIndex(ChunkColumn col) {
        var sections = col.sections();
        for (int i = sections.length - 1; i >= 0; i--) if (!sections[i].hasOnlyAir()) return i;
        return -1;
    }

    /**
     * findOrCreateValidTeleportPos 의 뒷부분 + getPortalPosition 의 출구 세우기: walk 가 멈춘 청크에서 엔드 돌 칸을 찾고(없으면
     * (w + 0.5, 75) 에 end_island 를 RandomSource.create(pos.asLong()) 로 놓는다), 그 둘레 16 의 가장 높은 블록(기반암 포함)에서 10 위에
     * exit = gateway 인 관문을 세운다. 필요한 청크(그 청크 둘레 2청크)는 올라와 있어야 한다. 출구 관문 칸을 돌려준다.
     */
    public static Pos buildExit(ChunkLevel level, ExitWalk walk, Pos gateway) {
        Pos p = findValidSpawnInChunk(level, walk.chunkX(), walk.chunkZ());
        if (p == null) {
            Pos q = new Pos((int) Math.floor(walk.x() + 0.5), 75, (int) Math.floor(walk.z() + 0.5));
            placeEndIsland(level, q);
            p = q;
        }
        Pos exit = findTallestBlock(level, p, 16, true).above(GATEWAY_HEIGHT_ABOVE_SURFACE);
        placeGatewayPortal(level, exit, gateway, false);
        return exit;
    }

    /**
     * findValidSpawnInChunk: 청크의 (최소, 30, 최소)~(최대, 가장 높은 구역 위치 + 15, 최대)를 x→y→z 순으로 훑어, 엔드 돌이고 위 두 칸이
     * 가득 찬 충돌 블록이 아닌 칸 중 (0, 0, 0) 에서 가장 가까운(칸 가운데 기준, 같으면 먼저 찾은) 칸. 없으면 null.
     */
    public static Pos findValidSpawnInChunk(ChunkLevel level, int cx, int cz) {
        ChunkColumn col = level.listenerChunk(cx, cz);
        if (col == null) return null;
        int idx = highestFilledSectionIndex(col);
        int top = (idx == -1 ? level.minBuildHeight() : level.minBuildHeight() + idx * 16) + 16 - 1;
        int x0 = cx << 4, z0 = cz << 4;
        Pos best = null;
        double bestDist = 0.0;
        for (int z = z0; z <= z0 + 15; z++) {
            for (int y = 30; y <= top; y++) {
                for (int x = x0; x <= x0 + 15; x++) {
                    Pos p = new Pos(x, y, z);
                    if (!B.is(level.getBlockState(p), "end_stone")) continue;
                    if (B.isCollisionShapeFullBlock(level.getBlockState(p.above()))) continue;
                    if (B.isCollisionShapeFullBlock(level.getBlockState(p.above(2)))) continue;
                    double dx = x + 0.5, dy = y + 0.5, dz = z + 0.5;
                    double d = dx * dx + dy * dy + dz * dz;
                    if (best == null || d < bestDist) {
                        best = p;
                        bestDist = d;
                    }
                }
            }
        }
        return best;
    }

    /**
     * findTallestBlock(level, pos, radius, allowBedrock): x 다음 z 순으로 둘레 칸마다(가운데는 allowBedrock 일 때만) 꼭대기에서
     * 지금까지 찾은 것보다 높은 곳까지 내려가며 가득 찬 충돌 블록(allowBedrock 가 아니면 기반암 제외)을 찾는다. 없으면 pos.
     */
    public static Pos findTallestBlock(ChunkLevel level, Pos pos, int radius, boolean allowBedrock) {
        Pos best = null;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0 && !allowBedrock) continue;
                for (int y = level.maxBuildHeight() - 1; y > (best == null ? level.minBuildHeight() : best.y()); y--) {
                    Pos p = new Pos(pos.x() + dx, y, pos.z() + dz);
                    int s = level.getBlockState(p);
                    if (B.isCollisionShapeFullBlock(s) && (allowBedrock || !B.is(s, "bedrock"))) {
                        best = p;
                        break;
                    }
                }
            }
        }
        return best == null ? pos : best;
    }

    /** end_island(EndIslandFeature) 를 살아 있는 레벨에 RandomSource.create(pos.asLong()) 로(플래그 3). */
    static void placeEndIsland(ChunkLevel level, Pos q) {
        var random = new org.domi.furnace.server.world.gen.carver.WorldgenRandom();
        random.setSeed(q.asLong());
        int endStone = B.defaultState("minecraft:end_stone");
        org.domi.furnace.server.world.gen.feature.EndIslandFeature.place(random,
                (x, y, z) -> level.setBlock(new Pos(x, y, z), endStone, 3), q.x(), q.y(), q.z());
    }

    /**
     * EndGatewayFeature.place 를 살아 있는 레벨에(플래그 3): 3x5x3 기반암 틀과 가운데 관문. exit 가 있으면 새 관문 블록 엔티티에
     * setExitPosition(exit, exact).
     */
    public static void placeGatewayPortal(ChunkLevel level, Pos o, Pos exit, boolean exact) {
        int gateway = B.defaultState("minecraft:end_gateway"), bedrock = B.defaultState("minecraft:bedrock"), air = B.defaultState("minecraft:air");
        for (int z = o.z() - 1; z <= o.z() + 1; z++) {
            for (int y = o.y() - 2; y <= o.y() + 2; y++) {
                for (int x = o.x() - 1; x <= o.x() + 1; x++) {
                    boolean sx = x == o.x(), sy = y == o.y(), sz = z == o.z(), edgeY = Math.abs(y - o.y()) == 2;
                    Pos p = new Pos(x, y, z);
                    if (sx && sy && sz) {
                        level.setBlock(p, gateway, 3);
                        if (exit != null && level.getBlockEntity(p) instanceof EndGatewayBlockEntity g) g.setExitPosition(level, exit, exact);
                    } else if (sy) {
                        level.setBlock(p, air, 3);
                    } else if (edgeY && sx && sz) {
                        level.setBlock(p, bedrock, 3);
                    } else if ((sx || sz) && !edgeY) {
                        level.setBlock(p, bedrock, 3);
                    } else {
                        level.setBlock(p, air, 3);
                    }
                }
            }
        }
    }
}
