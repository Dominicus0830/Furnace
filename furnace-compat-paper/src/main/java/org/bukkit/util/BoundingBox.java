package org.bukkit.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

/**
 * 축 정렬 상자(가변, 최솟값 ≤ 최댓값이 늘 유지된다). 설정에는 별칭 {@code BoundingBox}, 키 minX·minY·minZ·maxX·maxY·maxZ 로 저장된다(A15-01).
 * 인자 검사 문구와 유한값 검사는 Paper 와 같다.
 */
@SerializableAs("BoundingBox")
public class BoundingBox implements Cloneable, ConfigurationSerializable {

    private static void check(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException(message);
    }

    public static BoundingBox of(Vector corner1, Vector corner2) {
        check(corner1 != null, "Corner1 is null!");
        check(corner2 != null, "Corner2 is null!");
        return new BoundingBox(corner1.getX(), corner1.getY(), corner1.getZ(), corner2.getX(), corner2.getY(), corner2.getZ());
    }

    public static BoundingBox of(Location corner1, Location corner2) {
        check(corner1 != null, "Corner1 is null!");
        check(corner2 != null, "Corner2 is null!");
        check(Objects.equals(corner1.getWorld(), corner2.getWorld()), "Locations from different worlds!");
        return new BoundingBox(corner1.getX(), corner1.getY(), corner1.getZ(), corner2.getX(), corner2.getY(), corner2.getZ());
    }

    /** 두 블록 칸을 모두 감싸는 상자(큰 쪽 칸은 +1). */
    public static BoundingBox of(Block corner1, Block corner2) {
        check(corner1 != null, "Corner1 is null!");
        check(corner2 != null, "Corner2 is null!");
        check(Objects.equals(corner1.getWorld(), corner2.getWorld()), "Blocks from different worlds!");
        int x1 = corner1.getX(), y1 = corner1.getY(), z1 = corner1.getZ();
        int x2 = corner2.getX(), y2 = corner2.getY(), z2 = corner2.getZ();
        return new BoundingBox(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, Math.max(z1, z2) + 1);
    }

    public static BoundingBox of(Block block) {
        check(block != null, "Block is null!");
        return new BoundingBox(block.getX(), block.getY(), block.getZ(), block.getX() + 1, block.getY() + 1, block.getZ() + 1);
    }

    public static BoundingBox of(Vector center, double x, double y, double z) {
        check(center != null, "Center is null!");
        return new BoundingBox(center.getX() - x, center.getY() - y, center.getZ() - z, center.getX() + x, center.getY() + y, center.getZ() + z);
    }

    public static BoundingBox of(Location center, double x, double y, double z) {
        check(center != null, "Center is null!");
        return new BoundingBox(center.getX() - x, center.getY() - y, center.getZ() - z, center.getX() + x, center.getY() + y, center.getZ() + z);
    }

    private double minX;
    private double minY;
    private double minZ;
    private double maxX;
    private double maxY;
    private double maxZ;

    public BoundingBox() { this.resize(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D); }

    public BoundingBox(double x1, double y1, double z1, double x2, double y2, double z2) { this.resize(x1, y1, z1, x2, y2, z2); }

    /** 두 꼭짓점으로 크기를 다시 정한다(순서는 상관없다). 유한하지 않은 값이면 IllegalArgumentException. */
    public BoundingBox resize(double x1, double y1, double z1, double x2, double y2, double z2) {
        NumberConversions.checkFinite(x1, "x1 not finite");
        NumberConversions.checkFinite(y1, "y1 not finite");
        NumberConversions.checkFinite(z1, "z1 not finite");
        NumberConversions.checkFinite(x2, "x2 not finite");
        NumberConversions.checkFinite(y2, "y2 not finite");
        NumberConversions.checkFinite(z2, "z2 not finite");
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        return this;
    }

    public double getMinX() { return minX; }

    public double getMinY() { return minY; }

    public double getMinZ() { return minZ; }

    public Vector getMin() { return new Vector(minX, minY, minZ); }

    public double getMaxX() { return maxX; }

    public double getMaxY() { return maxY; }

