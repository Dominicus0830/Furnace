package net.minecraft.world.level.levelgen.feature;

import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * 지형지물 종류(Mojang 이름 shim, N2b-6). 설정된 지형지물({@link ConfiguredFeature})이 어떤 종류인지 알아보는 용도다 —
 * 나무는 {@link TreeFeature}, 산호 나무는 {@link CoralTreeFeature} 의 인스턴스, 나머지는 {@link FurnaceFeature}(종류 이름만).
 * 종류 하나는 하나의 객체다({@link #byType}). {@code place(…)} 등 생성 동작은 없다(부르면 UnsupportedNmsCall).
 */
public abstract class Feature<FC extends FeatureConfiguration> {

    private final String typeId;

    /** 호환 모듈 전용 — 종류 id(예: minecraft:tree). */
    protected Feature(String typeId) { this.typeId = typeId; }

    /** Furnace: 이 종류의 id(JSON 의 type). */
    public final String furnaceTypeId() { return typeId; }

    private static final java.util.concurrent.ConcurrentHashMap<String, Feature<?>> TYPES = new java.util.concurrent.ConcurrentHashMap<>();

    /** Furnace: 종류 id 의 객체(같은 id 면 같은 객체). */
    public static Feature<?> byType(String typeId) {
        return TYPES.computeIfAbsent(typeId, id -> switch (id) {
            case "minecraft:tree" -> new TreeFeature(id);
            case "minecraft:coral_tree" -> new CoralTreeFeature(id);
            case "minecraft:coral_claw" -> new CoralClawFeature(id);
            case "minecraft:coral_mushroom" -> new CoralMushroomFeature(id);
            default -> new FurnaceFeature<>(id);
        });
    }

    @Override
    public String toString() { return "Feature[" + typeId + "]"; }
}
