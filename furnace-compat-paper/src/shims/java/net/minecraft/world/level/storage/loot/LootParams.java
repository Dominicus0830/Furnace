package net.minecraft.world.level.storage.loot;

import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;

/** 전리품 매개변수(바닐라 {@code LootParams}). 굴리기는 {@link LootTable} 이 서버 다리로 한다. */
class LootParams {

    private final ServerLevel level;
    private final Map<LootContextParam<?>, Object> params;
    private final Map<ResourceLocation, DynamicDrop> dynamicDrops;
    private final float luck;

    public LootParams(ServerLevel level, Map<LootContextParam<?>, Object> params, Map<ResourceLocation, DynamicDrop> dynamicDrops, float luck) {
        this.level = level;
        this.params = params;
        this.dynamicDrops = dynamicDrops;
        this.luck = luck;
    }

    public ServerLevel getLevel() { return level; }

    public boolean hasParam(LootContextParam<?> param) { return params.containsKey(param); }

    @SuppressWarnings("unchecked")
    public <T> T getParameter(LootContextParam<T> param) {
        T value = (T) params.get(param);
        if (value == null) throw new NoSuchElementException(param.getName().toString());
        return value;
    }

    @SuppressWarnings("unchecked")
    public <T> T getOptionalParameter(LootContextParam<T> param) { return (T) params.get(param); }

    @SuppressWarnings("unchecked")
    public <T> T getParamOrNull(LootContextParam<T> param) { return (T) params.get(param); }

    public void addDynamicDrops(ResourceLocation id, Consumer<ItemStack> output) {
        DynamicDrop drop = dynamicDrops.get(id);
        if (drop != null) drop.add(output);
    }

    public float getLuck() { return luck; }

    /** 호환 모듈 전용: 매개변수 값(굴리기가 서버로 넘길 때 읽는다). */
    Map<LootContextParam<?>, Object> furnaceValues() { return params; }

    /** 호환 모듈 전용: 동적 드롭을 API 값 소비자 꼴로(서버가 표의 dynamic 항목에서 부른다). */
    Map<String, Consumer<Consumer<org.domi.furnace.api.item.ItemStack>>> furnaceDynamicDrops() {
        Map<String, Consumer<Consumer<org.domi.furnace.api.item.ItemStack>>> out = new java.util.LinkedHashMap<>();
        for (var e : dynamicDrops.entrySet()) {
            DynamicDrop d = e.getValue();
            out.put(e.getKey().toString(), apiOut -> d.add(nms -> apiOut.accept(nms.furnaceValue())));
        }
        return out;
    }

    @FunctionalInterface
    public interface DynamicDrop {
        void add(Consumer<ItemStack> output);
    }

    public static class Builder {
        private final ServerLevel level;
        private final Map<LootContextParam<?>, Object> params = new IdentityHashMap<>();
        private final Map<ResourceLocation, DynamicDrop> dynamicDrops = new java.util.HashMap<>();
        private float luck;

        public Builder(ServerLevel level) { this.level = level; }

        public ServerLevel getLevel() { return level; }

        public <T> Builder withParameter(LootContextParam<T> param, T value) {
            params.put(param, value);
            return this;
        }

        public <T> Builder withOptionalParameter(LootContextParam<T> param, T value) {
            if (value == null) params.remove(param);
            else params.put(param, value);
            return this;
        }

        @SuppressWarnings("unchecked")
        public <T> T getParameter(LootContextParam<T> param) {
            T value = (T) params.get(param);
            if (value == null) throw new NoSuchElementException(param.getName().toString());
            return value;
        }

        @SuppressWarnings("unchecked")
        public <T> T getOptionalParameter(LootContextParam<T> param) { return (T) params.get(param); }

        public Builder withDynamicDrop(ResourceLocation id, DynamicDrop drop) {
            DynamicDrop previous = dynamicDrops.put(id, drop);
            if (previous != null) throw new IllegalStateException("Duplicated dynamic drop '" + dynamicDrops + "'");
            return this;
        }

        public Builder withLuck(float luck) {
            this.luck = luck;
            return this;
        }

        private static Set<LootContextParam<?>> difference(Set<LootContextParam<?>> a, Set<LootContextParam<?>> b) {
            Set<LootContextParam<?>> out = new LinkedHashSet<>(a);
            out.removeAll(b);
            return out;
        }

        public LootParams create(LootContextParamSet paramSet) {
            Set<LootContextParam<?>> notAllowed = difference(params.keySet(), paramSet.getAllowed());
            if (!notAllowed.isEmpty()) throw new IllegalArgumentException("Parameters not allowed in this parameter set: " + notAllowed);
            Set<LootContextParam<?>> missing = difference(paramSet.getRequired(), params.keySet());
            if (!missing.isEmpty()) throw new IllegalArgumentException("Missing required parameters: " + missing);
            return new LootParams(level, params, dynamicDrops, luck);
        }
    }
}