    public double getMaxZ() { return maxZ; }

    public Vector getMax() { return new Vector(maxX, maxY, maxZ); }

    public double getWidthX() { return (this.maxX - this.minX); }

    public double getWidthZ() { return (this.maxZ - this.minZ); }

    public double getHeight() { return (this.maxY - this.minY); }

    public double getVolume() { return (this.getHeight() * this.getWidthX() * this.getWidthZ()); }

    public double getCenterX() { return (this.minX + this.getWidthX() * 0.5D); }

    public double getCenterY() { return (this.minY + this.getHeight() * 0.5D); }

    public double getCenterZ() { return (this.minZ + this.getWidthZ() * 0.5D); }

    public Vector getCenter() { return new Vector(this.getCenterX(), this.getCenterY(), this.getCenterZ()); }

    public BoundingBox copy(BoundingBox other) {
        check(other != null, "Other bounding box is null!");
        return this.resize(other.getMinX(), other.getMinY(), other.getMinZ(), other.getMaxX(), other.getMaxY(), other.getMaxZ());
    }

    /** 음수 방향·양수 방향으로 넓힌다(음수 값은 줄인다 — 서로 지나치면 가운데에서 멈춘다). */
    public BoundingBox expand(double negativeX, double negativeY, double negativeZ, double positiveX, double positiveY, double positiveZ) {
        if (negativeX == 0.0D && negativeY == 0.0D && negativeZ == 0.0D && positiveX == 0.0D && positiveY == 0.0D && positiveZ == 0.0D) return this;
        double newMinX = this.minX - negativeX;
        double newMinY = this.minY - negativeY;
        double newMinZ = this.minZ - negativeZ;
        double newMaxX = this.maxX + positiveX;
        double newMaxY = this.maxY + positiveY;
        double newMaxZ = this.maxZ + positiveZ;
        // 줄일 때 서로 지나치지 않게
        if (newMinX > newMaxX) {
            double centerX = this.getCenterX();
            if (newMaxX >= centerX) {
                newMinX = newMaxX;
            } else if (newMinX <= centerX) {
                newMaxX = newMinX;
            } else {
                newMinX = centerX;
                newMaxX = centerX;
            }
        }
        if (newMinY > newMaxY) {
            double centerY = this.getCenterY();
            if (newMaxY >= centerY) {
                newMinY = newMaxY;
            } else if (newMinY <= centerY) {
                newMaxY = newMinY;
            } else {
                newMinY = centerY;
                newMaxY = centerY;
            }
        }
        if (newMinZ > newMaxZ) {
            double centerZ = this.getCenterZ();
            if (newMaxZ >= centerZ) {
                newMinZ = newMaxZ;
            } else if (newMinZ <= centerZ) {
                newMaxZ = newMinZ;
            } else {
                newMinZ = centerZ;
                newMaxZ = centerZ;
            }
        }
        return this.resize(newMinX, newMinY, newMinZ, newMaxX, newMaxY, newMaxZ);
    }

    public BoundingBox expand(double x, double y, double z) { return this.expand(x, y, z, x, y, z); }

    public BoundingBox expand(Vector expansion) {
        check(expansion != null, "Expansion is null!");
        double x = expansion.getX(), y = expansion.getY(), z = expansion.getZ();
        return this.expand(x, y, z, x, y, z);
    }

    public BoundingBox expand(double expansion) { return this.expand(expansion, expansion, expansion, expansion, expansion, expansion); }

    /** 방향(dirX·dirY·dirZ)의 부호 쪽 면만 expansion × 성분 만큼 넓힌다. */
    public BoundingBox expand(double dirX, double dirY, double dirZ, double expansion) {
        if (expansion == 0.0D) return this;
        if (dirX == 0.0D && dirY == 0.0D && dirZ == 0.0D) return this;
        double negativeX = (dirX < 0.0D ? (-dirX * expansion) : 0.0D);
        double negativeY = (dirY < 0.0D ? (-dirY * expansion) : 0.0D);
        double negativeZ = (dirZ < 0.0D ? (-dirZ * expansion) : 0.0D);
        double positiveX = (dirX > 0.0D ? (dirX * expansion) : 0.0D);
        double positiveY = (dirY > 0.0D ? (dirY * expansion) : 0.0D);
        double positiveZ = (dirZ > 0.0D ? (dirZ * expansion) : 0.0D);
        return this.expand(negativeX, negativeY, negativeZ, positiveX, positiveY, positiveZ);
    }

