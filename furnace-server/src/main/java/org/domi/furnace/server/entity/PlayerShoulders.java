// 플레이어 어깨에 앉은 몹(바닐라 Player.setEntityOnShoulder / removeEntitiesOnShoulder / playShoulderEntityAmbientSound / NBT ShoulderEntityLeft·Right).
// 어깨 몹은 엔티티가 아니라 NBT 로만 들고 있다. 앉히기는 앵무새와 플레이어가 같은 region(몸이 겹친다)일 때만, 내려놓은 몹은 플레이어 region
// 스레드에서 세운다. 어깨 NBT 는 저장(IO)·명령 스레드도 읽으므로 Player 의 shoulderLock 으로 지킨다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtTags;

public final class PlayerShoulders {
    private PlayerShoulders() { }

    /** Entity.isInWater — 플레이어는 환경 조사 결과(PlayerVitals)를 쓴다. */
    public static boolean isInWater(Player p) { return p.vitals().isInWater(); }

    /** Entity.isInPowderSnow(가정): 발 칸이 가루눈이면 참. */
    public static boolean isInPowderSnow(Player p) {
        ChunkLevel level = p.currentLevel;
        if (level == null) return false;
        return org.domi.furnace.server.block.B.is(level.getBlockState(p.blockPosition()), "minecraft:powder_snow");
    }

    /**
     * Player.setEntityOnShoulder: 탑승 중·공중·물속·가루눈이면 못 앉힌다. 왼쪽이 비었으면 왼쪽, 아니면 오른쪽이 비었으면 오른쪽.
     * 조건 조사는 잠금 밖에서 한다(PlayerVitals 잠금과 순서가 꼬이지 않게).
     */
    public static boolean setEntityOnShoulder(Player p, NbtCompound tag) {
        if (!p.onOwnerThread()) return false;   // 몸이 겹쳐야 앉으므로 같은 region 이다 — 다른 region 의 플레이어 상태는 읽지 않는다
        if (p.isPassenger() || !p.onGround() || isInWater(p) || isInPowderSnow(p)) return false;
        ChunkLevel level = p.currentLevel;
        long now = level == null ? 0L : level.gameTime();
        synchronized (p.shoulderLock()) {
            if (p.shoulderLeft().tags().isEmpty()) {
                p.setShoulderLeft(tag);
            } else if (p.shoulderRight().tags().isEmpty()) {
                p.setShoulderRight(tag);
            } else {
                return false;
            }
            p.timeEntitySatOnShoulder(now);
            return true;
        }
    }

    /**
     * Player.removeEntitiesOnShoulder: 앉힌 지 20틱이 지났으면 두 어깨의 몹을 플레이어 자리에서 다시 세상에 내보낸다.
     * 어깨는 부른 스레드에서 바로 비우고(다른 앵무새가 그 사이 앉지 못하게), 몹은 플레이어를 가진 region 스레드에서 세운다 —
     * 다른 스레드(명령의 /gamemode·/damage, 다른 region 의 피해)에서 불리면 다음 플레이어 틱에 그 자리에서 내보낸다.
     */
    public static void removeEntitiesOnShoulder(Player p) {
        ChunkLevel level = p.currentLevel;
        NbtCompound left, right;
        synchronized (p.shoulderLock()) {
            if (level == null || p.timeEntitySatOnShoulder() + 20L >= level.gameTime()) return;
            left = p.shoulderLeft();
            right = p.shoulderRight();
            if (left.tags().isEmpty() && right.tags().isEmpty()) return;
            p.setShoulderLeft(new NbtCompound());
            p.setShoulderRight(new NbtCompound());
        }
        if (p.onOwnerThread()) {
            respawn(p, level, left);
            respawn(p, level, right);
            return;
        }
        p.runOnOwner(() -> {
            ChunkLevel here = p.currentLevel;
            if (here == null) {   // 그 사이 접속이 끊겼다 — 어깨로 되돌려 저장되게 한다(몹을 잃지 않게)
                restore(p, left, right);
                return;
            }
            respawn(p, here, left);
            respawn(p, here, right);
        });
    }

    /** 내보내지 못한 어깨 몹을 빈 어깨에 되돌린다. */
    private static void restore(Player p, NbtCompound left, NbtCompound right) {
        synchronized (p.shoulderLock()) {
            if (!left.tags().isEmpty() && p.shoulderLeft().tags().isEmpty()) p.setShoulderLeft(left);
            if (!right.tags().isEmpty() && p.shoulderRight().tags().isEmpty()) p.setShoulderRight(right);
        }
    }

    /** Player.respawnEntityOnShoulder: 주인(TamableAnimal)을 이 플레이어로 하고 (x, y + 0.7, z) 에 다시 세운다. */
    private static void respawn(Player p, ChunkLevel level, NbtCompound tag) {
        if (tag == null || tag.tags().isEmpty()) return;
        Entity e = Entities.load(level, tag);
        if (e == null) return;
        if (e instanceof TamableAnimal t) t.setOwnerUUID(p.uuid());
        e.setPos(p.x(), p.y() + 0.7F, p.z());
        level.addFreshEntity(e.spawnReason("SHOULDER_ENTITY"));
    }

    /** Player.playShoulderEntityAmbientSound: 1/200 로 앉은 앵무새가 소리를 낸다(근처 몹 흉내, 아니면 앵무새 울음). */
    public static void tick(Player p) {
        ChunkLevel level = p.currentLevel;
        if (level == null) return;
        ambient(p, level, p.shoulderLeft());
        ambient(p, level, p.shoulderRight());
        // aiStep 끝: 떨어지는 중(0.5 초과)·물속·비행·잠·가루눈이면 내려놓는다
        if (p.vitals().fallDistance() > 0.5F || isInWater(p) || p.vitals().isFlying() || p.isSleeping() || isInPowderSnow(p)) removeEntitiesOnShoulder(p);
    }

    private static void ambient(Player p, ChunkLevel level, NbtCompound tag) {
        if (tag == null || tag.tags().isEmpty()) return;
        boolean silent = tag.tags().get("Silent") instanceof Number n && n.byteValue() != 0;
        if (silent || level.random().nextInt(200) != 0) return;
        if (!"minecraft:parrot".equals(tag.tags().get("id"))) return;
        if (!Parrot.imitateNearbyMobs(level, p.vitals().health() > 0.0f, p.boundingBox(), p.x(), p.y(), p.z(), "player")) {
            level.playSound(null, p.x(), p.y(), p.z(), Parrot.getAmbient(level, level.random()), "player", 1.0F, Parrot.getPitch(level.random()));
        }
    }

    public static void save(Player p, NbtCompound out) {
        NbtCompound l, r;
        synchronized (p.shoulderLock()) {   // 바닐라 save 는 tag.put(…, getShoulderEntityLeft()) 처럼 복사본을 넣는다
            l = (NbtCompound) NbtTags.copy(p.shoulderLeft());
            r = (NbtCompound) NbtTags.copy(p.shoulderRight());
        }
        if (!l.tags().isEmpty()) out.putCompound("ShoulderEntityLeft", l);
        if (!r.tags().isEmpty()) out.putCompound("ShoulderEntityRight", r);
    }

    public static void load(Player p, NbtCompound in) {
        if (in.tags().get("ShoulderEntityLeft") instanceof NbtCompound c) p.setShoulderLeft(c);
        if (in.tags().get("ShoulderEntityRight") instanceof NbtCompound c) p.setShoulderRight(c);
    }
}
