package org.bukkit.block.banner;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.bukkit.DyeColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

/** Paper 1.21.1 Pattern: 현수막 무늬 한 겹(색 + 무늬 종류). 바꿀 수 없는 값. 설정 파일에는 {color: RED, pattern: minecraft:stripe_bottom} 로 적힌다. */
@SerializableAs("Pattern")
public class Pattern implements ConfigurationSerializable {

    private static final String COLOR = "color";
    private static final String PATTERN = "pattern";

    private final DyeColor color;
    private final PatternType pattern;

    public Pattern(DyeColor color, PatternType pattern) {
        this.color = color;
        this.pattern = pattern;
    }

    /** 설정에서 되읽기: 색은 열거형 이름(옛 SILVER 도), 무늬는 옛 짧은 이름(bs) 또는 키(minecraft:stripe_bottom). */
    public Pattern(Map<String, Object> map) {
        color = DyeColor.legacyValueOf(getString(map, COLOR));
        String value = getString(map, PATTERN);
        @SuppressWarnings("removal")
        PatternType type = PatternType.getByIdentifier(value);
        if (type == null) {
            NamespacedKey key = NamespacedKey.fromString(value);
            type = key == null ? null : Registry.BANNER_PATTERN.get(key);
        }
        if (type == null) throw new NullPointerException("Pattern type for key " + value + " cannot be null");
        pattern = type;
    }

    private static String getString(Map<?, ?> map, Object key) {
        if (map.get(key) instanceof String s) return s;
        throw new NoSuchElementException(map + " does not contain " + key);
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(COLOR, color.toString());
        m.put(PATTERN, pattern.getKey().toString());
        return m;
    }

    public DyeColor getColor() { return color; }

    public PatternType getPattern() { return pattern; }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + (color != null ? color.hashCode() : 0);
        hash = 97 * hash + (pattern != null ? pattern.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass()) return false;
        Pattern other = (Pattern) obj;
        return color == other.color && pattern == other.pattern;
    }
}
