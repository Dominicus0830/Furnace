package net.minecraft.world.entity.decoration;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;
import org.domi.furnace.api.internal.ChunkInternals.EntityEdit;
import org.domi.furnace.api.internal.ChunkInternals.EntityOp;
import org.domi.furnace.compat.nms.NmsEntityOwner;

/**
 * 걸이 엔티티(액자·그림)의 방향. 월드에 있는 엔티티는 서버에 묻고, 아직 월드에 없는 엔티티(EntityType#create → load)는 불러온 태그대로 이 조각이 들고
 * 조작을 기록해 넣을 때 서버가 재생한다(Axiom 이 소환 전에 돌린다).
 */
class HangingEntity {

    /** 아직 월드에 없는 엔티티의 걸린 방향(바닐라 필드 기본값 SOUTH). 월드에 있는 엔티티는 쓰지 않는다. */
    private volatile Direction furnaceDetachedDirection = Direction.SOUTH;

    /** 바닐라 getDirection: 걸린 방향. */
    public Direction getDirection() {
        NmsEntityOwner.read(this, "HangingEntity#getDirection");
        if (furnaceNotInWorld()) return furnaceDetachedDirection;
        return Direction.from3DDataValue((Integer) furnaceEditOf("HangingEntity#getDirection", new EntityEdit.HangingDirection()));
    }

    /** 바닐라 rotate(Rotation): 방향이 수평이면 방향만 돌리고(각도·상자는 그대로), wrapDegrees(yRot) 에 돌림을 더한 값을 준다. */
    public float rotate(Rotation rotation) {
        NmsEntityOwner.write(this, "HangingEntity#rotate");
        if (!furnaceNotInWorld()) return (Float) furnaceEditOf("HangingEntity#rotate", new EntityEdit.HangingRotate(rotation.ordinal()));
        furnaceDetachedOp("HangingEntity#rotate", new EntityOp.HangingRotate(rotation.ordinal()));
        Direction d = furnaceDetachedDirection;
        if (d.getAxis() != Direction.Axis.Y) {
            furnaceDetachedDirection = switch (rotation) {
                case CLOCKWISE_180 -> d.getOpposite();
                case COUNTERCLOCKWISE_90 -> d.getCounterClockWise();
                case CLOCKWISE_90 -> d.getClockWise();
                default -> d;
            };
        }
        float f = getYRot() % 360.0F;   // Mth.wrapDegrees
        if (f >= 180.0F) f -= 360.0F;
        if (f < -180.0F) f += 360.0F;
        return switch (rotation) {
            case CLOCKWISE_180 -> f + 180.0F;
            case COUNTERCLOCKWISE_90 -> f + 90.0F;
            case CLOCKWISE_90 -> f + 270.0F;
            default -> f;
        };
    }

    /** 아직 월드에 없는 엔티티에 바닐라 HangingEntity#setDirection 의 결과(방향, yRot = 2D 값 × 90). 액자는 재정의(위·아래도 된다). */
    protected void furnaceDetachedSetDirection(Direction d) {
        furnaceDetachedDirection = d;
        furnaceDetachedRot(d.get2DDataValue() * 90, getXRot());
    }

    /** 바닐라 Painting#readAdditionalSaveData: "facing"(2D 값)으로 setDirection. 액자는 ItemFrame 조각이 재정의한다. */
    @Override
    protected void furnaceDetachedLoaded(CompoundTag tag) {
        if (this instanceof Painting) furnaceDetachedSetDirection(Direction.from2DDataValue(tag.getByte("facing")));
    }
}
