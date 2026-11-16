package org.bukkit.block.banner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.util.OldEnum;

/**
 * Paper 1.21.1 PatternType: 현수막 무늬 종류 — minecraft:banner_pattern 레지스트리 값({@link Registry#BANNER_PATTERN}).
 * 상수는 레지스트리에서 꺼낸 같은 객체라 {@code ==} 로 견줘도 된다. 데이터 팩이 더한 무늬도 레지스트리로 얻는다.
 */
public interface PatternType extends OldEnum<PatternType>, Keyed {

    PatternType BASE = getType("base");
    PatternType SQUARE_BOTTOM_LEFT = getType("square_bottom_left");
    PatternType SQUARE_BOTTOM_RIGHT = getType("square_bottom_right");
    PatternType SQUARE_TOP_LEFT = getType("square_top_left");
    PatternType SQUARE_TOP_RIGHT = getType("square_top_right");
    PatternType STRIPE_BOTTOM = getType("stripe_bottom");
    PatternType STRIPE_TOP = getType("stripe_top");
    PatternType STRIPE_LEFT = getType("stripe_left");
    PatternType STRIPE_RIGHT = getType("stripe_right");
    PatternType STRIPE_CENTER = getType("stripe_center");
    PatternType STRIPE_MIDDLE = getType("stripe_middle");
    PatternType STRIPE_DOWNRIGHT = getType("stripe_downright");
    PatternType STRIPE_DOWNLEFT = getType("stripe_downleft");
    PatternType SMALL_STRIPES = getType("small_stripes");
    PatternType CROSS = getType("cross");
    PatternType STRAIGHT_CROSS = getType("straight_cross");
    PatternType TRIANGLE_BOTTOM = getType("triangle_bottom");
    PatternType TRIANGLE_TOP = getType("triangle_top");
    PatternType TRIANGLES_BOTTOM = getType("triangles_bottom");
    PatternType TRIANGLES_TOP = getType("triangles_top");
    PatternType DIAGONAL_LEFT = getType("diagonal_left");
    PatternType DIAGONAL_UP_RIGHT = getType("diagonal_up_right");
    PatternType DIAGONAL_UP_LEFT = getType("diagonal_up_left");
    PatternType DIAGONAL_RIGHT = getType("diagonal_right");
    PatternType CIRCLE = getType("circle");
    PatternType RHOMBUS = getType("rhombus");
    PatternType HALF_VERTICAL = getType("half_vertical");
    PatternType HALF_HORIZONTAL = getType("half_horizontal");
    PatternType HALF_VERTICAL_RIGHT = getType("half_vertical_right");
    PatternType HALF_HORIZONTAL_BOTTOM = getType("half_horizontal_bottom");
    PatternType BORDER = getType("border");
    PatternType CURLY_BORDER = getType("curly_border");
    PatternType CREEPER = getType("creeper");
    PatternType GRADIENT = getType("gradient");
    PatternType GRADIENT_UP = getType("gradient_up");
    PatternType BRICKS = getType("bricks");
    PatternType SKULL = getType("skull");
    PatternType FLOWER = getType("flower");
    PatternType MOJANG = getType("mojang");
    PatternType GLOBE = getType("globe");
    PatternType PIGLIN = getType("piglin");
    PatternType FLOW = getType("flow");
    PatternType GUSTER = getType("guster");

    @Override
    NamespacedKey getKey();

    /** 1.20.4 까지 NBT 에 쓰던 짧은 이름(예: bs). 짧은 이름이 없는 무늬는 키 전체. */
    @Deprecated(since = "1.20.4", forRemoval = true)
    String getIdentifier();

    /** 짧은 이름으로 찾는다(없으면 null). */
    @Deprecated(since = "1.20.4", forRemoval = true)
    static PatternType getByIdentifier(String identifier) {
        if (identifier == null) return null;
        for (PatternType type : Registry.BANNER_PATTERN) {
            if (identifier.equals(type.getIdentifier())) return type;
        }
        return null;
    }

    private static PatternType getType(String key) {
        return Registry.BANNER_PATTERN.getOrThrow(NamespacedKey.minecraft(key));
    }

    /** 키(대소문자 무시, 이름공간 없으면 minecraft)로 찾는다. 없으면 IllegalArgumentException. */
    @Deprecated(since = "1.21", forRemoval = true)
    static PatternType valueOf(String name) {
        PatternType type = Registry.BANNER_PATTERN.get(NamespacedKey.fromString(name.toLowerCase(Locale.ROOT)));
        if (type == null) throw new IllegalArgumentException("No pattern type found with the name " + name);
        return type;
    }

    /** 레지스트리의 모든 무늬(레지스트리 순서). */
    @Deprecated(since = "1.21", forRemoval = true)
    static PatternType[] values() {
        List<PatternType> all = new ArrayList<>();
        for (PatternType t : Registry.BANNER_PATTERN) all.add(t);
        return all.toArray(new PatternType[0]);
    }
}
