package net.minecraft.world.level.block.state;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 한 주인(블록)의 속성 목록과 가능한 상태 전부(Mojang 이름 shim, N0). 속성은 이름순, 상태는 바닐라 상태 번호 순서다.
 * 속성을 더해 새 정의를 만드는 {@code Builder} 는 플러그인이 블록을 등록할 수 없어 두지 않았다.
 */
public class StateDefinition<O, S extends StateHolder<O, S>> {

    private final O owner;
    private final Map<String, Property<?>> propertiesByName;
    private final List<S> states;
    /** getPossibleStates 의 Guava 사본(처음 부를 때 만든다 — Guava 는 그 메서드를 부르는 플러그인이 있을 때만 붙으므로 필드 형식에 쓰지 않는다). */
    private volatile Object immutableStates;

    /** 호환 모듈 전용 — 블록 shim 이 만든다(속성은 이름순 Map, 상태는 번호 순). */
    public StateDefinition(O owner, Map<String, Property<?>> propertiesByName, List<S> states) {
        this.owner = owner;
        this.propertiesByName = Collections.unmodifiableMap(propertiesByName);
        this.states = List.copyOf(states);
    }

    /** 바닐라처럼 Guava ImmutableList(Orebfuscator 가 그 서술자로 부른다). */
    public com.google.common.collect.ImmutableList<S> getPossibleStates() {
        Object o = immutableStates;
        if (o == null) immutableStates = o = com.google.common.collect.ImmutableList.copyOf(states);
        @SuppressWarnings("unchecked") var list = (com.google.common.collect.ImmutableList<S>) o;
        return list;
    }

    public S any() { return states.get(0); }

    public O getOwner() { return owner; }

    public Collection<Property<?>> getProperties() { return propertiesByName.values(); }

    public Property<?> getProperty(String name) { return propertiesByName.get(name); }

    @Override
    public String toString() {
        return "StateDefinition{block=" + owner + ", properties="
                + propertiesByName.values().stream().map(Property::getName).collect(Collectors.toList()) + "}";
    }
}