    public BoundingBox expand(Vector direction, double expansion) {
        check(direction != null, "Direction is null!");
        return this.expand(direction.getX(), direction.getY(), direction.getZ(), expansion);
    }

    public BoundingBox expand(BlockFace blockFace, double expansion) {
        check(blockFace != null, "Block face is null!");
        if (blockFace == BlockFace.SELF) return this;
        return this.expand(blockFace.getDirection(), expansion);
    }

    public BoundingBox expandDirectional(double dirX, double dirY, double dirZ) { return this.expand(dirX, dirY, dirZ, 1.0D); }

    public BoundingBox expandDirectional(Vector direction) {
        check(direction != null, "Expansion is null!");
        return this.expand(direction.getX(), direction.getY(), direction.getZ(), 1.0D);
    }

    public BoundingBox union(double posX, double posY, double posZ) {
        double newMinX = Math.min(this.minX, posX);
        double newMinY = Math.min(this.minY, posY);
        double newMinZ = Math.min(this.minZ, posZ);
        double newMaxX = Math.max(this.maxX, posX);
        double newMaxY = Math.max(this.maxY, posY);
        double newMaxZ = Math.max(this.maxZ, posZ);
        if (newMinX == this.minX && newMinY == this.minY && newMinZ == this.minZ && newMaxX == this.maxX && newMaxY == this.maxY && newMaxZ == this.maxZ) return this;
        return this.resize(newMinX, newMinY, newMinZ, newMaxX, newMaxY, newMaxZ);
    }

    public BoundingBox union(Vector position) {
        check(position != null, "Position is null!");
        return this.union(position.getX(), position.getY(), position.getZ());
    }

    public BoundingBox union(Location position) {
        check(position != null, "Position is null!");
        return this.union(position.getX(), position.getY(), position.getZ());
    }

    public BoundingBox union(BoundingBox other) {
        check(other != null, "Other bounding box is null!");
        if (this.contains(other)) return this;
        return this.resize(Math.min(this.minX, other.minX), Math.min(this.minY, other.minY), Math.min(this.minZ, other.minZ),
                Math.max(this.maxX, other.maxX), Math.max(this.maxY, other.maxY), Math.max(this.maxZ, other.maxZ));
    }

    /** 겹치지 않으면 IllegalArgumentException("The bounding boxes do not overlap!"). */
    public BoundingBox intersection(BoundingBox other) {
        check(other != null, "Other bounding box is null!");
        check(this.overlaps(other), "The bounding boxes do not overlap!");
        return this.resize(Math.max(this.minX, other.minX), Math.max(this.minY, other.minY), Math.max(this.minZ, other.minZ),
                Math.min(this.maxX, other.maxX), Math.min(this.maxY, other.maxY), Math.min(this.maxZ, other.maxZ));
    }

    public BoundingBox shift(double shiftX, double shiftY, double shiftZ) {
        if (shiftX == 0.0D && shiftY == 0.0D && shiftZ == 0.0D) return this;
        return this.resize(this.minX + shiftX, this.minY + shiftY, this.minZ + shiftZ, this.maxX + shiftX, this.maxY + shiftY, this.maxZ + shiftZ);
    }

    public BoundingBox shift(Vector shift) {
        check(shift != null, "Shift is null!");
        return this.shift(shift.getX(), shift.getY(), shift.getZ());
    }

    public BoundingBox shift(Location shift) {
        check(shift != null, "Shift is null!");
        return this.shift(shift.getX(), shift.getY(), shift.getZ());
    }

    private boolean overlaps(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return this.minX < maxX && this.maxX > minX && this.minY < maxY && this.maxY > minY && this.minZ < maxZ && this.maxZ > minZ;
    }

