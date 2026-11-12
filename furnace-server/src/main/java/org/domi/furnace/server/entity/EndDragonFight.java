// 바닐라 EndDragonFight: 엔드의 드래곤 전투 상태. 보스 바(분홍, 음악·안개), 드래곤 찾기/새로 만들기, 기둥 수정 수 세기,
// 처치 시 출구 포털(받침대)·관문·드래곤 알, 출구 포털 둘레의 수정 4개로 부활(수정 기둥 재생성 → 드래곤 소환).
// 근사(가정): 출구 포털 찾기(BlockPattern)는 기억한 위치나 받침대 기반암 기둥으로, 활성 포털 검사는 받침대 둘레 칸으로 본다.
// 청크 티켓(TicketType.DRAGON)·level.dat 저장 연결은 없다(엔드 World 호스팅 뒤).
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Explosion;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.BossEvent;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.feature.EndSpikeFeature;

public final class EndDragonFight {
    /** DragonRespawnAnimation 단계. */
    public enum RespawnStage { START, PREPARING_TO_SUMMON_PILLARS, SUMMONING_PILLARS, SUMMONING_DRAGON, END }

    private static final int BAR_PINK = 0, BAR_PROGRESS = 0, FLAG_MUSIC = 2, FLAG_FOG = 4;
    private static final Pos BEAM_CENTER = new Pos(0, 128, 0);

    // 엔드 (0,0) 청크를 가진 region 의 레벨 — region 이 합쳐지거나 나뉘면 새 주인이 rebind 한다
    private ChunkLevel level;
    /** 매 틱: 보스 바를 보는 플레이어가 있는가 → 드래곤 티켓((0,0) 거리 9)을 건다/뗀다(서버가 단다). */
    private java.util.function.Consumer<Boolean> ticketHook;
    private final Pos origin;
    private final List<Integer> gateways = new ArrayList<>();
    private final UUID barId = UUID.randomUUID();
    private final Set<Player> barPlayers = new HashSet<>();
    private boolean barVisible = true;
    private float barProgress = 1.0F;
    private Text barName = Text.translatable("entity.minecraft.ender_dragon");
    private int ticksSinceDragonSeen, crystalsAlive, ticksSinceCrystalsScanned;
    /** 바닐라 초기값 21: 첫 틱에 바로 플레이어를 훑는다. */
    private int ticksSinceLastPlayerScan = 21;
    private boolean dragonKilled, previouslyKilled;
    private boolean needsStateScanning = true;
    private boolean skipArenaLoadedCheck;
    private UUID dragonUUID;
    private Pos portalLocation;
    private RespawnStage respawnStage;
    private int respawnTime;
    private List<EndCrystal> respawnCrystals;

    public EndDragonFight(ChunkLevel level, long seed, Pos origin) {
        this.level = level;
        this.origin = origin;
        for (int i = 0; i < 20; i++) gateways.add(i);
        // Util.shuffle(gateways, RandomSource.create(seed))
        LegacyRandom r = new LegacyRandom(seed);
        for (int i = gateways.size(); i > 1; i--) {
            int j = r.nextInt(i);
            gateways.set(i - 1, gateways.set(j, gateways.get(i - 1)));
        }
    }

    public void skipArenaLoadedCheck() { skipArenaLoadedCheck = true; }

    /** 전투가 보는 레벨을 바꾼다(엔드 중앙 청크의 주인 region 이 바뀌었을 때). */
    public void rebind(ChunkLevel l) { this.level = l; }

    public ChunkLevel level() { return level; }

    public void ticketHook(java.util.function.Consumer<Boolean> h) { this.ticketHook = h; }

    public UUID getDragonUUID() { return dragonUUID; }

    public int getCrystalsAlive() { return crystalsAlive; }

    public boolean hasPreviouslyKilledDragon() { return previouslyKilled; }

    public boolean isDragonKilled() { return dragonKilled; }

    public Pos portalLocation() { return portalLocation; }

    public RespawnStage respawnStage() { return respawnStage; }

