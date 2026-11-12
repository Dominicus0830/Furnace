// 이 파일은 tools/compat/gen-bukkit-values.py 가 만든다 — 손으로 고치지 않는다.
// painting_variant 1.21.1 의 50 종. 순서·번호는 1.21.1 Bukkit 과 같다. 크기는 데이터 값.
package org.bukkit;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum Art implements Keyed {
    KEBAB(0, "kebab", 1, 1),
    AZTEC(1, "aztec", 1, 1),
    ALBAN(2, "alban", 1, 1),
    AZTEC2(3, "aztec2", 1, 1),
    BOMB(4, "bomb", 1, 1),
    PLANT(5, "plant", 1, 1),
    WASTELAND(6, "wasteland", 1, 1),
    POOL(7, "pool", 2, 1),
    COURBET(8, "courbet", 2, 1),
    SEA(9, "sea", 2, 1),
    SUNSET(10, "sunset", 2, 1),
    CREEBET(11, "creebet", 2, 1),
    WANDERER(12, "wanderer", 1, 2),
    GRAHAM(13, "graham", 1, 2),
    MATCH(14, "match", 2, 2),
    BUST(15, "bust", 2, 2),
    STAGE(16, "stage", 2, 2),
    VOID(17, "void", 2, 2),
    SKULL_AND_ROSES(18, "skull_and_roses", 2, 2),
    WITHER(19, "wither", 2, 2),
    FIGHTERS(20, "fighters", 4, 2),
    POINTER(21, "pointer", 4, 4),
    PIGSCENE(22, "pigscene", 4, 4),
    BURNING_SKULL(23, "burning_skull", 4, 4),
    SKELETON(24, "skeleton", 4, 3),
    DONKEY_KONG(25, "donkey_kong", 4, 3),
    EARTH(26, "earth", 2, 2),
    WIND(27, "wind", 2, 2),
    WATER(28, "water", 2, 2),
    FIRE(29, "fire", 2, 2),
    BAROQUE(30, "baroque", 2, 2),
    HUMBLE(31, "humble", 2, 2),
    MEDITATIVE(32, "meditative", 1, 1),
    PRAIRIE_RIDE(33, "prairie_ride", 1, 2),
    UNPACKED(34, "unpacked", 4, 4),
    BACKYARD(35, "backyard", 3, 4),
    BOUQUET(36, "bouquet", 3, 3),
    CAVEBIRD(37, "cavebird", 3, 3),
    CHANGING(38, "changing", 4, 2),
    COTAN(39, "cotan", 3, 3),
    ENDBOSS(40, "endboss", 3, 3),
    FERN(41, "fern", 3, 3),
    FINDING(42, "finding", 4, 2),
    LOWMIST(43, "lowmist", 4, 2),
    ORB(44, "orb", 4, 4),
    OWLEMONS(45, "owlemons", 3, 3),
    PASSAGE(46, "passage", 4, 2),
    POND(47, "pond", 3, 4),
    SUNFLOWERS(48, "sunflowers", 3, 3),
    TIDES(49, "tides", 3, 3);

    private static final Map<Integer, Art> BY_ID = new HashMap<>();
    private static final Map<String, Art> BY_NAME = new HashMap<>();

    static {
        for (Art a : values()) {
            BY_ID.put(a.id, a);
            BY_NAME.put(a.key.getKey(), a);
            BY_NAME.put(a.name().toLowerCase(Locale.ROOT).replace("_", ""), a);
        }
    }

    private final int id;
    private final NamespacedKey key;
    private final int width;
    private final int height;

    Art(int id, String key, int width, int height) {
        this.id = id;
        this.key = NamespacedKey.minecraft(key);
        this.width = width;
        this.height = height;
    }

    /** 가로 블록 수. */
    public int getBlockWidth() { return width; }

    /** 세로 블록 수. */
    public int getBlockHeight() { return height; }

    /** @deprecated 옛 번호. */
    @Deprecated
    public int getId() { return id; }

    @Override
    public NamespacedKey getKey() { return key; }

    /** @deprecated 옛 번호로 찾기(없으면 null). */
    @Deprecated
    public static Art getById(int id) { return BY_ID.get(id); }

    /** 이름으로 찾기(대소문자·밑줄 무시, 없으면 null). */
    public static Art getByName(String name) {
        if (name == null) throw new IllegalArgumentException("Name cannot be null");
        String n = name.toLowerCase(Locale.ROOT);
        Art a = BY_NAME.get(n);
        return a != null ? a : BY_NAME.get(n.replace("_", ""));
    }
}
