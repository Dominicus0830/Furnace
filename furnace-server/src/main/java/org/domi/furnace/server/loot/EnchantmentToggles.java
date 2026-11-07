// 꺼진 마법부여(불만 표 32번 "마법부여 끄기"). 꺼진 마법부여는 게임이 새로 고르는 곳에서 빠진다:
//   마법 부여대, 전리품의 enchant_randomly·enchant_with_levels(낚시·상자 포함), 마법 부여 제공자(몹 장비·습격자 장비 등),
//   주민 거래(마법이 부여된 책·장비). 이미 붙은 아이템·/enchant·모루 합치기·전리품의 set_enchantments(명시한 것)는 그대로다.
// 레지스트리에서는 빼지 않는다 — 클라 번호·아이템 데이터가 그대로라 다시 켜면 곧바로 원래대로다.
// 끈 주체(설정 "config" 또는 플러그인 이름)별로 기록하고, 플러그인이 꺼지면 그 플러그인 몫만 풀린다. 아무 스레드에서 써도 된다.
package org.domi.furnace.server.loot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class EnchantmentToggles {
    private EnchantmentToggles() {}

    public static final String CONFIG = "config";

    private static final Map<String, Set<String>> owners = new HashMap<>();
    private static volatile Set<String> disabled = Set.of();

    /** 꺼졌는가. 아무것도 끄지 않았으면 바로 거짓(평소 경로에 비용이 없다). */
    public static boolean isDisabled(String id) {
        Set<String> d = disabled;
        return !d.isEmpty() && d.contains(id);
    }

    public static boolean any() { return !disabled.isEmpty(); }

    /** 지금 꺼진 것(이름 순). */
    public static Set<String> disabled() { return Collections.unmodifiableSet(new TreeSet<>(disabled)); }

    public static synchronized void disable(String owner, String id) {
        owners.computeIfAbsent(id, k -> new LinkedHashSet<>()).add(owner);
        publish();
    }

    /** 그 주체가 끈 것을 푼다(다른 주체가 끈 것은 그대로). 풀린 게 있으면 참. */
    public static synchronized boolean enable(String owner, String id) {
        Set<String> s = owners.get(id);
        if (s == null || !s.remove(owner)) return false;
        if (s.isEmpty()) owners.remove(id);
        publish();
        return true;
    }

    /** 그 주체의 몫을 모두 푼다(플러그인이 꺼질 때). */
    public static synchronized void removeOwner(String owner) {
        owners.values().removeIf(s -> s.remove(owner) && s.isEmpty());
        publish();
    }

    /** 설정(furnace.properties enchantments.disabled)의 목록으로 바꾼다. */
    public static synchronized void configure(List<String> ids) {
        removeOwner(CONFIG);
        for (String id : ids) owners.computeIfAbsent(id, k -> new LinkedHashSet<>()).add(CONFIG);
        publish();
    }

    /**
     * furnace.properties 의 enchantments.disabled(쉼표로 나눈 id, 기본 비어 있음)를 읽어 건다. namespace 가 없으면 minecraft.
     * known 에 없는 id 는 경고하고 뺀다.
     */
    public static void load(java.nio.file.Path furnaceProps, java.util.function.Predicate<String> known, java.util.function.Consumer<String> warn) {
        List<String> ids = new ArrayList<>();
        if (java.nio.file.Files.exists(furnaceProps)) {
            java.util.Properties p = new java.util.Properties();
            try (var in = java.nio.file.Files.newInputStream(furnaceProps)) {
                p.load(in);
            } catch (java.io.IOException e) {
                warn.accept("furnace.properties 를 읽지 못해 enchantments.disabled 를 건너뛴다: " + e.getMessage());
            }
            for (String raw : p.getProperty("enchantments.disabled", "").split(",")) {
                String id = raw.strip();
                if (id.isEmpty()) continue;
                if (id.indexOf(':') < 0) id = "minecraft:" + id;
                if (known.test(id)) ids.add(id);
                else warn.accept("enchantments.disabled: 모르는 마법부여 " + id);
            }
        }
        configure(ids);
    }

    /** 시험용: 모두 푼다. */
    public static synchronized void reset() {
        owners.clear();
        publish();
    }

    private static void publish() { disabled = Set.copyOf(owners.keySet()); }

    /** 꺼진 것을 뺀 목록(아무것도 꺼지지 않았으면 그대로 돌려준다). */
    static List<Enchantment> enabled(List<Enchantment> list) {
        if (!any()) return list;
        List<Enchantment> out = new ArrayList<>(list.size());
        for (Enchantment e : list) if (!isDisabled(e.id())) out.add(e);
        return out;
    }

    /** 꺼진 것을 뺀 id 목록. */
    public static List<String> enabledIds(List<String> list) {
        if (!any()) return list;
        List<String> out = new ArrayList<>(list.size());
        for (String e : list) if (!isDisabled(e)) out.add(e);
        return out;
    }
}
