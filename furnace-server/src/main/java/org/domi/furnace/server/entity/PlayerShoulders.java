// 플레이어 어깨에 앉은 몹(바닐라 Player.setEntityOnShoulder / removeEntitiesOnShoulder / playShoulderEntityAmbientSound / NBT ShoulderEntityLeft·Right).
// 어깨 몹은 엔티티가 아니라 NBT 로만 들고 있다. 앵무새(다른 region)도 앉히므로 상태는 Player 의 shoulderLock 으로 지킨다.
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

    /** Player.removeEntitiesOnShoulder: 앉힌 지 20틱이 지났으면 두 어깨의 몹을 플레이어 자리에서 다시 세상에 내보낸다. */
    public static void removeEntitiesOnShoulder(Player p) {
        ChunkLevel level = p.currentLevel;
        NbtCompound left, right;
        synchronized (p.shoulderLock()) {
            if (level == null || p.timeEntitySatOnShoulder() + 20L >= level.gameTime()) return;
            left = p.shoulderLeft();
            right = p.shoulderRight();
            p.setShoulderLeft(new NbtCompound());
            p.setShoulderRight(new NbtCompound());
        }
        respawn(p, level, left);
        respawn(p, level, right);
    }

    /** Player.respawnEntityOnShoulder: 주인(TamableAnimal)을 이 플레이어로 하고 (x, y + 0.7, z) 에 다시 세운다. */
    private static void respawn(Player p, ChunkLevel level, NbtCompound tag) {
        if (tag == null || tag.tags().isEmpty()) return;
        Entity e = Entities.load(level, tag);
        if (e == null) return;
        if (e instanceof TamableAnimal t) t.setOwnerUUID(p.uuid());
        e.setPos(p.x(), p.y() + 0.7F, p.z());
        level.addFreshEntity(e);
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
