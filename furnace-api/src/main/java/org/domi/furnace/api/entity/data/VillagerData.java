// 주민·좀비 주민 겉모습(바닐라 VillagerData): 종류(바이옴 옷), 직업, 레벨(1~5). 이름은 "minecraft:plains"·"minecraft:librarian" 꼴.
package org.domi.furnace.api.entity.data;

import java.util.Objects;

public record VillagerData(String type, String profession, int level) {

    public VillagerData {
        type = ns(Objects.requireNonNull(type, "type"));
        profession = ns(Objects.requireNonNull(profession, "profession"));
        if (level < 1) level = 1;   // 바닐라 VillagerData 생성자도 1 아래는 1 로 올린다
    }

    private static String ns(String s) { return s.indexOf(':') < 0 ? "minecraft:" + s : s; }

    public VillagerData withType(String t) { return new VillagerData(t, profession, level); }

    public VillagerData withProfession(String p) { return new VillagerData(type, p, level); }

    public VillagerData withLevel(int l) { return new VillagerData(type, profession, l); }
}
