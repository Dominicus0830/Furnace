// LEGACY-ORACLE since 14767c19: EventBridge.Mapping#bukkitFor 의 live(synchronizedMap(WeakHashMap)) — T-032 붙임 칸 이전
// 옮길 때 바꾼 것은 클래스 밖에서 돌게 하는 부분뿐이다: Mapping 의 필드(live·create) → 이 객체의 필드·생성자 인자,
// 타입 인자 F·B → Event·Object(호환 계층 모듈이 서버 시험에 보이지 않는다). 찾기·만들기·SKIP 표지 계산은 그 커밋 그대로다.
package org.domi.furnace.server.identity.legacy.plugin;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import org.domi.furnace.api.event.Event;
import org.domi.furnace.server.identity.LegacyOracle;

@LegacyOracle(since = "14767c19", copyOf = "furnace-compat-paper EventBridge.Mapping#bukkitFor (live 맵)")
public final class LegacyBridgeLiveMap {

    private static final Object SKIP = new Object();

    private final Function<Event, Object> create;
    private final Map<Event, Object> live = Collections.synchronizedMap(new WeakHashMap<>());

    public LegacyBridgeLiveMap(Function<Event, Object> create) { this.create = create; }

    /** 이 Furnace 이벤트의 Bukkit 이벤트(처음 부르면 만든다). 건너뛰는 이벤트면 null. */
    public Object bukkitFor(Event f) {
        synchronized (live) {
            Object o = live.get(f);
            if (o == null) {
                Object b = create.apply(f);
                live.put(f, b == null ? SKIP : b);
                return b;
            }
            return o == SKIP ? null : o;
        }
    }
}
