// 탄 채 나간 플레이어의 탈것(바닐라 ServerPlayer.addAdditionalSaveData 의 RootVehicle, PlayerList.placeNewPlayer·remove 의 탈것 부분).
// 플레이어 데이터 본체는 연결 스레드가 저장하고, 탈것은 주인 region 스레드가 떠서 같은 플레이어 데이터 실행기에 RootVehicle 만 덧쓴다.
// 본체 저장은 RootVehicle 을 건드리지 않으므로 두 저장의 순서가 바뀌어도 결과가 같다.
package org.domi.furnace.server.entity;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class PlayerVehicleSave {
    private PlayerVehicleSave() { }

    public static final String KEY = "RootVehicle";

    /** RootVehicle {Attach: 바로 탄 것의 UUID, Entity: 뿌리 탈것(승객 포함)} — 플레이어 하나만 태운 뿌리 탈것일 때만. 아니면 null. */
    public static NbtCompound rootVehicleTag(Player p) {
        Entity v = p.vehicle();
        if (v == null) return null;
        Entity root = v.rootVehicle();
        if (!root.hasExactlyOnePlayerPassenger()) return null;
        NbtCompound entity = root.save();
        if (entity == null) return null;
        NbtCompound tag = new NbtCompound();
        tag.putIntArray("Attach", Entity.uuidToInts(v.uuid()));
        tag.put("Entity", entity);
        return tag;
    }

    /** 자동 저장(주인 region 스레드): 탄 것이면 RootVehicle 을 새로 쓰고, 안 탔는데 파일에 남아 있으면 지운다. */
    public static void autosave(Player p, Executor io, Path worldDir) {
        NbtCompound tag = rootVehicleTag(p);
        if (tag == null && !p.rootVehicleOnDisk) return;
        p.rootVehicleOnDisk = tag != null;
        submit(p, io, worldDir, tag);
    }

    /**
     * 퇴장(주인 region 스레드, PlayerList.remove): RootVehicle 을 저장하고, 플레이어 하나만 태운 탈것이면 승객째 월드에서 뺀다
     * (UNLOADED_WITH_PLAYER — 떨굼·죽음 없이). 그다음 플레이어를 내린다. 두 번 불러도 같다.
     */
    public static void onDisconnect(Player p, Executor io, Path worldDir) {
        if (p.quitMountHandled) return;
        p.quitMountHandled = true;
        NbtCompound tag = rootVehicleTag(p);
        if (tag != null || p.rootVehicleOnDisk) {
            p.rootVehicleOnDisk = tag != null;
            submit(p, io, worldDir, tag);
        }
        Entity v = p.vehicle();
        if (v == null) return;
        Entity root = v.rootVehicle();
        boolean removeMount = root.hasExactlyOnePlayerPassenger();
        p.detachFromVehicle();   // 끊긴 플레이어라 내릴 자리로 옮기지 않는다
        if (removeMount) {
            java.util.List<Rider> all = new java.util.ArrayList<>();
            root.collectIndirectPassengers(all);
            for (Rider r : all) if (r instanceof Entity e) e.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
            root.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        }
    }

    private static void submit(Player p, Executor io, Path worldDir, NbtCompound tag) {
        if (io == null || worldDir == null) return;
        UUID id = p.uuid();
        try {
            io.execute(() -> {
                try {
                    PlayerData.saveRootVehicle(worldDir, id, tag);
                } catch (RuntimeException e) {
                    org.slf4j.LoggerFactory.getLogger(PlayerVehicleSave.class).error("탈것 저장 실패 {}", p.name(), e);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            org.slf4j.LoggerFactory.getLogger(PlayerVehicleSave.class).error("탈것 저장 거부(종료 중): {}", p.name());
        }
    }

    /**
     * 접속(주인 region 스레드, placeNewPlayer): RootVehicle 을 승객째 불러 월드에 넣고 Attach 의 것에 강제로 태운다.
     * 같은 UUID 가 이미 월드에 있으면 넣지 않는다(addWithUUID). 태우지 못하면 불러온 것을 모두 지운다.
     */
    public static void restore(Player p, ChunkLevel level, NbtCompound saved) {
        if (saved == null || level == null || !(saved.tags().get(KEY) instanceof NbtCompound rv)) return;
        p.rootVehicleOnDisk = true;
        if (!(rv.tags().get("Entity") instanceof NbtCompound et)) return;
        Entity root = Entities.load(level, et.copy());
        if (root == null || !level.addFreshEntityWithPassengers(root)) return;
        int[] a = rv.getIntArray("Attach");
        UUID attach = a.length == 4 ? org.domi.furnace.server.bossbar.CustomBossEvents.uuidFromInts(a) : null;
        java.util.List<Rider> all = new java.util.ArrayList<>();
        root.collectIndirectPassengers(all);
        if (root.uuid().equals(attach)) {
            p.startRiding(root, true);
        } else {
            for (Rider r : all) {
                if (r instanceof Entity e && e.uuid().equals(attach)) {
                    p.startRiding(e, true);
                    break;
                }
            }
        }
        if (p.vehicle() == null) {
            org.slf4j.LoggerFactory.getLogger(PlayerVehicleSave.class).warn("Couldn't reattach entity to player");
            root.discard();
            for (Rider r : all) if (r instanceof Entity e) e.discard();
        }
    }
}