    /** 맞닿기만 한 것은 겹침이 아니다(엄격한 부등호). */
    public boolean overlaps(BoundingBox other) {
        check(other != null, "Other bounding box is null!");
        return this.overlaps(other.minX, other.minY, other.minZ, other.maxX, other.maxY, other.maxZ);
    }

    public boolean overlaps(Vector min, Vector max) {
        check(min != null, "Min is null!");
        check(max != null, "Max is null!");
        double x1 = min.getX(), y1 = min.getY(), z1 = min.getZ();
        double x2 = max.getX(), y2 = max.getY(), z2 = max.getZ();
        return this.overlaps(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    /** 최솟값은 포함, 최댓값은 제외(반열린 구간). */
    public boolean contains(double x, double y, double z) {
        return x >= this.minX && x < this.maxX && y >= this.minY && y < this.maxY && z >= this.minZ && z < this.maxZ;
    }

    public boolean contains(Vector position) {
        check(position != null, "Position is null!");
        return this.contains(position.getX(), position.getY(), position.getZ());
    }

    private boolean contains(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return this.minX <= minX && this.maxX >= maxX && this.minY <= minY && this.maxY >= maxY && this.minZ <= minZ && this.maxZ >= maxZ;
    }

    public boolean contains(BoundingBox other) {
        check(other != null, "Other bounding box is null!");
        return this.contains(other.minX, other.minY, other.minZ, other.maxX, other.maxY, other.maxZ);
    }

    public boolean contains(Vector min, Vector max) {
        check(min != null, "Min is null!");
        check(max != null, "Max is null!");
        double x1 = min.getX(), y1 = min.getY(), z1 = min.getZ();
        double x2 = max.getX(), y2 = max.getY(), z2 = max.getZ();
        return this.contains(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    /** 광선(start, direction)이 이 상자를 maxDistance 안에서 맞추는 첫 지점. 안 맞추면 null. start 가 상자 안이면 나가는 지점을 돌려준다. */
    public RayTraceResult rayTrace(Vector start, Vector direction, double maxDistance) {
        check(start != null, "Start is null!");
        start.checkFinite();
        check(direction != null, "Direction is null!");
        direction.checkFinite();
        check(direction.lengthSquared() > 0, "Direction's magnitude is 0!");
        if (maxDistance < 0.0D) return null;

        double startX = start.getX(), startY = start.getY(), startZ = start.getZ();
        Vector dir = direction.clone().normalizeZeros().normalize();
        double dirX = dir.getX(), dirY = dir.getY(), dirZ = dir.getZ();
        // 성분이 0 이면 나눗셈이 무한대가 되지만 아래 비교에서 문제가 없다
        double divX = 1.0D / dirX;
        double divY = 1.0D / dirY;
        double divZ = 1.0D / dirZ;

        double tMin;
        double tMax;
        BlockFace hitBlockFaceMin;
        BlockFace hitBlockFaceMax;
        if (dirX >= 0.0D) {
            tMin = (this.minX - startX) * divX;
            tMax = (this.maxX - startX) * divX;
            hitBlockFaceMin = BlockFace.WEST;
            hitBlockFaceMax = BlockFace.EAST;
        } else {
            tMin = (this.maxX - startX) * divX;
            tMax = (this.minX - startX) * divX;
            hitBlockFaceMin = BlockFace.EAST;
            hitBlockFaceMax = BlockFace.WEST;
        }

        double tyMin;
        double tyMax;
        BlockFace hitBlockFaceYMin;
        BlockFace hitBlockFaceYMax;
        if (dirY >= 0.0D) {
            tyMin = (this.minY - startY) * divY;
            tyMax = (this.maxY - startY) * divY;
            hitBlockFaceYMin = BlockFace.DOWN;
            hitBlockFaceYMax = BlockFace.UP;
        } else {
            tyMin = (this.maxY - startY) * divY;
            tyMax = (this.minY - startY) * divY;
            hitBlockFaceYMin = BlockFace.UP;
            hitBlockFaceYMax = BlockFace.DOWN;
        }
        if ((tMin > tyMax) || (tMax < tyMin)) return null;
        if (tyMin > tMin) {
            tMin = tyMin;
            hitBlockFaceMin = hitBlockFaceYMin;
        }
        if (tyMax < tMax) {
            tMax = tyMax;
            hitBlockFaceMax = hitBlockFaceYMax;
        }

        double tzMin;
        double tzMax;
        BlockFace hitBlockFaceZMin;
        BlockFace hitBlockFaceZMax;
        if (dirZ >= 0.0D) {
            tzMin = (this.minZ - startZ) * divZ;
            tzMax = (this.maxZ - startZ) * divZ;
            hitBlockFaceZMin = BlockFace.NORTH;
            hitBlockFaceZMax = BlockFace.SOUTH;
        } else {
            tzMin = (this.maxZ - startZ) * divZ;
            tzMax = (this.minZ - startZ) * divZ;
            hitBlockFaceZMin = BlockFace.SOUTH;
            hitBlockFaceZMax = BlockFace.NORTH;
        }
        if ((tMin > tzMax) || (tMax < tzMin)) return null;
        if (tzMin > tMin) {
            tMin = tzMin;
            hitBlockFaceMin = hitBlockFaceZMin;
        }
        if (tzMax < tMax) {
            tMax = tzMax;
            hitBlockFaceMax = hitBlockFaceZMax;
        }

        if (tMax < 0.0D) return null;          // 교차가 시작점 뒤
        if (tMin > maxDistance) return null;    // 너무 멀다

        double t;
        BlockFace hitBlockFace;
        if (tMin < 0.0D) {
            t = tMax;
            hitBlockFace = hitBlockFaceMax;
        } else {
            t = tMin;
            hitBlockFace = hitBlockFaceMin;
        }
        Vector hitPosition = dir.multiply(t).add(start);
        return new RayTraceResult(hitPosition, hitBlockFace);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        long temp;
        temp = Double.doubleToLongBits(maxX);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(maxY);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(maxZ);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(minX);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(minY);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(minZ);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BoundingBox other)) return false;
        return Double.doubleToLongBits(maxX) == Double.doubleToLongBits(other.maxX) && Double.doubleToLongBits(maxY) == Double.doubleToLongBits(other.maxY)
                && Double.doubleToLongBits(maxZ) == Double.doubleToLongBits(other.maxZ) && Double.doubleToLongBits(minX) == Double.doubleToLongBits(other.minX)
                && Double.doubleToLongBits(minY) == Double.doubleToLongBits(other.minY) && Double.doubleToLongBits(minZ) == Double.doubleToLongBits(other.minZ);
    }

    @Override
    public String toString() {
        return "BoundingBox [minX=" + minX + ", minY=" + minY + ", minZ=" + minZ + ", maxX=" + maxX + ", maxY=" + maxY + ", maxZ=" + maxZ + "]";
    }

    @Override
    public BoundingBox clone() {
        try {
            return (BoundingBox) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("minX", minX);
        result.put("minY", minY);
        result.put("minZ", minZ);
        result.put("maxX", maxX);
        result.put("maxY", maxY);
        result.put("maxZ", maxZ);
        return result;
    }

    /** 빠진 키는 0. 값이 숫자가 아니면 ClassCastException(Paper 와 같다 — 되읽기 실패 로그). */
    public static BoundingBox deserialize(Map<String, Object> args) {
        double minX = 0.0D, minY = 0.0D, minZ = 0.0D, maxX = 0.0D, maxY = 0.0D, maxZ = 0.0D;
        if (args.containsKey("minX")) minX = ((Number) args.get("minX")).doubleValue();
        if (args.containsKey("minY")) minY = ((Number) args.get("minY")).doubleValue();
        if (args.containsKey("minZ")) minZ = ((Number) args.get("minZ")).doubleValue();
        if (args.containsKey("maxX")) maxX = ((Number) args.get("maxX")).doubleValue();
        if (args.containsKey("maxY")) maxY = ((Number) args.get("maxY")).doubleValue();
        if (args.containsKey("maxZ")) maxZ = ((Number) args.get("maxZ")).doubleValue();
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
