// 입자 한 개(종류 + 종류별 옵션) — 바닐라 ParticleOptions 와 같은 뜻. 엔티티 메타데이터(구름 입자·효과 입자)와 입자 패킷에 쓴다.
// 종류 이름은 "minecraft:flame" 꼴. 옵션이 필요한 종류(블록·먼지·아이템 …)는 그 레코드로만 만들 수 있다.
//
// 예) Particle.of("minecraft:flame"), Particle.dust(0xFF0000, 1.5f), Particle.block("minecraft:falling_dust", BlockData.of("minecraft:sand"))
package org.domi.furnace.api.particle;

import java.util.Objects;
import java.util.Set;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;

public sealed interface Particle {

    /** "minecraft:..." 종류 이름. */
    String type();

    /** 블록 상태를 옵션으로 갖는 종류(BlockParticleOption). */
    Set<String> BLOCK_TYPES = Set.of("minecraft:block", "minecraft:block_marker", "minecraft:falling_dust", "minecraft:dust_pillar");

    /** 옵션 없는 종류. 옵션이 필요한 종류 이름이면 IllegalArgumentException. */
    static Particle of(String type) {
        String t = ns(type);
        if (Kind.of(t) != Kind.SIMPLE) throw new IllegalArgumentException(t + " 는 옵션이 필요한 입자다 — Particle." + Kind.of(t).factory + " 로 만든다");
        return new Simple(t);
    }

    static Particle block(String type, BlockData block) { return new Block(type, block); }

    /** 먼지(dust): 색 0xRRGGBB, 크기 0.01~4. */
    static Particle dust(int rgb, float scale) {
        return new Dust(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, scale);
    }

    static Particle dustTransition(int fromRgb, int toRgb, float scale) {
        return new DustTransition(((fromRgb >> 16) & 0xFF) / 255f, ((fromRgb >> 8) & 0xFF) / 255f, (fromRgb & 0xFF) / 255f,
                ((toRgb >> 16) & 0xFF) / 255f, ((toRgb >> 8) & 0xFF) / 255f, (toRgb & 0xFF) / 255f, scale);
    }

    /** 물약 효과 입자(entity_effect): 0xAARRGGBB. */
    static Particle entityEffect(int argb) { return new Color("minecraft:entity_effect", argb); }

    static Particle item(ItemStack item) { return new Item(item); }

    /** 옵션 없는 종류. */
    record Simple(String type) implements Particle {
        public Simple {
            type = ns(type);
            if (Kind.of(type) != Kind.SIMPLE) throw new IllegalArgumentException(type + " 는 옵션이 필요한 입자다");
        }
    }

    /** block·block_marker·falling_dust·dust_pillar. */
    record Block(String type, BlockData block) implements Particle {
        public Block {
            type = ns(type);
            if (!BLOCK_TYPES.contains(type)) throw new IllegalArgumentException(type + " 는 블록 입자가 아니다");
            Objects.requireNonNull(block, "block");
        }
    }

    /** dust: 색(0~1 실수 셋)과 크기(0.01~4 로 죔, 바닐라와 같다). */
    record Dust(float red, float green, float blue, float scale) implements Particle {
        public Dust { scale = clampScale(scale); }

        @Override public String type() { return "minecraft:dust"; }
    }

    /** dust_color_transition: 시작색 → 끝색, 크기(0.01~4). */
    record DustTransition(float fromRed, float fromGreen, float fromBlue, float toRed, float toGreen, float toBlue, float scale) implements Particle {
        public DustTransition { scale = clampScale(scale); }

        @Override public String type() { return "minecraft:dust_color_transition"; }
    }

    /** 색 하나를 갖는 종류(1.21.1 은 entity_effect 뿐): 0xAARRGGBB. */
    record Color(String type, int argb) implements Particle {
        public Color {
            type = ns(type);
            if (Kind.of(type) != Kind.COLOR) throw new IllegalArgumentException(type + " 는 색 입자가 아니다");
        }
    }

    /** item: 비어 있지 않은 아이템(바닐라도 빈 스택은 거부). */
    record Item(ItemStack item) implements Particle {
        public Item {
            Objects.requireNonNull(item, "item");
            if (item.isEmpty()) throw new IllegalArgumentException("빈 아이템 입자는 안 된다");
        }

        @Override public String type() { return "minecraft:item"; }
    }

    /** sculk_charge: 기울기(라디안). */
    record SculkCharge(float roll) implements Particle {
        @Override public String type() { return "minecraft:sculk_charge"; }
    }

    /** shriek: 나타나기까지 지연(틱). */
    record Shriek(int delay) implements Particle {
        @Override public String type() { return "minecraft:shriek"; }
    }

    /** vibration: 목적지(블록 칸 또는 엔티티)와 도착 틱. */
    record Vibration(Destination destination, int arrivalTicks) implements Particle {
        public Vibration { Objects.requireNonNull(destination, "destination"); }

        @Override public String type() { return "minecraft:vibration"; }

        public static Vibration toBlock(BlockPos pos, int arrivalTicks) { return new Vibration(new BlockDestination(pos), arrivalTicks); }

        public static Vibration toEntity(int entityId, float yOffset, int arrivalTicks) { return new Vibration(new EntityDestination(entityId, yOffset), arrivalTicks); }

        public sealed interface Destination {}

        public record BlockDestination(BlockPos pos) implements Destination {
            public BlockDestination { Objects.requireNonNull(pos, "pos"); }
        }

        /** 엔티티 번호와 눈높이 보정(블록). */
        public record EntityDestination(int entityId, float yOffset) implements Destination {}
    }

    private static float clampScale(float s) { return Math.max(0.01f, Math.min(4.0f, s)); }

    private static String ns(String s) {
        Objects.requireNonNull(s, "type");
        return s.indexOf(':') < 0 ? "minecraft:" + s : s;
    }

    /** 종류 이름 → 옵션 모양. */
    enum Kind {
        SIMPLE("of"), BLOCK("block"), DUST("dust"), DUST_TRANSITION("dustTransition"), COLOR("entityEffect"), ITEM("item"),
        SCULK_CHARGE("SculkCharge"), SHRIEK("Shriek"), VIBRATION("Vibration");

        final String factory;

        Kind(String factory) { this.factory = factory; }

        static Kind of(String type) {
            if (BLOCK_TYPES.contains(type)) return BLOCK;
            return switch (type) {
                case "minecraft:dust" -> DUST;
                case "minecraft:dust_color_transition" -> DUST_TRANSITION;
                case "minecraft:entity_effect" -> COLOR;
                case "minecraft:item" -> ITEM;
                case "minecraft:sculk_charge" -> SCULK_CHARGE;
                case "minecraft:shriek" -> SHRIEK;
                case "minecraft:vibration" -> VIBRATION;
                default -> SIMPLE;
            };
        }
    }
}