    public List<Integer> gateways() { return gateways; }

    public float barProgress() { return barProgress; }

    public boolean barVisible() { return barVisible; }

    public Set<Player> barPlayers() { return barPlayers; }

    // ------------------------------------------------------------------ 틱

    public void tick() {
        // 받침대 청크가 없어 미뤄 둔 출구 포털·알을 청크가 들어온 첫 틱에 놓는다(바닐라는 동기 로드로 그 자리에서 놓는다)
        if ((pendingExitPortal != null || pendingEgg) && podiumChunksLoaded()) {
            Boolean active = pendingExitPortal;
            boolean egg = pendingEgg;
            pendingExitPortal = null;
            pendingEgg = false;
            if (active != null) spawnExitPortal(active);
            if (egg) placeEgg();
        }
        setBarVisible(!dragonKilled);
        if (++ticksSinceLastPlayerScan >= 20) {
            updatePlayers();
            ticksSinceLastPlayerScan = 0;
        }
        if (ticketHook != null) ticketHook.accept(!barPlayers.isEmpty());   // addRegionTicket / removeRegionTicket(DRAGON)
        if (barPlayers.isEmpty()) return;
        // 의도한 차이: 바닐라는 받침대 높이를 볼 때 청크를 동기로 불러오지만 region 스레드에선 못 하므로,
        // 받침대 청크가 없으면 상태 훑기·부활·드래곤 찾기를 이 틱엔 건너뛰고 다음 틱에 다시 본다.
        boolean loaded = isArenaLoaded() && podiumChunksLoaded();
        if (needsStateScanning && loaded) {
            scanState();
            needsStateScanning = false;
        }
        if (respawnStage != null) {
            if (respawnCrystals == null && loaded) {
                respawnStage = null;
                tryRespawn();
            }
            if (respawnStage != null) tickRespawn(respawnStage, respawnTime++);
        }
        if (!dragonKilled) {
            if ((dragonUUID == null || ++ticksSinceDragonSeen >= 1200) && loaded) {
                findOrCreateDragon();
                ticksSinceDragonSeen = 0;
            }
            if (++ticksSinceCrystalsScanned >= 100 && loaded) {
                updateCrystalCount();
                ticksSinceCrystalsScanned = 0;
            }
        }
    }

    /**
     * isArenaLoaded: 바닐라 그대로 — 안쪽 반복이 {@code z = 8 + cz; z <= 8 + cz} 라 중심 청크 기준 x -8..8, z +8 한 줄만 본다.
     * 바닐라는 FULL 청크이고 BLOCK_TICKING 이상인지 보지만 여기선 불러와 있는지만 본다(가정).
     */
    private boolean isArenaLoaded() {
        if (skipArenaLoadedCheck) return true;
        int cx = origin.x() >> 4, cz = origin.z() >> 4;
        return level.hasChunksAt((cx - 8) << 4, (cz + 8) << 4, ((cx + 8) << 4) + 15, ((cz + 8) << 4) + 15);
    }

    private List<EnderDragon> dragons() {
        List<EnderDragon> out = new ArrayList<>();
        for (Entity e : level.entities().tickSnapshot()) if (e instanceof EnderDragon d && d.isAlive()) out.add(d);
        return out;
    }

    private Entity entityByUuid(UUID id) {
        if (id == null) return null;
        for (Entity e : level.entities().tickSnapshot()) if (id.equals(e.uuid()) && !e.isRemoved()) return e;
        return null;
    }

    private void scanState() {
        boolean active = hasActiveExitPortal();
        if (active) {
            previouslyKilled = true;
        } else {
            previouslyKilled = false;
            if (findExitPortal() == null) spawnExitPortal(false);
        }
        List<EnderDragon> list = dragons();
        if (list.isEmpty()) {
            dragonKilled = true;
        } else {
            EnderDragon d = list.get(0);
            dragonUUID = d.uuid();
            dragonKilled = false;
            if (!active) {
                d.discard();
                dragonUUID = null;
            }
        }
        if (!previouslyKilled && dragonKilled) dragonKilled = false;
    }

