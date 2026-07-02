package org.domi.furnace.server.world.gen.feature;

public record BlockPos(int x, int y, int z) {

    @Override
    public int hashCode() {
        return (this.y + this.z * 31) * 31 + this.x;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockPos p)) return false;
        return this.x == p.x && this.y == p.y && this.z == p.z;
    }
}
