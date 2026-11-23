package net.minecraft.world.entity;

/**
 * 바닐라 EquipmentSlot(장비 칸). 상수(바닐라 순서)는 명세가 정하고 생성기가 머리 뒤에 쓴다. 칸마다의 값(종류·번호·필터 번호·이름)은
 * Paper 1.21.1 정적 초기화와 같다: 손 칸 (HAND, 0·1, 필터 0·5), 갑옷 칸 FEET~HEAD (HUMANOID_ARMOR, 0~3, 필터 1~4), BODY (ANIMAL_ARMOR, 0, 필터 6).
 */
enum EquipmentSlot {

    /** 바닐라 getType. */
    public Type getType() {
        return switch (this) {
            case MAINHAND, OFFHAND -> Type.HAND;
            case FEET, LEGS, CHEST, HEAD -> Type.HUMANOID_ARMOR;
            case BODY -> Type.ANIMAL_ARMOR;
        };
    }

    /** 바닐라 getIndex: 같은 종류 안의 번호. */
    public int getIndex() {
        return switch (this) {
            case MAINHAND, FEET, BODY -> 0;
            case OFFHAND, LEGS -> 1;
            case CHEST -> 2;
            case HEAD -> 3;
        };
    }

    /** 바닐라 getIndex(base): base + 번호. */
    public int getIndex(int base) { return base + getIndex(); }

    /** 바닐라 getFilterFlag. */
    public int getFilterFlag() {
        return switch (this) {
            case MAINHAND -> 0;
            case FEET -> 1;
            case LEGS -> 2;
            case CHEST -> 3;
            case HEAD -> 4;
            case OFFHAND -> 5;
            case BODY -> 6;
        };
    }

    /** 바닐라 getName: "mainhand"·"offhand"·"feet"·"legs"·"chest"·"head"·"body". */
    public String getName() { return name().toLowerCase(java.util.Locale.ROOT); }

    /** 바닐라 getSerializedName: getName 과 같다. */
    public String getSerializedName() { return getName(); }

    /** 바닐라 isArmor: 사람 갑옷 칸 또는 동물 갑옷 칸. */
    public boolean isArmor() { return getType() == Type.HUMANOID_ARMOR || getType() == Type.ANIMAL_ARMOR; }

    /** 바닐라 byName: 이름으로 찾고, 없으면 IllegalArgumentException("Invalid slot '이름'"). */
    public static EquipmentSlot byName(String name) {
        for (EquipmentSlot s : values()) if (s.getName().equals(name)) return s;
        throw new IllegalArgumentException("Invalid slot '" + name + "'");
    }

    /** 바닐라 EquipmentSlot.Type. */
    public enum Type {
        HAND,
        HUMANOID_ARMOR,
        ANIMAL_ARMOR
    }
}