    private void findOrCreateDragon() {
        List<EnderDragon> list = dragons();
        if (list.isEmpty()) createNewDragon();
        else dragonUUID = list.get(0).uuid();
    }

    /** createNewDragon: (0, 128, 0) 위에 맴돌기 단계로, 방향은 level.random. */
    public EnderDragon createNewDragon() {
        EnderDragon d = new EnderDragon(level, level.nextEntityIds(9));
        d.setDragonFight(this);
        d.setFightOrigin(origin);
        d.getPhaseManager().setPhase(DragonPhase.HOLDING_PATTERN);
        d.moveTo(origin.x(), 128 + origin.y(), origin.z(), level.random().nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(d);
        dragonUUID = d.uuid();
        return d;
    }

    // ------------------------------------------------------------------ 보스 바

    /** updatePlayers: 살아 있고 (0, 128, 0) 에서 192 안의 플레이어만 바를 본다. */
    private void updatePlayers() {
        Set<Player> now = new HashSet<>();
        double cx = origin.x(), cy = 128 + origin.y(), cz = origin.z();
        level.forEachPlayer(p -> {
            double dx = p.x() - cx, dy = p.y() - cy, dz = p.z() - cz;
            if (p.isAlive() && !p.isDisconnected() && dx * dx + dy * dy + dz * dz <= 192.0 * 192.0) now.add(p);
        });
        for (Player p : now) if (barPlayers.add(p) && barVisible) p.send(addPacket());
        for (Player p : new ArrayList<>(barPlayers)) {
            if (!now.contains(p)) {
                barPlayers.remove(p);
                if (barVisible) p.send(BossEvent.remove(barId));
            }
        }
    }

    private BossEvent addPacket() { return BossEvent.add(barId, barName, barProgress, BAR_PINK, BAR_PROGRESS, FLAG_MUSIC | FLAG_FOG); }

    private void setBarVisible(boolean v) {
        if (v == barVisible) return;
        barVisible = v;
        for (Player p : barPlayers) p.send(v ? addPacket() : BossEvent.remove(barId));
    }

    private void setBarName(Text name) {
        if (name.equals(barName)) return;
        barName = name;
        if (barVisible) for (Player p : barPlayers) p.send(BossEvent.name(barId, name));
    }

    private void setBarProgress(float f) {
        if (f == barProgress) return;
        barProgress = f;
        if (barVisible) for (Player p : barPlayers) p.send(BossEvent.progress(barId, f));
    }

    // ------------------------------------------------------------------ 드래곤 알림

    /** 지금 보스 바 이름. */
    public Text barName() { return barName; }

    public void updateDragon(EnderDragon d) {
        if (d.uuid().equals(dragonUUID)) {
            setBarProgress(d.health() / d.maxHealth());
            ticksSinceDragonSeen = 0;
            if (d.hasCustomName()) setBarName(d.displayName());
        }
    }

    /** setDragonKilled: 바를 지우고 출구 포털을 열고 관문 하나, 첫 처치면 받침대 위에 드래곤 알. */
    public void setDragonKilled(EnderDragon d) {
        if (!d.uuid().equals(dragonUUID)) return;
        setBarProgress(0.0F);
        setBarVisible(false);
        spawnExitPortal(true);
        spawnNewGateway();
        if (!previouslyKilled) {
            if (podiumChunksLoaded()) placeEgg();
            else pendingEgg = true;
        }
        previouslyKilled = true;
        dragonKilled = true;
    }

    public void onCrystalDestroyed(EndCrystal crystal, Entity.Damage source, Targetable destroyer) {
        if (respawnStage != null && respawnCrystals != null && respawnCrystals.contains(crystal)) {
            respawnStage = null;
            respawnTime = 0;
            resetSpikeCrystals();
            spawnExitPortal(true);
        } else {
            updateCrystalCount();
            if (entityByUuid(dragonUUID) instanceof EnderDragon d) d.onCrystalDestroyed(crystal, crystal.blockPosition(), source, destroyer);
        }
    }

    /** updateCrystalCount: 기둥 10개의 꼭대기 상자(세로 전체) 안 수정 수. */
    public void updateCrystalCount() {
        ticksSinceCrystalsScanned = 0;
        crystalsAlive = 0;
        for (EndSpikeFeature.Spike s : EndSpikeFeature.spikesForSeed(level.worldSeed())) crystalsAlive += crystalsOn(s).size();
    }

    private List<Entity> crystalsOn(EndSpikeFeature.Spike s) {
        AABB top = new AABB(s.centerX() - s.radius(), -2032, s.centerZ() - s.radius(), s.centerX() + s.radius(), 2031, s.centerZ() + s.radius());
        return level.getEntities(null, top, e -> e instanceof EndCrystal);
    }

    private void resetSpikeCrystals() {
        for (EndSpikeFeature.Spike s : EndSpikeFeature.spikesForSeed(level.worldSeed())) {
            for (Entity e : crystalsOn(s)) {
                e.setInvulnerable(false);
                ((EndCrystal) e).setBeamTarget(null);
            }
        }
    }

    // ------------------------------------------------------------------ 구조물

    /** spawnNewGateway: 섞인 목록 끝 번호 i 로 반지름 96 원 위 (x, 75, z) 에 관문(이벤트 3000). */
    private void spawnNewGateway() {
        if (gateways.isEmpty()) return;
        int i = gateways.remove(gateways.size() - 1);
        int x = (int) Math.floor(96.0 * Math.cos(2.0 * (-Math.PI + 0.15707963267948966 * i)));
        int z = (int) Math.floor(96.0 * Math.sin(2.0 * (-Math.PI + 0.15707963267948966 * i)));
        Pos p = new Pos(x, 75, z);
        level.levelEvent(null, 3000, p, 0);
        placeGateway(p);
    }

    /** EndGatewayFeature(출구 없음)를 살아 있는 레벨에. */
    private void placeGateway(Pos o) {
        org.domi.furnace.server.block.entity.EndGatewayBlockEntity.placeGatewayPortal(level, o, null, false);
    }

    /** spawnExitPortal(active): 처음이면 받침대 높이 바로 아래(기반암이면 해수면까지 더 내려감)를 기억하고 받침대를 놓는다. */
    /** 받침대(origin ±4) 청크가 불러와 있는가. 없으면 높이 지도가 최저 높이를 돌려줘 잘못된 위치를 영구 기록하게 된다. */
    boolean podiumChunksLoaded() {
        return skipArenaLoadedCheck || level.hasChunksAt(origin.x() - 4, origin.z() - 4, origin.x() + 4, origin.z() + 4);
    }

    /** 받침대 청크가 없어 미룬 spawnExitPortal 의 active(null = 없음, 마지막 호출이 이긴다)와 알. */
    private Boolean pendingExitPortal;
    private boolean pendingEgg;

    private void placeEgg() {
        Pos p = new Pos(origin.x(), level.getMotionBlockingHeight(origin.x(), origin.z()), origin.z());
        level.setBlock(p, B.defaultState("minecraft:dragon_egg"), 3);
    }

    public void spawnExitPortal(boolean active) {
        if (!podiumChunksLoaded()) {   // 위 의도한 차이: 청크가 없으면 기록·블록 쓰기를 미루고 청크가 들어온 틱에 다시 한다
            pendingExitPortal = active;
            return;
        }
        pendingExitPortal = null;
        if (portalLocation == null) {
            Pos p = new Pos(origin.x(), level.getMotionBlockingNoLeavesHeight(origin.x(), origin.z()) - 1, origin.z());
            while (B.is(level.getBlockState(p), "minecraft:bedrock") && p.y() > 0) p = new Pos(p.x(), p.y() - 1, p.z());
            portalLocation = p;
        }
        placePodium(portalLocation, active);
    }

    /** EndPodiumFeature.place: 반지름 2.5 안은 기반암 바닥·포털(또는 공기), 3.5 안은 엔드 돌·기반암 테, 가운데 기반암 기둥 4·벽 횃불 4. */
    static void placePodium(ChunkLevel level, Pos o, boolean active) {
        int bedrock = B.defaultState("minecraft:bedrock"), endStone = B.defaultState("minecraft:end_stone"), air = B.defaultState("minecraft:air");
        int portal = B.defaultState("minecraft:end_portal");
        for (int z = o.z() - 4; z <= o.z() + 4; z++) {
            for (int y = o.y() - 1; y <= o.y() + 32; y++) {
                for (int x = o.x() - 4; x <= o.x() + 4; x++) {
                    int dx = x - o.x(), dy = y - o.y(), dz = z - o.z();
                    double d = dx * dx + dy * dy + dz * dz;
                    boolean inner = d < 2.5 * 2.5;
                    if (!inner && !(d < 3.5 * 3.5)) continue;
                    Pos p = new Pos(x, y, z);
                    if (y < o.y()) level.setBlock(p, inner ? bedrock : endStone, 3);
                    else if (y > o.y()) level.setBlock(p, air, 3);
                    else if (!inner) level.setBlock(p, bedrock, 3);
                    else level.setBlock(p, active ? portal : air, 3);
                }
            }
        }
        for (int i = 0; i < 4; i++) level.setBlock(new Pos(o.x(), o.y() + i, o.z()), bedrock, 3);
        int torch = B.defaultState("minecraft:wall_torch");
        var facing = B.prop("facing");
        String[] dirs = {"north", "east", "south", "west"};
        int[][] off = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int i = 0; i < 4; i++) level.setBlock(new Pos(o.x() + off[i][0], o.y() + 2, o.z() + off[i][1]), facing.with(torch, dirs[i]), 3);
    }

