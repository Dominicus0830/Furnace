// 보낼 입자 하나의 종류와 옵션(바닐라 ParticleOptions). 종류마다 옵션 모양이 정해져 있다 — 맞지 않으면 IllegalArgumentException.
package org.domi.furnace.api.world;

import java.util.Objects;
import java.util.Set;
import org.domi.furnace.api.item.ItemStack;

/**
 * 입자 종류(minecraft:flame)와 그 종류가 받는 옵션. 옵션이 없는 종류는 {@link #of(String)}, 나머지는 각 record 로 만든다.
 * 키는 1.21.1 particle_type 레지스트리 이름이다(모르는 이름은 보낼 때 IllegalArgumentException).
 */
public sealed interface ParticleEffect {

    /** "minecraft:..." 입자 종류 키. */
    String type();

    /** 블록 상태를 받는 종류(BlockParticleOption). */
    Set<String> BLOCK_TYPES = Set.of("minecraft:block", "minecraft:block_marker", "minecraft:falling_dust", "minecraft:dust_pillar");

    /** 옵션을 받는 종류 전부(이 밖의 종류는 옵션이 없다). */
    Set<String> WITH_OPTIONS = Set.of("minecraft:block", "minecraft:block_marker", "minecraft:falling_dust", "minecraft:dust_pillar",
            "minecraft:dust", "minecraft:dust_color_transition", "minecraft:entity_effect", "minecraft:item", "minecraft:vibration",
            "minecraft:sculk_charge", "minecraft:shriek");

    private static String key(String type) {
        Objects.requireNonNull(type, "type");
        return type.indexOf(':') < 0 ? "minecraft:" + type : type;
    }

    /** 옵션이 없는 종류(flame·smoke·heart …). 옵션이 필요한 종류면 IllegalArgumentException. */
    static ParticleEffect of(String type) { return new Simple(type); }

    record Simple(String type) implements ParticleEffect {
        public Simple {
            type = key(type);
            if (WITH_OPTIONS.contains(type)) throw new IllegalArgumentException(type + " 는 옵션이 필요하다");
        }
    }

    /** block·block_marker·falling_dust·dust_pillar — 블록 상태. */
    record BlockState(String type, BlockData block) implements ParticleEffect {
        public BlockState {
            type = key(type);
            Objects.requireNonNull(block, "block");
            if (!BLOCK_TYPES.contains(type)) throw new IllegalArgumentException(type + " 는 블록 상태를 받지 않는다");
        }
    }

    /** dust — 색(0xRRGGBB)과 크기(바닐라처럼 0.01~4 로 죈다). */
    record Dust(int rgb, float size) implements ParticleEffect {
        public Dust { rgb &= 0xFFFFFF; }
        @Override public String type() { return "minecraft:dust"; }
    }

    /** dust_color_transition — 시작색·끝색(0xRRGGBB)과 크기. */
    record DustTransition(int fromRgb, int toRgb, float size) implements ParticleEffect {
        public DustTransition { fromRgb &= 0xFFFFFF; toRgb &= 0xFFFFFF; }
        @Override public String type() { return "minecraft:dust_color_transition"; }
    }

    /** entity_effect — 알파 포함 색(0xAARRGGBB). */
    record ColorEffect(int argb) implements ParticleEffect {
        @Override public String type() { return "minecraft:entity_effect"; }
    }

    /** item — 아이템(빈 칸이면 IllegalArgumentException, 바닐라와 같다). */
    record Item(ItemStack item) implements ParticleEffect {
        public Item {
            Objects.requireNonNull(item, "item");
            if (item.isEmpty()) throw new IllegalArgumentException("빈 아이템으로는 item 입자를 만들 수 없다");
        }
        @Override public String type() { return "minecraft:item"; }
    }

    /** sculk_charge — 회전(라디안). */
    record SculkCharge(float roll) implements ParticleEffect {
        @Override public String type() { return "minecraft:sculk_charge"; }
    }

    /** shriek — 나타나기까지 지연 틱. */
    record Shriek(int delay) implements ParticleEffect {
        @Override public String type() { return "minecraft:shriek"; }
    }

    /**
     * vibration — 진동이 날아갈 곳과 도착까지 틱. 곳은 블록 칸이나 엔티티(엔티티 번호와 눈높이 보정).
     * 출발점은 입자를 내는 좌표다.
     */
    record Vibration(BlockPos block, int entityId, float entityYOffset, int arrivalTicks) implements ParticleEffect {
        public static Vibration toBlock(BlockPos block, int arrivalTicks) { return new Vibration(Objects.requireNonNull(block, "block"), -1, 0.0F, arrivalTicks); }
        public static Vibration toEntity(int entityId, float yOffset, int arrivalTicks) { return new Vibration(null, entityId, yOffset, arrivalTicks); }
        @Override public String type() { return "minecraft:vibration"; }
    }
}
