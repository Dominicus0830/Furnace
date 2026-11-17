package org.bukkit.util;

import java.util.Map;
import org.bukkit.configuration.serialization.SerializableAs;

/** 블록 칸 좌표로 같음을 가르는 벡터(같음·해시가 정수 부분만 본다). 설정에는 별칭 {@code BlockVector} 로 저장된다(A15-01). */
@SerializableAs("BlockVector")
public class BlockVector extends Vector {

    public BlockVector() {
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }

    public BlockVector(Vector vec) {
        this.x = vec.getX();
        this.y = vec.getY();
        this.z = vec.getZ();
    }

    public BlockVector(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public BlockVector(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public BlockVector(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BlockVector other && (int) other.getX() == (int) this.x && (int) other.getY() == (int) this.y && (int) other.getZ() == (int) this.z;
    }

    @Override
    public int hashCode() { return (Integer.valueOf((int) x).hashCode() >> 13) ^ (Integer.valueOf((int) y).hashCode() >> 7) ^ Integer.valueOf((int) z).hashCode(); }

    @Override
    public BlockVector clone() { return (BlockVector) super.clone(); }

    /** 빠진 축은 0. */
    public static BlockVector deserialize(Map<String, Object> args) {
        return new BlockVector(args.containsKey("x") ? NumberConversions.toDouble(args.get("x")) : 0,
                args.containsKey("y") ? NumberConversions.toDouble(args.get("y")) : 0,
                args.containsKey("z") ? NumberConversions.toDouble(args.get("z")) : 0);
    }
}
