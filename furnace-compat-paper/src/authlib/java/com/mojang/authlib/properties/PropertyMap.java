package com.mojang.authlib.properties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * authlib PropertyMap 의 컴파일·시험용 대역(배포 jar 에 넣지 않는다). 진짜는 Guava ForwardingMultimap&lt;String, Property&gt; 다 —
 * 호환 계층이 부르는 put·values·isEmpty 만 진짜와 같은 지운 서술자로 둔다(put(Object, Object)boolean, values()Collection, isEmpty()boolean).
 */
public class PropertyMap {

    private final Map<String, List<Property>> map = new LinkedHashMap<>();

    public boolean put(Object key, Object value) {
        return map.computeIfAbsent((String) key, k -> new ArrayList<>()).add((Property) value);
    }

    public Collection<Property> values() {
        List<Property> out = new ArrayList<>();
        for (List<Property> l : map.values()) out.addAll(l);
        return out;
    }

    public boolean isEmpty() { return map.isEmpty(); }

    @Override
    public String toString() { return map.toString(); }
}
