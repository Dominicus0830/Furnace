// 게임 동기화 레지스트리 보기와 마법부여 끄기 — Furnace.server().gameRegistries().
// 항목 순서는 클라가 받는 번호 순서다(시작 때 고정). 항목을 더하는 것은 부트스트랩 훅(LifecycleEvents.GAME_REGISTRIES)으로만 된다.
// 마법부여 끄기(불만 표 32번): 꺼진 마법부여는 마법 부여대·전리품(낚시·상자의 무작위 마법부여)·마법 부여 제공자(몹·습격자 장비)·
// 주민 거래에서 새로 나오지 않는다. 이미 붙은 아이템, /enchant, 모루 합치기, 전리품의 set_enchantments(명시한 것)는 그대로이고,
// 레지스트리에서도 빠지지 않는다(다시 켜면 곧바로 원래대로). 서버 설정 furnace.properties 의 enchantments.disabled 로도 끌 수 있다.
// 플러그인이 끈 것은 그 플러그인이 꺼지면 풀린다. 아무 스레드에서 불러도 된다.
package org.domi.furnace.api.registry;

import java.util.List;
import java.util.Set;
import org.domi.furnace.api.plugin.FurnacePlugin;

public interface GameRegistries {

    String ENCHANTMENT = "minecraft:enchantment";
    String WOLF_VARIANT = "minecraft:wolf_variant";
    String PAINTING_VARIANT = "minecraft:painting_variant";
    String BANNER_PATTERN = "minecraft:banner_pattern";
    String TRIM_MATERIAL = "minecraft:trim_material";
    String TRIM_PATTERN = "minecraft:trim_pattern";
    String JUKEBOX_SONG = "minecraft:jukebox_song";

    /** 그 레지스트리의 항목 id(클라 번호 순). 모르는 레지스트리면 빈 목록. */
    List<String> entries(String registry);

    default boolean contains(String registry, String key) { return entries(registry).contains(key); }

    /**
     * 내장 레지스트리(데이터 팩이 바꾸지 않는 것: minecraft:block·minecraft:item·minecraft:entity_type 등)의 항목 id, 바닐라 1.21.1 번호 순.
     * 모르는 레지스트리·동기화 레지스트리면 빈 목록. 번호는 버전마다 바뀌므로 저장하지 말 것(NMS 호환용).
     */
    default List<String> builtIn(String registry) { throw new UnsupportedOperationException("builtIn"); }

    /** 항목을 낸 곳: "vanilla", 데이터 팩 id(예: "file/mypack"), "plugin:<이름>". 없거나 내장 목록이면 empty. */
    java.util.Optional<String> source(String registry, String key);

    /** 마법부여를 끈다(owner 몫으로). 레지스트리에 없는 키면 IllegalArgumentException. */
    void disableEnchantment(FurnacePlugin owner, String key);

    /** owner 가 끈 것을 푼다(설정이나 다른 플러그인이 끈 것은 그대로). 풀린 게 있으면 true. */
    boolean enableEnchantment(FurnacePlugin owner, String key);

    boolean isEnchantmentDisabled(String key);

    /** 지금 꺼진 마법부여(이름 순). */
    Set<String> disabledEnchantments();
}
