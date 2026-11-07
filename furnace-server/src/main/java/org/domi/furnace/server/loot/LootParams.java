// 바닐라 LootParams — 매개변수(위치·엔티티·도구·블록 상태·폭발 반경 …)와 행운, 동적 드롭.
package org.domi.furnace.server.loot;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class LootParams {

    /** 매개변수 키(바닐라 LootContextParams). 값의 자바 타입이 키마다 정해져 있다. */
    public record Key<T>(String name, Class<T> type) {}

    public static final Key<LootEntity> THIS_ENTITY = new Key<>("this_entity", LootEntity.class);
    public static final Key<LootEntity> LAST_DAMAGE_PLAYER = new Key<>("last_damage_player", LootEntity.class);
    public static final Key<LootDamageSource> DAMAGE_SOURCE = new Key<>("damage_source", LootDamageSource.class);
    public static final Key<LootEntity> ATTACKING_ENTITY = new Key<>("attacking_entity", LootEntity.class);
    public static final Key<LootEntity> DIRECT_ATTACKING_ENTITY = new Key<>("direct_attacking_entity", LootEntity.class);
    public static final Key<double[]> ORIGIN = new Key<>("origin", double[].class);
    public static final Key<LootBlockState> BLOCK_STATE = new Key<>("block_state", LootBlockState.class);
    public static final Key<Map> BLOCK_ENTITY = new Key<>("block_entity", Map.class);   // 블록 엔티티가 모은 컴포넌트
    public static final Key<LootStack> TOOL = new Key<>("tool", LootStack.class);
    public static final Key<Float> EXPLOSION_RADIUS = new Key<>("explosion_radius", Float.class);
    public static final Key<Integer> ENCHANTMENT_LEVEL = new Key<>("enchantment_level", Integer.class);
    public static final Key<Boolean> ENCHANTMENT_ACTIVE = new Key<>("enchantment_active", Boolean.class);

    private final Map<Key<?>, Object> values = new HashMap<>();
    private final Map<String, Consumer<Consumer<LootStack>>> dynamicDrops = new HashMap<>();
    private float luck;

    public <T> LootParams with(Key<T> key, T value) {
        if (value != null) values.put(key, value);
        return this;
    }

    /** FishingHook.retrieve 의 FISHING 문맥: origin·tool·this_entity(낚시찌)·행운. */
    public static LootParams fishing(double[] origin, LootStack tool, LootEntity hook, float luck) {
        return new LootParams().with(ORIGIN, origin).with(TOOL, tool).with(THIS_ENTITY, hook).luck(luck);
    }

    public LootParams luck(float luck) {
        this.luck = luck;
        return this;
    }

    /** 바닐라 withDynamicDrop(예: 셜커 상자 minecraft:contents). */
    public LootParams dynamicDrop(String id, Consumer<Consumer<LootStack>> drops) {
        dynamicDrops.put(LootJson.id(id), drops);
        return this;
    }

    /**
     * DecoratedPotBlock.getDrops 의 withDynamicDrop(minecraft:sherds): 단지 블록 엔티티의 pot_decorations 를 뒤·왼·오른·앞 순서로
     * 아이템 하나씩(벽돌 포함) 낸다. 금 간 단지 표가 이 동적 항목을 쓴다.
     */
    public LootParams sherds(Map<?, ?> blockEntityComponents) {
        if (blockEntityComponents != null && blockEntityComponents.get("minecraft:pot_decorations") instanceof java.util.List<?> l) {
            dynamicDrop("minecraft:sherds", out -> {
                for (Object o : l) out.accept(new LootStack(String.valueOf(o), 1));
            });
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Key<T> key) { return (T) values.get(key); }

    public boolean has(Key<?> key) { return values.containsKey(key); }

    public float luck() { return luck; }

    void addDynamicDrops(String id, Consumer<LootStack> out) {
        Consumer<Consumer<LootStack>> d = dynamicDrops.get(id);
        if (d != null) d.accept(out);
    }
}
