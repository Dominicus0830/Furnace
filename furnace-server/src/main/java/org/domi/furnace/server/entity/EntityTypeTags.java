// entity_type 태그(바닐라 데이터 생성기의 태그 표에서 읽는다).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.net.registry.TagSet;

public final class EntityTypeTags {

    private EntityTypeTags() {}

    private static final class Holder {
        static final TagSet SET = TagSet.load("registries/tags-1.21.1.txt");
    }

    /** 그 태그에 든 종류 번호들(없으면 null). */
    public static int[] ids(String tag) { return Holder.SET.ids("minecraft:entity_type", tag); }
}
