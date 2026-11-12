package org.bukkit.block.spawner;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

/** 스포너 조건(블록 빛·하늘 빛 범위, 0 이상). 설정에는 별칭 {@code SpawnRule} 로 저장된다(A15-01). 인자가 틀리면 IllegalArgumentException(문구는 Paper 와 같다). */
@SerializableAs("SpawnRule")
public class SpawnRule implements Cloneable, ConfigurationSerializable {

    private int minBlockLight;
    private int maxBlockLight;
    private int minSkyLight;
    private int maxSkyLight;

    public SpawnRule(int minBlockLight, int maxBlockLight, int minSkyLight, int maxSkyLight) {
        check(minBlockLight <= maxBlockLight, "minBlockLight must be <= maxBlockLight (" + minBlockLight + " <= " + maxBlockLight + ")");
        check(minSkyLight <= maxSkyLight, "minSkyLight must be <= maxSkyLight (" + minSkyLight + " <= " + maxSkyLight + ")");
        check(minBlockLight >= 0, "minBlockLight must be >= 0 (given " + minBlockLight + ")");
        check(maxBlockLight >= 0, "maxBlockLight must be >= 0 (given " + maxBlockLight + ")");
        check(minSkyLight >= 0, "minSkyLight must be >= 0 (given " + minSkyLight + ")");
        check(maxSkyLight >= 0, "maxSkyLight must be >= 0 (given " + maxSkyLight + ")");
        this.minBlockLight = minBlockLight;
        this.maxBlockLight = maxBlockLight;
        this.minSkyLight = minSkyLight;
        this.maxSkyLight = maxSkyLight;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException(message);
    }

    public int getMinBlockLight() { return minBlockLight; }

    public void setMinBlockLight(int minBlockLight) {
        check(minBlockLight >= 0, "minBlockLight must be >= 0 (given " + minBlockLight + ")");
        check(minBlockLight <= maxBlockLight, "minBlockLight must be <= maxBlockLight (" + minBlockLight + " <= " + maxBlockLight + ")");
        this.minBlockLight = minBlockLight;
    }

    public int getMaxBlockLight() { return maxBlockLight; }

    /** Paper 처럼 0 이상만 보고 최솟값과의 순서는 보지 않는다. */
    public void setMaxBlockLight(int maxBlockLight) {
        check(maxBlockLight >= 0, "maxBlockLight must be >= 0 (given " + maxBlockLight + ")");
        this.maxBlockLight = maxBlockLight;
    }

    public int getMinSkyLight() { return minSkyLight; }

    public void setMinSkyLight(int minSkyLight) {
        check(minSkyLight >= 0, "minSkyLight must be >= 0 (given " + minSkyLight + ")");
        check(minSkyLight <= maxSkyLight, "minSkyLight must be <= maxSkyLight (" + minSkyLight + " <= " + maxSkyLight + ")");
        this.minSkyLight = minSkyLight;
    }

    public int getMaxSkyLight() { return maxSkyLight; }

    public void setMaxSkyLight(int maxSkyLight) {
        check(maxSkyLight >= 0, "maxSkyLight must be >= 0 (given " + maxSkyLight + ")");
        this.maxSkyLight = maxSkyLight;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SpawnRule other && minBlockLight == other.minBlockLight && maxBlockLight == other.maxBlockLight
                && minSkyLight == other.minSkyLight && maxSkyLight == other.maxSkyLight;
    }

    @Override
    public int hashCode() {
        int hash = minBlockLight;
        hash = (hash << 4) + maxBlockLight;
        hash = (hash << 4) + minSkyLight;
        hash = (hash << 4) + maxSkyLight;
        return hash;
    }

    @Override
    public SpawnRule clone() {
        try {
            return (SpawnRule) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> block = new LinkedHashMap<>();
        Map<String, Object> sky = new LinkedHashMap<>();
        block.put("min", getMinBlockLight());
        block.put("max", getMaxBlockLight());
        sky.put("min", getMinSkyLight());
        sky.put("max", getMaxSkyLight());
        result.put("block-light", block);
        result.put("sky-light", sky);
        return result;
    }

    public static SpawnRule deserialize(Map<String, Object> args) {
        int minBlock = 0, maxBlock = 0, minSky = 0, maxSky = 0;
        Object block = args.get("block-light");
        Object sky = args.get("sky-light");
        // Paper 는 (int) 로 풀어서 정수가 아닌 값이면 ClassCastException(되읽기 실패 로그) — 같게 둔다
        if (block instanceof Map<?, ?> blockMap) {
            if (blockMap.containsKey("min")) minBlock = (int) blockMap.get("min");
            if (blockMap.containsKey("max")) maxBlock = (int) blockMap.get("max");
        }
        if (sky instanceof Map<?, ?> skyMap) {
            if (skyMap.containsKey("min")) minSky = (int) skyMap.get("min");
            if (skyMap.containsKey("max")) maxSky = (int) skyMap.get("max");
        }
        return new SpawnRule(minBlock, maxBlock, minSky, maxSky);
    }
}