    private void placePodium(Pos o, boolean active) { placePodium(level, o, active); }

    /**
     * hasActiveExitPortal: 바닐라 그대로 — 중심 청크 ±8 의 청크마다 블록 엔티티 중 TheEndPortalBlockEntity(엔드 관문 포함)가 하나라도 있으면 true.
     * 바닐라는 청크를 동기로 불러오지만 여기선 불러와 있는 청크만 본다(의도한 차이: 아레나 판정이 z+8 한 줄뿐이라 그 밖 청크는 없을 수 있다).
     */
    private boolean hasActiveExitPortal() {
        int cx = origin.x() >> 4, cz = origin.z() >> 4;
        for (int i = -8; i <= 8; i++) {
            for (int j = -8; j <= 8; j++) {
                var col = level.listenerChunk(cx + i, cz + j);
                if (col == null) continue;
                for (var be : col.blockEntities().values()) {
                    if (be instanceof org.domi.furnace.server.block.entity.EndPortalBlockEntity && !be.isRemoved()) return true;
                }
            }
        }
        return false;
    }

    /**
     * findExitPortal: 바닐라는 (1) 중심 청크 ±8 의 end_portal·end_gateway 블록 엔티티 자리에서 BlockPattern 을 찾고,
     * (2) 없으면 MOTION_BLOCKING 높이부터 최저 높이까지 내려가며 찾는다. 찾은 패턴의 (3,3,3) = 포털 층 가운데를,
     * 기억한 위치가 없을 때만 기억한다. 여기선 (1) 없이 (2) 만, 가운데 기둥 x·z 가 origin 인 경우만 본다(가정: 받침대는 origin 에 놓인다).
     */
    private Pos findExitPortal() {
        int top = level.getMotionBlockingHeight(origin.x(), origin.z());
        for (int y = top; y >= level.minBuildHeight(); y--) {
            Pos p = new Pos(origin.x(), y, origin.z());
            if (isPodiumAt(p)) {
                if (portalLocation == null) portalLocation = p;
                return p;
            }
        }
        return null;
    }

