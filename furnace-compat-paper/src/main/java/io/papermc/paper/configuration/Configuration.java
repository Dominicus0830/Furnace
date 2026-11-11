package io.papermc.paper.configuration;

/**
 * Paper 서버의 설정 상수 클래스(Paper 1.21.1 서버 쪽 {@code io.papermc.paper.configuration.Configuration}).
 *
 * <p>Furnace 가 이 클래스를 두는 까닭은 값이 아니라 <b>존재</b>다: 플러그인들이 "Paper 서버인가"를 이 클래스(또는 1.19 이전의
 * {@code com.destroystokyo.paper.PaperConfig})가 있는지로 판별한다 — PaperLib {@code isPaper()}, FastAsyncWorldEdit
 * {@code PaperSupport}. 없으면 Paper 용 jar 가 "Spigot 서버에서 Paper jar 를 돌린다"며 onLoad 에서 멈춘다.
 * Furnace 호환 계층은 Paper API(teleportAsync·비동기 스케줄러 등)를 목표로 하므로 Paper 로 판별되는 쪽이 맞다.
 * 상수 값은 Paper 원문을 대조하지 못한 기억 기반이다(가정).
 */
public final class Configuration {

    /** 설정 파일의 판 번호 키. */
    public static final String VERSION_FIELD = "_version";

    /** 옛 paper.yml 의 판 번호 키. */
    @Deprecated
    public static final String LEGACY_CONFIG_VERSION_FIELD = "config-version";

    private Configuration() {}
}
