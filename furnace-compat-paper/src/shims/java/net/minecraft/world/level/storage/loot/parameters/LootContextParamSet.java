package net.minecraft.world.level.storage.loot.parameters;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 전리품 매개변수 묶음(바닐라 {@code LootContextParamSet} — 필수·허용 키). 바닐라는 식별 집합을 쓰므로 {@code toString} 순서가 실행마다 다를 수 있다.
 * 여기서는 넣은 순서다.
 */
class LootContextParamSet {

    private final Set<LootContextParam<?>> required;
    private final Set<LootContextParam<?>> all;

    LootContextParamSet(Set<LootContextParam<?>> required, Set<LootContextParam<?>> optional) {
        this.required = Collections.unmodifiableSet(new LinkedHashSet<>(required));
        Set<LootContextParam<?>> a = new LinkedHashSet<>(required);
        a.addAll(optional);
        this.all = Collections.unmodifiableSet(a);
    }

    public boolean isAllowed(LootContextParam<?> param) { return all.contains(param); }

    public Set<LootContextParam<?>> getRequired() { return required; }

    public Set<LootContextParam<?>> getAllowed() { return all; }

    @Override
    public String toString() {
        return "[" + all.stream().map(p -> (required.contains(p) ? "!" : "") + p.getName()).collect(Collectors.joining(", ")) + "]";
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Set<LootContextParam<?>> required = new LinkedHashSet<>();
        private final Set<LootContextParam<?>> optional = new LinkedHashSet<>();

        public Builder required(LootContextParam<?> param) {
            if (optional.contains(param)) throw new IllegalArgumentException("Parameter " + param.getName() + " is already optional");
            required.add(param);
            return this;
        }

        public Builder optional(LootContextParam<?> param) {
            if (required.contains(param)) throw new IllegalArgumentException("Parameter " + param.getName() + " is already required");
            optional.add(param);
            return this;
        }

        public LootContextParamSet build() { return new LootContextParamSet(required, optional); }
    }
}
