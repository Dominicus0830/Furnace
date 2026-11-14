// 장면 공용 도우미: 시험 환경 설치, 상태 해시(엔티티·블록 상자), 줄 적기.
package org.domi.furnace.server.scene;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;

public final class SceneKit {
    private SceneKit() {}

    /** 태그·레지스트리 설치(장면이 블록·아이템 이름을 쓰기 전에). 여러 번 불러도 된다. */
    public static void install() {
        if (!ItemContext.installed()) ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    /**
     * 시험 플레이어 한 명(생존, 고정 UUID, 가만히 서 있음)을 레벨 플레이어 목록에 넣는다. 레벨이 틱하는 몹은 forEachPlayer 로 그 플레이어를 보고
     * 쫓거나(좀비) 비킨다(밀기). 플레이어 자신의 틱은 돌지 않는다 — 장면 틱은 레벨(엔티티·블록)만 다룬다.
     */
    public static org.domi.furnace.server.entity.Player addPlayer(SceneLevel level, String name, double x, double y, double z) {
        int id = level.nextEntityId();
        var p = new org.domi.furnace.server.entity.Player(id, new java.util.UUID(0x5CE4EL, id), name, new org.domi.furnace.server.ConcurrentEmbeddedChannel());
        p.inventory(new org.domi.furnace.server.entity.PlayerInventory());
        p.vitals().loadGameTypes(null, org.domi.furnace.server.world.GameMode.SURVIVAL, false);
        p.applyMove(x, y, z, 0, 0, true);
        p.publishPosition(x, y, z, 0, 0, true);
        level.players.add(p);
        return p;
    }

    /** 블록 상자(양 끝 포함)에서 이 블록(이름)인 칸 수 — 금 파일을 사람이 읽을 수 있게 하는 보조 숫자(장면이 비어 있지 않다는 표시이기도 하다). */
    public static int count(ChunkLevel level, String block, int x0, int y0, int z0, int x1, int y1, int z1) {
        int id = org.domi.furnace.server.block.B.id(block), n = 0;
        for (int y = y0; y <= y1; y++)
            for (int z = z0; z <= z1; z++)
                for (int x = x0; x <= x1; x++) if (org.domi.furnace.server.block.B.block(level.getBlockState(new Pos(x, y, z))) == id) n++;
        return n;
    }

    /** 블록 상자(양 끝 포함)의 모든 상태를 y→z→x 순으로 해시에 섞는다. */
    public static IdentityHash blocks(ChunkLevel level, IdentityHash h, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int y = y0; y <= y1; y++)
            for (int z = z0; z <= z1; z++)
                for (int x = x0; x <= x1; x++) h.add(level.getBlockState(new Pos(x, y, z)));
        return h;
    }

    /**
     * 엔티티 하나의 상태: 종류·id·위치·속도·회전·땅 여부·틱 수 + 저장 NBT 전체(체력·효과·두뇌 기억·속성 등).
     * NBT 는 바이트로 직렬화해 섞는다 — 필드 하나만 바뀌어도 잡힌다. 지워진 엔티티는 "removed" 만.
     */
    public static IdentityHash entity(IdentityHash h, Entity e) {
        h.add(e.type().name()).add(e.id());
        if (e.isRemoved()) return h.add("removed");
        h.add(e.x()).add(e.y()).add(e.z()).add(e.vx()).add(e.vy()).add(e.vz()).add(e.yRot()).add(e.xRot()).add(e.onGround()).add(e.tickCount);
        var tag = e.save();
        if (tag != null) {
            try {
                h.add(org.domi.furnace.server.net.io.nbt.NbtIo.toBytes(tag, ""));
            } catch (java.io.IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
        }
        return h;
    }

    /** 레벨의 모든 엔티티(id 순)를 한 해시로. */
    public static IdentityHash entities(ChunkLevel level) {
        java.util.List<Entity> all = new java.util.ArrayList<>(level.entities().tickSnapshot());
        all.sort(java.util.Comparator.comparingInt(Entity::id));
        IdentityHash h = new IdentityHash().add(all.size());
        for (Entity e : all) entity(h, e);
        return h;
    }
}
