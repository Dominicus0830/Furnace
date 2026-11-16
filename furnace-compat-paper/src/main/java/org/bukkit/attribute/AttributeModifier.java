package org.bukkit.attribute;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;

/**
 * 속성 수정치(1.21 Bukkit: 키·양·연산·칸 묶음). 옛 UUID·이름 생성자는 Bukkit 처럼 UUID 문자열을 키(minecraft:<uuid>)로 바꾼다.
 * 엔티티·아이템에 거는 API(AttributeInstance·ItemMeta)는 아직 없다 — 값 타입만이다.
 */
@SerializableAs("AttributeModifier")
public class AttributeModifier implements ConfigurationSerializable, Keyed {

    private final NamespacedKey key;
    private final double amount;
    private final Operation operation;
    private final EquipmentSlotGroup slot;

    @Deprecated
    public AttributeModifier(String name, double amount, Operation operation) { this(UUID.randomUUID(), name, amount, operation); }

    @Deprecated
    public AttributeModifier(UUID uuid, String name, double amount, Operation operation) { this(uuid, name, amount, operation, (EquipmentSlot) null); }

    @Deprecated
    public AttributeModifier(UUID uuid, String name, double amount, Operation operation, EquipmentSlot slot) {
        this(uuid, name, amount, operation, slot == null ? EquipmentSlotGroup.ANY : group(slot));
    }

    @Deprecated
    public AttributeModifier(UUID uuid, String name, double amount, Operation operation, EquipmentSlotGroup slot) {
        this(NamespacedKey.fromString(Objects.requireNonNull(uuid, "uuid").toString()), amount, operation, slot);
    }

    /** Paper: 칸 묶음 없이(ANY). */
    public AttributeModifier(NamespacedKey key, double amount, Operation operation) { this(key, amount, operation, EquipmentSlotGroup.ANY); }

    public AttributeModifier(NamespacedKey key, double amount, Operation operation, EquipmentSlotGroup slot) {
        if (key == null) throw new IllegalArgumentException("Key cannot be null");
        if (operation == null) throw new IllegalArgumentException("Operation cannot be null");
        if (slot == null) throw new IllegalArgumentException("EquipmentSlotGroup cannot be null");
        this.key = key;
        this.amount = amount;
        this.operation = operation;
        this.slot = slot;
    }

    private static EquipmentSlotGroup group(EquipmentSlot s) {
        return switch (s) {
            case HAND -> EquipmentSlotGroup.MAINHAND;
            case OFF_HAND -> EquipmentSlotGroup.OFFHAND;
            case FEET -> EquipmentSlotGroup.FEET;
            case LEGS -> EquipmentSlotGroup.LEGS;
            case CHEST -> EquipmentSlotGroup.CHEST;
            case HEAD -> EquipmentSlotGroup.HEAD;
        };
    }

    /** @deprecated minecraft:&lt;UUID 모양&gt; 키면 그 UUID, 아니면 키 문자열의 이름 기반 UUID(Bukkit 1.21.1 과 같다). */
    @Deprecated
    public UUID getUniqueId() {
        if (key.getNamespace().equals(NamespacedKey.MINECRAFT) && UUID_SHAPE.matcher(key.getKey()).matches()) return UUID.fromString(key.getKey());
        return UUID.nameUUIDFromBytes(key.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static final java.util.regex.Pattern UUID_SHAPE =
            java.util.regex.Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    @Override
    public NamespacedKey getKey() { return key; }

    /** @deprecated 키 이름. */
    @Deprecated
    public String getName() { return key.getKey(); }

    public double getAmount() { return amount; }

    public Operation getOperation() { return operation; }

    /** @deprecated 칸 하나(ANY 면 null, 여러 칸 묶음이면 대표 칸). */
    @Deprecated
    public EquipmentSlot getSlot() { return slot == EquipmentSlotGroup.ANY ? null : slot.getExample(); }

    public EquipmentSlotGroup getSlotGroup() { return slot; }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key.toString());
        m.put("operation", operation.ordinal());
        m.put("amount", amount);
        if (slot != EquipmentSlotGroup.ANY) m.put("slot", slot.toString());
        return m;
    }

    public static AttributeModifier deserialize(Map<String, Object> args) {
        // Bukkit 1.21.1: uuid 를 먼저 본다. 숫자는 NumberConversions(문자열 숫자도 된다), 모르는 칸 이름은 EquipmentSlot.valueOf 의 예외가 그대로 난다
        NamespacedKey key = args.containsKey("uuid") ? NamespacedKey.fromString((String) args.get("uuid")) : NamespacedKey.fromString((String) args.get("key"));
        EquipmentSlotGroup slot = EquipmentSlotGroup.ANY;
        if (args.containsKey("slot")) {
            String s = args.get("slot").toString();
            EquipmentSlotGroup g = EquipmentSlotGroup.getByName(s.toLowerCase(java.util.Locale.ROOT));
            slot = g != null ? g : group(EquipmentSlot.valueOf(s.toUpperCase(java.util.Locale.ROOT)));
        }
        return new AttributeModifier(key, org.bukkit.util.NumberConversions.toDouble(args.get("amount")),
                Operation.values()[org.bukkit.util.NumberConversions.toInt(args.get("operation"))], slot);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AttributeModifier m && m.key.equals(key) && Double.compare(m.amount, amount) == 0
                && m.operation == operation && m.slot.equals(slot);
    }

    @Override
    public int hashCode() { return Objects.hash(key, amount, operation, slot); }

    @Override
    public String toString() {
        return "AttributeModifier{key=" + key + ", operation=" + operation.name() + ", amount=" + amount + ", slot=" + slot + "}";
    }

    /** 연산(바닐라 AttributeModifier.Operation 순서). */
    public enum Operation {
        /** 기본값에 더한다(add_value). */
        ADD_NUMBER,
        /** 기본값 × 양을 더한다(add_multiplied_base). */
        ADD_SCALAR,
        /** 결과에 (1 + 양)을 곱한다(add_multiplied_total). */
        MULTIPLY_SCALAR_1
    }
}
