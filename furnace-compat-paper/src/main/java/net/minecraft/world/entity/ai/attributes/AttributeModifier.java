package net.minecraft.world.entity.ai.attributes;

import java.util.function.IntFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/**
 * 속성 수정자(Mojang 이름 shim, easy-nms E4c-2). 바닐라 1.21.1 과 같은 record 다(이름 id, 양, 연산).
 * NBT 저장(save·load)·코덱 필드는 DataFixerUpper·네트워크 코덱 shim 이 없어 두지 않았다.
 */
public record AttributeModifier(ResourceLocation id, double amount, Operation operation) {

    public boolean is(ResourceLocation other) { return other.equals(id); }

    /** 연산(바닐라 순서·번호·직렬화 이름). */
    public enum Operation implements StringRepresentable {
        ADD_VALUE("add_value", 0),
        ADD_MULTIPLIED_BASE("add_multiplied_base", 1),
        ADD_MULTIPLIED_TOTAL("add_multiplied_total", 2);

        /** 번호 → 연산. 바닐라 ByIdMap.continuous(ZERO) 처럼 범위 밖 번호는 첫 상수다. */
        public static final IntFunction<Operation> BY_ID = i -> i >= 0 && i < values().length ? values()[i] : values()[0];

        private final String name;
        private final int id;

        Operation(String name, int id) {
            this.name = name;
            this.id = id;
        }

        public int id() { return id; }

        @Override
        public String getSerializedName() { return name; }
    }
}
