package org.bukkit.block;

public enum BlockFace {
    NORTH(0, 0, -1), EAST(1, 0, 0), SOUTH(0, 0, 1), WEST(-1, 0, 0), UP(0, 1, 0), DOWN(0, -1, 0), SELF(0, 0, 0);

    private final int modX, modY, modZ;

    BlockFace(int x, int y, int z) {
        this.modX = x;
        this.modY = y;
        this.modZ = z;
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
            case SELF -> SELF;
        };
    }
}
