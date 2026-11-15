package net.minecraft.world.phys;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 블록에 맞은 자리 shim(Mojang 이름, ProtocolLib 다리 2b-3). 바닐라 1.21.1 과 같은 필드·생성자 — 블록에 대고 쓰기 패킷(ServerboundUseItemOnPacket)에 들어 있다.
 * location 은 맞은 점(블록 좌표 + 칸 안 위치), 와이어에는 칸 안 위치(cursor, float 셋)로 간다.
 */
public class BlockHitResult extends HitResult {

    private final Direction direction;
    private final BlockPos blockPos;
    private final boolean miss;
    private final boolean inside;

    public static BlockHitResult miss(Vec3 location, Direction direction, BlockPos blockPos) {
        return new BlockHitResult(true, location, direction, blockPos, false);
    }

    public BlockHitResult(Vec3 location, Direction direction, BlockPos blockPos, boolean inside) {
        this(false, location, direction, blockPos, inside);
    }

    private BlockHitResult(boolean miss, Vec3 location, Direction direction, BlockPos blockPos, boolean inside) {
        super(location);
        this.miss = miss;
        this.direction = direction;
        this.blockPos = blockPos;
        this.inside = inside;
    }

    public BlockHitResult withDirection(Direction direction) { return new BlockHitResult(miss, location, direction, blockPos, inside); }

    public BlockHitResult withPosition(BlockPos blockPos) { return new BlockHitResult(miss, location, direction, blockPos, inside); }

    public BlockPos getBlockPos() { return blockPos; }

    public Direction getDirection() { return direction; }

    @Override
    public Type getType() { return miss ? Type.MISS : Type.BLOCK; }

    public boolean isInside() { return inside; }
}
