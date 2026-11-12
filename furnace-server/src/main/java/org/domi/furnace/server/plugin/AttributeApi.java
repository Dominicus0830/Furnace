// 엔티티 속성 API(api.entity.attribute)의 서버 쪽: API 값 ↔ 서버 속성 변환, 지금 값 손잡이(AttributeInstance), 핸들(AttributeHandle).
// 바닐라 AttributeInstance 를 그대로 만지므로 /attribute 명령과 효과·장비 수정자가 같은 상태를 본다. 플레이어는 /attribute 와 같은 경로(AttributeMap 직접).
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.domi.furnace.api.entity.attribute.Attribute;
import org.domi.furnace.api.entity.attribute.AttributeHandle;
import org.domi.furnace.api.entity.attribute.AttributeInstance;
import org.domi.furnace.api.entity.attribute.AttributeModifier;
import org.domi.furnace.api.item.AttributeModifiers.Operation;
import org.domi.furnace.server.attribute.AttributeMap;

final class AttributeApi {
    private AttributeApi() { }

    static final String ALREADY_APPLIED = "Modifier is already applied on this attribute!";

    /** API 속성 → 서버 속성(키로 찾는다 — 서버 표와 같은 키여야 한다, AttributeApiTest 가 전부 대조). */
    static org.domi.furnace.server.attribute.Attribute server(Attribute a) {
        var s = org.domi.furnace.server.attribute.Attribute.byName(a.key());
        if (s == null) throw new IllegalArgumentException("서버에 없는 속성: " + a.key());
        return s;
    }

    static Attribute api(org.domi.furnace.server.attribute.Attribute s) { return Attribute.of(s.name()); }

    static int opId(Operation o) {
        return switch (o) {
            case ADD_VALUE -> org.domi.furnace.server.attribute.AttributeModifier.ADD_VALUE;
            case ADD_MULTIPLIED_BASE -> org.domi.furnace.server.attribute.AttributeModifier.ADD_MULTIPLIED_BASE;
            case ADD_MULTIPLIED_TOTAL -> org.domi.furnace.server.attribute.AttributeModifier.ADD_MULTIPLIED_TOTAL;
        };
    }

    static Operation operation(int id) {
        return switch (id) {
            case org.domi.furnace.server.attribute.AttributeModifier.ADD_MULTIPLIED_BASE -> Operation.ADD_MULTIPLIED_BASE;
            case org.domi.furnace.server.attribute.AttributeModifier.ADD_MULTIPLIED_TOTAL -> Operation.ADD_MULTIPLIED_TOTAL;
            default -> Operation.ADD_VALUE;
        };
    }

    /** API 가 id 를 맞추는 규칙(네임스페이스 없으면 minecraft:, 소문자 키 검사)을 그대로 쓴다. */
    static String normalizeId(String id) { return new AttributeModifier(id, 0, Operation.ADD_VALUE).id(); }

    static org.domi.furnace.server.attribute.AttributeModifier server(AttributeModifier m) {
        return new org.domi.furnace.server.attribute.AttributeModifier(m.id(), m.amount(), opId(m.operation()));
    }

    static AttributeModifier api(org.domi.furnace.server.attribute.AttributeModifier m) {
        return new AttributeModifier(m.id(), m.amount(), operation(m.operation()));
    }

