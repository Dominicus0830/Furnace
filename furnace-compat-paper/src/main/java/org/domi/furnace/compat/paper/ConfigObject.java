// 설정 트리의 잎에 들어가는 직렬화 객체 — Paper MemorySection 처럼 넣은 객체 참조를 그대로 들고 있다가 쓸 때만 serialize() 를 부른다.
package org.domi.furnace.compat.paper;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.domi.furnace.api.config.ConfigSection;

public final class ConfigObject implements ConfigSection.Opaque {

    private final ConfigurationSerializable value;

    public ConfigObject(ConfigurationSerializable value) { this.value = value; }

    public ConfigurationSerializable value() { return value; }

    /** 쓰는 순간의 저장 꼴({@code ==} 별칭이 맨 앞인 맵). */
    @Override
    public Object lower() { return ConfigValues.lowerSerializable(value); }

    @Override
    public boolean equals(Object o) { return o == this || o instanceof ConfigObject c && value.equals(c.value); }

    @Override
    public int hashCode() { return value.hashCode(); }

    @Override
    public String toString() { return String.valueOf(value); }
}
