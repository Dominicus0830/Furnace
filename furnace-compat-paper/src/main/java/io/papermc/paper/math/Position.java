package io.papermc.paper.math;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.NumberConversions;

/** 칸 좌표(정수)와 실수 좌표를 같은 꼴로 다루는 위치(Paper {@code io.papermc.paper.math.Position}). */
public interface Position {

    /** 정수 칸 좌표 위치. */
    static BlockPosition block(int x, int y, int z) { return new BlockPositionImpl(x, y, z); }

    /** 위치의 칸 좌표(floor). */
    static BlockPosition block(Location location) { return block(location.getBlockX(), location.getBlockY(), location.getBlockZ()); }

    /** 실수 좌표 위치. */
    static FinePosition fine(double x, double y, double z) { return new FinePositionImpl(x, y, z); }

    static FinePosition fine(Location location) { return fine(location.getX(), location.getY(), location.getZ()); }

    double x();

    double y();

    double z();

    default int blockX() { return NumberConversions.floor(x()); }

    default int blockY() { return NumberConversions.floor(y()); }

    default int blockZ() { return NumberConversions.floor(z()); }

    boolean isBlock();

    boolean isFine();

    /** 이 위치를 칸 모서리로 내린 위치. */
    default BlockPosition toBlock() { return block(blockX(), blockY(), blockZ()); }

    /** 이 위치가 든 칸의 가운데(+0.5). */
    default FinePosition toCenter() { return fine(blockX() + 0.5, blockY() + 0.5, blockZ() + 0.5); }

    Position offset(int x, int y, int z);

    Position offset(double x, double y, double z);

    default Location toLocation(World world) { return new Location(world, x(), y(), z()); }
}