    /** 패턴 층(위에서부터): 기둥 3층, 포털 층(테 + 가운데), 바닥 원판. '#' = 기반암, 나머지는 아무것이나. */
    private static final String[][] PODIUM_PATTERN = {
            {"       ", "       ", "       ", "   #   ", "       ", "       ", "       "},
            {"       ", "       ", "       ", "   #   ", "       ", "       ", "       "},
            {"       ", "       ", "       ", "   #   ", "       ", "       ", "       "},
            {"  ###  ", " #   # ", "#     #", "#  #  #", "#     #", " #   # ", "  ###  "},
            {"       ", "  ###  ", " ##### ", " ##### ", " ##### ", "  ###  ", "       "}};

    /** p = 포털 층 가운데. 패턴은 네 방향 대칭이라 회전은 따로 보지 않는다(위아래 뒤집힌 방향은 가정상 무시). */
    private boolean isPodiumAt(Pos p) {
        for (int layer = 0; layer < 5; layer++) {
            int y = p.y() + 3 - layer;
            for (int row = 0; row < 7; row++) {
                String r = PODIUM_PATTERN[layer][row];
                for (int col = 0; col < 7; col++) {
                    if (r.charAt(col) != '#') continue;
                    if (!B.is(level.getBlockState(new Pos(p.x() + col - 3, y, p.z() + row - 3)), "minecraft:bedrock")) return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ 부활

    /** tryRespawn: 처치된 뒤 출구 포털 네 방향 2칸(포털 층 + 1) 칸마다 수정이 있으면 부활 시작. */
    public void tryRespawn() {
        if (!dragonKilled || respawnStage != null) return;
        Pos p = portalLocation;
        if (p == null) {
            if (findExitPortal() == null) spawnExitPortal(true);
            p = portalLocation;
            if (p == null) return;   // 받침대 청크가 아직 없다 — 다음에 다시
        }
        List<EndCrystal> all = new ArrayList<>();
        Pos up = new Pos(p.x(), p.y() + 1, p.z());
        int[][] off = {{0, -2}, {2, 0}, {0, 2}, {-2, 0}};   // 북·동·남·서
        for (int[] o : off) {
            double x = up.x() + o[0], y = up.y(), z = up.z() + o[1];
            List<Entity> list = level.getEntities(null, new AABB(x, y, z, x + 1, y + 1, z + 1), e -> e instanceof EndCrystal);
            if (list.isEmpty()) return;
            for (Entity e : list) all.add((EndCrystal) e);
        }
        respawnDragon(all);
    }

    private void respawnDragon(List<EndCrystal> crystals) {
        if (!dragonKilled || respawnStage != null) return;
        if (!podiumChunksLoaded()) return;   // 청크가 없으면 치환이 헛돈다 — 부활을 시작하지 않고 다음 시도에
        // 바닐라: 패턴을 못 찾을 때까지 되풀이해 찾은 상자(7×7, 포털 층 -1 ~ +3)의 기반암·포털을 엔드 돌로
        int endStone = B.defaultState("minecraft:end_stone");
        // 안전 상한: 바꿔도 같은 자리가 또 잡히면(청크 없음 등으로 setBlock 무효) region 스레드가 멈추지 않게 끊는다
        int guard = 0;
        for (Pos p = findExitPortal(); p != null && guard++ < 64; p = findExitPortal()) {
            boolean changed = false;
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -1; dy <= 3; dy++) {
                Pos q = new Pos(p.x() + dx, p.y() + dy, p.z() + dz);
                int s = level.getBlockState(q);
                if (B.is(s, "minecraft:bedrock") || B.is(s, "minecraft:end_portal")) {
                    level.setBlock(q, endStone, 3);
                    changed |= !B.is(level.getBlockState(q), "minecraft:bedrock") && !B.is(level.getBlockState(q), "minecraft:end_portal");
                }
            }
            if (!changed) break;
        }
        respawnStage = RespawnStage.START;
        respawnTime = 0;
        spawnExitPortal(false);
        respawnCrystals = crystals;
    }

    public void setRespawnStage(RespawnStage stage) {
        if (respawnStage == null) throw new IllegalStateException("부활 중이 아니다");
        respawnTime = 0;
        if (stage == RespawnStage.END) {
            respawnStage = null;
            dragonKilled = false;
            EnderDragon dragon = createNewDragon();
            if (dragon != null) {   // 바닐라: 보스 바를 보는 플레이어 모두에게 summoned_entity(드래곤)
                var snap = KillCredit.lootOf(dragon);
                for (Player p : new ArrayList<>(barPlayers)) p.triggerAdvancement(a -> a.triggerSummoned(snap));
            }
        } else {
            respawnStage = stage;
        }
    }

    /** DragonRespawnAnimation.tick. */
    private void tickRespawn(RespawnStage stage, int time) {
        switch (stage) {
            case START -> {
                for (EndCrystal c : respawnCrystals) c.setBeamTarget(BEAM_CENTER);
                setRespawnStage(RespawnStage.PREPARING_TO_SUMMON_PILLARS);
            }
            case PREPARING_TO_SUMMON_PILLARS -> {
                if (time < 100) {
                    if (time == 0 || time == 50 || time == 51 || time == 52 || time >= 95) level.levelEvent(null, 3001, BEAM_CENTER, 0);
                } else {
                    setRespawnStage(RespawnStage.SUMMONING_PILLARS);
                }
            }
            case SUMMONING_PILLARS -> {
                boolean start = time % 40 == 0, finish = time % 40 == 39;
                if (start || finish) {
                    List<EndSpikeFeature.Spike> list = EndSpikeFeature.spikesForSeed(level.worldSeed());
                    int j = time / 40;
                    if (j < list.size()) {
                        EndSpikeFeature.Spike s = list.get(j);
                        if (start) {
                            for (EndCrystal c : respawnCrystals) c.setBeamTarget(new Pos(s.centerX(), s.height() + 1, s.centerZ()));
                        } else {
                            // BlockPos.betweenClosed 순서: x 가 가장 빨리, 그다음 y, z
                            for (int z = s.centerZ() - 10; z <= s.centerZ() + 10; z++)
                                for (int y = s.height() - 10; y <= s.height() + 10; y++)
                                    for (int x = s.centerX() - 10; x <= s.centerX() + 10; x++) level.removeBlock(new Pos(x, y, z), false);
                            level.explode(null, s.centerX() + 0.5F, s.height(), s.centerZ() + 0.5F, 5.0F, false, level.destroyType("BLOCK"));
                            placeSpike(s);
                        }
                    } else if (start) {
                        setRespawnStage(RespawnStage.SUMMONING_DRAGON);
                    }
                }
            }
            case SUMMONING_DRAGON -> {
                if (time >= 100) {
                    setRespawnStage(RespawnStage.END);
                    resetSpikeCrystals();
                    for (EndCrystal c : respawnCrystals) {
                        c.setBeamTarget(null);
                        level.explode(c, c.x(), c.y(), c.z(), 6.0F, false, Explosion.BlockInteraction.KEEP);
                        c.discard();
                    }
                } else if (time >= 80) {
                    level.levelEvent(null, 3001, BEAM_CENTER, 0);
                } else if (time == 0) {
                    for (EndCrystal c : respawnCrystals) c.setBeamTarget(BEAM_CENTER);
                } else if (time < 5) {
                    level.levelEvent(null, 3001, BEAM_CENTER, 0);
                }
            }
            case END -> {}
        }
    }

    /** SpikeFeature.placeSpike(crystalInvulnerable = true, 광선 (0, 128, 0)) 을 살아 있는 레벨에. 수정 방향 난수는 새 RandomSource. */
    private void placeSpike(EndSpikeFeature.Spike spike) {
        int obsidian = B.defaultState("minecraft:obsidian"), air = B.defaultState("minecraft:air"), bedrock = B.defaultState("minecraft:bedrock");
        int r = spike.radius(), cx = spike.centerX(), cz = spike.centerZ(), height = spike.height();
        for (int z = cz - r; z <= cz + r; z++) {
            for (int y = level.minBuildHeight(); y <= height + 10; y++) {
                for (int x = cx - r; x <= cx + r; x++) {
                    double dx = x - (double) cx, dz = z - (double) cz;
                    if (dx * dx + dz * dz <= (double) (r * r + 1) && y < height) level.setBlock(new Pos(x, y, z), obsidian, 3);
                    else if (y > 65) level.setBlock(new Pos(x, y, z), air, 3);
                }
            }
        }
        if (spike.guarded()) {
            int bars = B.defaultState("minecraft:iron_bars");
            var north = B.prop("north");
            var east = B.prop("east");
            var south = B.prop("south");
            var west = B.prop("west");
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 3; dy++) {
                boolean xEdge = Math.abs(dx) == 2, zEdge = Math.abs(dz) == 2, top = dy == 3;
                if (xEdge || zEdge || top) {
                    boolean f = dx == -2 || dx == 2 || top, f1 = dz == -2 || dz == 2 || top;
                    int s = north.with(bars, f && dz != -2);
                    s = south.with(s, f && dz != 2);
                    s = west.with(s, f1 && dx != -2);
                    s = east.with(s, f1 && dx != 2);
                    level.setBlock(new Pos(cx + dx, height + dy, cz + dz), s, 3);
                }
            }
        }
        EndCrystal crystal = new EndCrystal(level, level.nextEntityId());
        crystal.setBeamTarget(BEAM_CENTER);
        crystal.setInvulnerable(true);
        crystal.moveTo(cx + 0.5, height + 1, cz + 0.5, org.domi.furnace.server.block.RandomSource.createFor(crystal.id(), org.domi.furnace.server.block.RandomSeeds.Purpose.END_CRYSTAL_YAW).nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(crystal);
        Pos cp = crystal.blockPosition();
        level.setBlock(new Pos(cp.x(), cp.y() - 1, cp.z()), bedrock, 3);
        level.setBlock(cp, org.domi.furnace.server.block.types.BaseFireBlock.getState(level, cp), 3);
    }

    // ------------------------------------------------------------------ 저장(EndDragonFight.Data)

    public NbtCompound save() {
        NbtCompound t = new NbtCompound();
        t.putBoolean("NeedsStateScanning", needsStateScanning);
        t.putBoolean("DragonKilled", dragonKilled);
        t.putBoolean("PreviouslyKilled", previouslyKilled);
        // IsRespawning: 바닐라는 언제나 false 를 넘기고 lenientOptionalFieldOf(기본 false)라 키를 쓰지 않는다
        if (dragonUUID != null) t.putIntArray("Dragon", Entity.uuidToInts(dragonUUID));
        if (portalLocation != null) t.putIntArray("ExitPortalLocation", new int[] {portalLocation.x(), portalLocation.y(), portalLocation.z()});
        int[] g = new int[gateways.size()];
        for (int i = 0; i < g.length; i++) g[i] = gateways.get(i);
        t.putIntArray("Gateways", g);
        return t;
    }

    public void load(NbtCompound t) {
        needsStateScanning = !t.contains("NeedsStateScanning") || t.getBoolean("NeedsStateScanning");
        dragonKilled = t.getBoolean("DragonKilled");
        previouslyKilled = t.getBoolean("PreviouslyKilled");
        if (t.contains("IsRespawning") && t.getBoolean("IsRespawning")) respawnStage = RespawnStage.START;
        if (t.tags().get("Dragon") instanceof int[] u && u.length == 4) dragonUUID = new UUID((long) u[0] << 32 | u[1] & 0xFFFFFFFFL, (long) u[2] << 32 | u[3] & 0xFFFFFFFFL);
        if (t.tags().get("ExitPortalLocation") instanceof int[] p && p.length == 3) portalLocation = new Pos(p[0], p[1], p[2]);
        if (t.tags().get("Gateways") instanceof int[] g) {
            gateways.clear();
            for (int i : g) gateways.add(i);
        }
    }
}