    static List<AttributeModifier> apiModifiers(Map<String, org.domi.furnace.server.attribute.AttributeModifier> mods) {
        List<AttributeModifier> out = new ArrayList<>(mods.size());
        for (var m : mods.values()) {
            try {
                out.add(api(m));
            } catch (IllegalArgumentException e) {
                // NBT 에서 읽은 키가 아닌 id 는 API 값으로 만들 수 없다 — 보이지 않을 뿐 계산에는 그대로 든다
            }
        }
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------ 지금 값 손잡이

    /** 서버 AttributeInstance 위의 손잡이. check 는 호출마다 소유 스레드인지 확인한다. */
    static final class Live implements AttributeInstance {
        private final org.domi.furnace.server.attribute.AttributeInstance i;
        private final Runnable check;

        Live(org.domi.furnace.server.attribute.AttributeInstance i, Runnable check) {
            this.i = i;
            this.check = check;
        }

        @Override
        public Attribute attribute() { return api(i.attribute()); }

        @Override
        public double defaultValue() {
            check.run();
            return i.attribute().defaultValue();
        }

        @Override
        public double baseValue() {
            check.run();
            return i.baseValue();
        }

        @Override
        public void setBaseValue(double value) {
            check.run();
            i.setBaseValue(value);
        }

        @Override
        public double value() {
            check.run();
            return i.value();
        }

        @Override
        public List<AttributeModifier> modifiers() {
            check.run();
            return apiModifiers(i.modifiers());
        }

        @Override
        public Optional<AttributeModifier> modifier(String id) {
            check.run();
            var m = i.getModifier(normalizeId(id));
            return m == null ? Optional.empty() : Optional.of(api(m));
        }

        @Override
        public void addModifier(AttributeModifier modifier) {
            java.util.Objects.requireNonNull(modifier, "modifier");
            check.run();
            if (i.hasModifier(modifier.id())) throw new IllegalArgumentException(ALREADY_APPLIED);
            i.addOrReplaceModifier(server(modifier));
        }

        @Override
        public void addTransientModifier(AttributeModifier modifier) {
            java.util.Objects.requireNonNull(modifier, "modifier");
            check.run();
            if (i.hasModifier(modifier.id())) throw new IllegalArgumentException(ALREADY_APPLIED);
            i.addOrReplaceTransientModifier(server(modifier));
        }

        @Override
        public void setModifier(AttributeModifier modifier) {
            java.util.Objects.requireNonNull(modifier, "modifier");
            check.run();
            i.addOrReplaceModifier(server(modifier));
        }

        @Override
        public boolean removeModifier(String id) {
            check.run();
            return i.removeModifier(normalizeId(id));
        }
    }

    // ------------------------------------------------------------------ 핸들

    /** 소유 스레드에서 속성 맵으로 일을 돌려 주는 쪽(엔티티·플레이어마다 다르다). 엔티티가 없어졌으면 IllegalStateException 으로 완료한다. */
    interface Owner {
        <T> CompletableFuture<T> run(Function<AttributeMap, T> task);

        /** 소유 스레드 확인(손잡이 호출마다) — 이미 소유 스레드 안에서 돈 일이라 보통 통과한다. */
        default void check() { }
    }

    /** 핸들 시점 값 하나와 소유 스레드 실행자. */
    static final class Handle implements AttributeHandle {
        private final org.domi.furnace.server.attribute.AttributeMap.View view;
        private final Owner owner;
        private final List<AttributeModifier> modifiers;

        Handle(org.domi.furnace.server.attribute.AttributeMap.View view, Owner owner) {
            this.view = view;
            this.owner = owner;
            this.modifiers = apiModifiers(view.modifiers());
        }

        @Override
        public Attribute attribute() { return api(view.attribute()); }

        @Override
        public double defaultValue() { return view.attribute().defaultValue(); }

        @Override
        public double baseValue() { return view.base(); }

        @Override
        public double value() { return view.value(); }

        @Override
        public List<AttributeModifier> modifiers() { return modifiers; }

        @Override
        @SuppressWarnings("unchecked")
        public <T> CompletableFuture<T> edit(Function<? super AttributeInstance, ? extends T> action) {
            java.util.Objects.requireNonNull(action, "action");
            var attr = view.attribute();
            return owner.run(map -> {
                var inst = map.get(attr);
                if (inst == null) throw new IllegalStateException("그 엔티티에 속성이 없다: " + attr.name());
                return (T) action.apply(new Live(inst, owner::check));
            });
        }
    }

    /** 맵의 스냅샷에서 핸들을 만든다(속성이 없으면 empty). */
    static Optional<AttributeHandle> handle(Map<org.domi.furnace.server.attribute.Attribute, org.domi.furnace.server.attribute.AttributeMap.View> snap, Attribute a, Owner owner) {
        var v = snap.get(server(a));
        return v == null ? Optional.empty() : Optional.of(new Handle(v, owner));
    }

    static List<Attribute> keys(Map<org.domi.furnace.server.attribute.Attribute, ?> snap) {
        List<Attribute> out = new ArrayList<>(snap.size());
        for (var a : snap.keySet()) out.add(api(a));
        return List.copyOf(out);
    }
}
