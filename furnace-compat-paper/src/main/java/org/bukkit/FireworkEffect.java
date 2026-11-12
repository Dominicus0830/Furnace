package org.bukkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

/** 폭죽 별 하나의 모양(바꿀 수 없는 값). 만들 때는 {@link #builder()}. */
@SerializableAs("Firework")
public final class FireworkEffect implements ConfigurationSerializable {

    /** 폭발 모양(바닐라 FireworkExplosion.Shape 순서). */
    public enum Type { BALL, BALL_LARGE, STAR, BURST, CREEPER }

    public static Builder builder() { return new Builder(); }

    /** 색을 하나 이상 넣어야 build() 가 된다. */
    public static final class Builder {
        private boolean flicker;
        private boolean trail;
        private final List<Color> colors = new ArrayList<>();
        private final List<Color> fadeColors = new ArrayList<>();
        private Type type = Type.BALL;

        Builder() {}

        public Builder with(Type type) {
            if (type == null) throw new IllegalArgumentException("Cannot have null type");
            this.type = type;
            return this;
        }

        public Builder withFlicker() { flicker = true; return this; }

        public Builder flicker(boolean flicker) { this.flicker = flicker; return this; }

        public Builder withTrail() { trail = true; return this; }

        public Builder trail(boolean trail) { this.trail = trail; return this; }

        public Builder withColor(Color color) { colors.add(nonNull(color)); return this; }

        public Builder withColor(Color... colors) { for (Color c : nonNull(colors)) this.colors.add(nonNull(c)); return this; }

        public Builder withColor(Iterable<?> colors) { addAll(this.colors, colors); return this; }

        public Builder withFade(Color color) { fadeColors.add(nonNull(color)); return this; }

        public Builder withFade(Color... colors) { for (Color c : nonNull(colors)) fadeColors.add(nonNull(c)); return this; }

        public Builder withFade(Iterable<?> colors) { addAll(fadeColors, colors); return this; }

        public FireworkEffect build() {
            if (colors.isEmpty()) throw new IllegalStateException("Cannot make FireworkEffect without any color");
            return new FireworkEffect(flicker, trail, List.copyOf(colors), List.copyOf(fadeColors), type);
        }

        private static <T> T nonNull(T v) {
            if (v == null) throw new IllegalArgumentException("Cannot have null color");
            return v;
        }

        private static void addAll(List<Color> to, Iterable<?> from) {
            if (from == null) throw new IllegalArgumentException("Cannot have null colors");
            for (Object o : from) {
                if (!(o instanceof Color c)) throw new IllegalArgumentException(o + " is not a Color in " + from);
                to.add(c);
            }
        }
    }

    private final boolean flicker;
    private final boolean trail;
    private final List<Color> colors;
    private final List<Color> fadeColors;
    private final Type type;

    FireworkEffect(boolean flicker, boolean trail, List<Color> colors, List<Color> fadeColors, Type type) {
        this.flicker = flicker;
        this.trail = trail;
        this.colors = colors;
        this.fadeColors = fadeColors;
        this.type = type;
    }

    public boolean hasFlicker() { return flicker; }

    public boolean hasTrail() { return trail; }

    /** 바꿀 수 없는 목록. */
    public List<Color> getColors() { return colors; }

    public List<Color> getFadeColors() { return fadeColors; }

    public Type getType() { return type; }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("flicker", flicker);
        m.put("trail", trail);
        m.put("colors", colors);
        m.put("fade-colors", fadeColors);
        m.put("type", type.name());
        return m;
    }

    public static ConfigurationSerializable deserialize(Map<String, Object> map) {
        Type type = Type.valueOf((String) map.get("type"));
        return builder().flicker(Boolean.TRUE.equals(map.get("flicker"))).trail(Boolean.TRUE.equals(map.get("trail")))
                .withColor((Iterable<?>) map.getOrDefault("colors", Collections.emptyList()))
                .withFade((Iterable<?>) map.getOrDefault("fade-colors", Collections.emptyList()))
                .with(type).build();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FireworkEffect f && f.flicker == flicker && f.trail == trail && f.type == type
                && f.colors.equals(colors) && f.fadeColors.equals(fadeColors);
    }

    @Override
    public int hashCode() {
        int h = flicker ? 1 : 0;
        h = 31 * h + (trail ? 1 : 0);
        h = 31 * h + colors.hashCode();
        h = 31 * h + fadeColors.hashCode();
        return 31 * h + type.hashCode();
    }

    @Override
    public String toString() { return serialize().toString(); }
}
