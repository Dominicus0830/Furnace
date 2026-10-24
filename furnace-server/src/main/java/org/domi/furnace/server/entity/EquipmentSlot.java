// 바닐라 EquipmentSlot: 번호는 set_equipment 패킷의 칸 번호(순서 그대로). 종류: 손·방어구·몸(동물 갑옷).
package org.domi.furnace.server.entity;

public enum EquipmentSlot {
    MAINHAND(Type.HAND, 0, "mainhand"), OFFHAND(Type.HAND, 1, "offhand"), FEET(Type.HUMANOID_ARMOR, 0, "feet"), LEGS(Type.HUMANOID_ARMOR, 1, "legs"),
    CHEST(Type.HUMANOID_ARMOR, 2, "chest"), HEAD(Type.HUMANOID_ARMOR, 3, "head"), BODY(Type.ANIMAL_ARMOR, 0, "body");

    public enum Type { HAND, HUMANOID_ARMOR, ANIMAL_ARMOR }

    private final Type type;
    private final int index;
    private final String slotName;

    EquipmentSlot(Type type, int index, String name) {
        this.type = type;
        this.index = index;
        this.slotName = name;
    }

    public Type type() { return type; }

    /** 그 종류 안의 번호(손 0·1, 방어구 발·다리·가슴·머리 0..3). */
    public int index() { return index; }

    public String slotName() { return slotName; }
}
