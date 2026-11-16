package org.bukkit.block;

/**
 * 블록 면과 방향. Bukkit·Paper 와 같은 순서·같은 이동량이다(보조 방향은 두 면의 합: WEST_NORTH_WEST = WEST + NORTH_WEST = (-2, 0, -1)).
 * SELF 가 맨 끝인 것도 같다 — 순서 번호(ordinal)를 저장하는 플러그인이 어긋나지 않게.
 */
public enum BlockFace {
    NORTH(0, 0, -1), EAST(1, 0, 0), SOUTH(0, 0, 1), WEST(-1, 0, 0), UP(0, 1, 0), DOWN(0, -1, 0),
    NORTH_EAST(NORTH, EAST), NORTH_WEST(NORTH, WEST), SOUTH_EAST(SOUTH, EAST), SOUTH_WEST(SOUTH, WEST),
    WEST_NORTH_WEST(WEST, NORTH_WEST), NORTH_NORTH_WEST(NORTH, NORTH_WEST), NORTH_NORTH_EAST(NORTH, NORTH_EAST), EAST_NORTH_EAST(EAST, NORTH_EAST),
    EAST_SOUTH_EAST(EAST, SOUTH_EAST), SOUTH_SOUTH_EAST(SOUTH, SOUTH_EAST), SOUTH_SOUTH_WEST(SOUTH, SOUTH_WEST), WEST_SOUTH_WEST(WEST, SOUTH_WEST),
    SELF(0, 0, 0);

    private final int modX, modY, modZ;

    BlockFace(int x, int y, int z) {
        this.modX = x;
        this.modY = y;
        this.modZ = z;
    }

    BlockFace(BlockFace face1, BlockFace face2) {
        this.modX = face1.getModX() + face2.getModX();
        this.modY = face1.getModY() + face2.getModY();
        this.modZ = face1.getModZ() + face2.getModZ();
    }

    public int getModX() { return modX; }

    public int getModY() { return modY; }

    public int getModZ() { return modZ; }

    /** 그 면이 향하는 단위 벡터(SELF 는 0 벡터). */
    public org.bukkit.util.Vector getDirection() {
        org.bukkit.util.Vector direction = new org.bukkit.util.Vector(modX, modY, modZ);
        if (modX != 0 || modY != 0 || modZ != 0) direction.normalize();
        return direction;
    }

    public BlockFace getOppositeFace() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
            case UP -> DOWN;
            case DOWN -> UP;
            case NORTH_EAST -> SOUTH_WEST;
            case NORTH_WEST -> SOUTH_EAST;
            case SOUTH_EAST -> NORTH_WEST;
            case SOUTH_WEST -> NORTH_EAST;
            case WEST_NORTH_WEST -> EAST_SOUTH_EAST;
            case NORTH_NORTH_WEST -> SOUTH_SOUTH_EAST;
            case NORTH_NORTH_EAST -> SOUTH_SOUTH_WEST;
            case EAST_NORTH_EAST -> WEST_SOUTH_WEST;
            case EAST_SOUTH_EAST -> WEST_NORTH_WEST;
            case SOUTH_SOUTH_EAST -> NORTH_NORTH_WEST;
            case SOUTH_SOUTH_WEST -> NORTH_NORTH_EAST;
            case WEST_SOUTH_WEST -> EAST_NORTH_EAST;
            case SELF -> SELF;
        };
    }
}
