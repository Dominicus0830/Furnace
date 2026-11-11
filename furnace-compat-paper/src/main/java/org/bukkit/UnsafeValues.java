package org.bukkit;

import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.PluginDescriptionFile;

/**
 * Bukkit·Paper 의 "내부 값" 창구({@link Bukkit#getUnsafe()}). 실제 구현은 Paper 처럼
 * {@code org.bukkit.craftbukkit.util.CraftMagicNumbers}. 이름·서명은 Paper 1.21.1 API 와 같다.
 *
 * <p>여기 있는 것만 지원한다. 레거시 Material 변환(toLegacy·fromLegacy)·{@code modifyItemStack}·{@code processClass}·
 * 번역 키·아이템 바이트 직렬화 같은 나머지 멤버는 아직 없어서, 부르면 JVM 이 {@code NoSuchMethodError} 를 낸다
 * (조용히 틀린 값을 주지 않는다).
 */
@Deprecated
public interface UnsafeValues {

    /** 이 서버의 저장 데이터 버전(1.21.1 = 3955). adventure-platform 같은 라이브러리가 JSON 형식을 고를 때 쓴다. */
    int getDataVersion();

    /** 클라이언트 프로토콜 번호(1.21.1 = 767). Paper API. */
    int getProtocolVersion();

    /**
     * 그 api-version 을 이 서버가 받는가(Paper API): null 이면 false, 서버 API(1.21.1)보다 새로우면 false.
     * 읽을 수 없는 값이면 IllegalArgumentException.
     */
    boolean isSupportedApiVersion(String apiVersion);

    /** 그 플러그인의 api-version 이 서버보다 새로우면 InvalidPluginException("Unsupported API version ..."). */
    void checkSupported(PluginDescriptionFile pdf) throws InvalidPluginException;

    /** 주 월드(오버월드) 레벨 이름 — server.properties 의 level-name. Paper API. */
    String getMainLevelName();

    /** 서버 엔티티와 겹치지 않는 새 엔티티 번호(패킷으로만 보이는 엔티티용). Paper API. */
    int nextEntityId();
}
