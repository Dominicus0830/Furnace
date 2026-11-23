// 바닐라 HangingEntity(BlockAttachedEntity + HangingEntity): 블록 면에 걸리는 엔티티(액자·그림). 칸 위치와 방향으로 얇은 상자를 만들고,
// 100틱마다 붙은 면이 아직 단단한지 확인한다. 움직이거나 밀리면 떨어진다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.DiodeBlock;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public abstract class HangingEntity extends Entity {

    /** BlockAttachedEntity.thunderHit: 번개에 맞아도 아무 일 없다. */
    @Override
    public void thunderHit(org.domi.furnace.server.block.ChunkLevel level, LightningBolt bolt) {}

    protected Pos blockPos;
    protected int direction = Dir.SOUTH;
    private int checkInterval;

    protected HangingEntity(EntityType type, ChunkLevel level, int id) {
        super(type, level, id);
        refreshBoundingBox();
    }

    protected HangingEntity(EntityType type, ChunkLevel level, int id, Pos pos) {
        this(type, level, id);
        this.blockPos = pos;
    }

    /** 칸과 방향으로 정한 상자. */
    protected abstract AABB calculateBoundingBox(Pos pos, int direction);

    @Override
    protected AABB makeBb(double px, double py, double pz) {
        return blockPos == null ? super.makeBb(px, py, pz) : calculateBoundingBox(blockPos, direction);
    }

    /** recalculateBoundingBox: 위치를 상자 가운데로 옮기고 상자를 갱신한다. */
    protected final void recalculate() {
        if (blockPos == null) return;
        AABB box = calculateBoundingBox(blockPos, direction);
        setPosRaw((box.minX() + box.maxX()) / 2.0, (box.minY() + box.maxY()) / 2.0, (box.minZ() + box.maxZ()) / 2.0);
        refreshBoundingBox();
    }

    @Override
    public void setPos(double nx, double ny, double nz) {
        blockPos = new Pos((int) Math.floor(nx), (int) Math.floor(ny), (int) Math.floor(nz));
        recalculate();
    }

    public Pos pos() { return blockPos; }

    public int direction() { return direction; }

    /** 방향을 정하고 회전·상자를 맞춘다(수평만 있는 종류는 호출 쪽이 검사). */
    protected void setDirection(int dir) {
        direction = dir;
        if (Dir.horizontal(dir)) {
            setXRot(0.0F);
            setYRot(Dir.to2D(dir) * 90);
        } else {
            setXRot(-90 * (Dir.positive(dir) ? 1 : -1));
            setYRot(0.0F);
        }
        xRotO = xRot();
        yRotO = yRot();
        recalculate();
    }

    /**
     * 바닐라 HangingEntity#rotate(Rotation): 걸린 방향이 수평이면 방향만 돌린다(각도·상자는 그대로 — 바닐라도 필드만 바꾼다).
     * 돌려줌은 wrapDegrees(yRot) 에 돌림을 더한 값. {@code rotation} 은 Rotation 서수(0 없음·1 시계 90·2 180·3 반시계 90).
     */
    public float rotate(int rotation) {
        direction = rotatedDirection(direction, rotation);
        return org.domi.furnace.server.entity.ai.control.Mth.wrapDegrees(yRot()) + rotateDegrees(rotation);
    }

    /** rotate 가 바꾸는 방향: 수평이면 180 → 반대, 시계 90 → 시계, 반시계 90 → 반시계(위·아래는 그대로). */
    public static int rotatedDirection(int dir, int rotation) {
        if (!Dir.horizontal(dir)) return dir;
        return switch (rotation) {
            case 2 -> Dir.opposite(dir);
            case 3 -> Dir.counterClockWise(dir);
            case 1 -> Dir.clockWise(dir);
            default -> dir;
        };
    }

    /** rotate 가 wrapDegrees(yRot) 에 더하는 값(Entity#rotate 와 90·270 이 바뀌어 있다 — 바닐라 그대로). */
    public static float rotateDegrees(int rotation) {
        return switch (rotation) {
            case 2 -> 180.0F;
            case 3 -> 90.0F;
            case 1 -> 270.0F;
            default -> 0.0F;
        };
    }

    /** 바닐라 Mirror#getRotation(direction) — HangingEntity#mirror 가 rotate 에 넘기는 돌림. mirror 는 BlockStateTable 값(1 LEFT_RIGHT·2 FRONT_BACK). */
    public static int mirrorRotation(int mirror, int dir) {
        int axis = Dir.axis(dir);
        return mirror == 1 && axis == Dir.AXIS_Z || mirror == 2 && axis == Dir.AXIS_X ? 2 : 0;
    }

    /**
     * 살아 있는 월드에 템플릿으로 놓은 걸이 엔티티(바닐라 placeEntities 는 레벨에 바로 넣는다): 불러오기의 setDirection 이 덮은 yRot 을
     * 저장 각도(바닐라 moveTo 의 각도)로 되돌린다. 다시 불러오면 setDirection 이 정한다 — 바닐라도 같다.
     */
    public void keepPlacedYaw(NbtCompound nbt) {
        var r = nbt.getList("Rotation");
        if (r.size() > 0 && r.get(0) instanceof Float y) {
            setYRot(y);
            yRotO = y;
        }
    }

    @Override public int spawnData() { return direction; }

    @Override public boolean isPickable() { return true; }

    @Override
    public void tick() {
        if (checkInterval++ == 100) {
            checkInterval = 0;
            if (!isRemoved() && !survives()) {
                discard();
                dropItem(null);
            }
        }
    }

    /** 붙은 면이 단단하고 겹치는 다른 걸이 엔티티가 없는가. */
    public boolean survives() {
        if (!level.noCollision(this, boundingBox())) return false;
        AABB behind = boundingBox().move(-0.5 * Dir.DX[direction], -0.5 * Dir.DY[direction], -0.5 * Dir.DZ[direction]).deflate(1.0E-7);
        for (int x = (int) Math.floor(behind.minX()); x <= (int) Math.floor(behind.maxX()); x++) {
            for (int y = (int) Math.floor(behind.minY()); y <= (int) Math.floor(behind.maxY()); y++) {
                for (int z = (int) Math.floor(behind.minZ()); z <= (int) Math.floor(behind.maxZ()); z++) {
                    int s = level.getBlockState(new Pos(x, y, z));
                    if (!(B.isSolid(s) || Dir.axis(direction) != Dir.AXIS_Y && DiodeBlock.isDiode(s))) return false;
                }
            }
        }
        return level.getEntities(this, boundingBox(), e -> e instanceof HangingEntity).isEmpty();
    }

    /** 부서질 때 아이템을 떨군다(attacker 는 때린 쪽, 없으면 null). */
    public abstract void dropItem(Targetable attacker);

    public abstract void playPlacementSound();

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (isInvulnerableTo(source)) return false;
        if (!isRemoved()) {
            kill();
            markHurt();
            dropItem(attacker);
        }
        return true;
    }

    @Override public boolean hurt(Damage source, float amount) { return hurtBy(source, amount, null); }

    @Override
    public boolean skipAttackInteraction(Player attacker) {
        if (attacker.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) return true;
        hurtBy(Damage.playerAttack(attacker), 0.0F, attacker);
        return true;
    }

    @Override public boolean isIgnoringBlockTriggers() { return true; }

    /** 플레이어 우클릭(Entity.interact). */
    public boolean interact(Player p, int hand) { return false; }

    /** 걸이 엔티티는 움직임으로 밀리면 떨어진다. */
    @Override
    public void move(int mover, double dx, double dy, double dz) {
        if (!isRemoved() && dx * dx + dy * dy + dz * dz > 0.0) {
            kill();
            dropItem(null);
        }
    }

    protected void spawnItem(ItemStack stack) {
        if (stack.isEmpty()) return;
        var ie = new ItemEntity(level, level.nextEntityId(), x() + Dir.DX[direction] * 0.15F, y() + 0.0, z() + Dir.DZ[direction] * 0.15F, stack);
        ie.setDefaultPickUpDelay();
        level.addFreshEntity(ie);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        Pos p = blockPos == null ? new Pos(0, 0, 0) : blockPos;
        tag.putInt("TileX", p.x());
        tag.putInt("TileY", p.y());
        tag.putInt("TileZ", p.z());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        blockPos = new Pos(tag.getIntOr0("TileX"), tag.getIntOr0("TileY"), tag.getIntOr0("TileZ"));
    }

}
