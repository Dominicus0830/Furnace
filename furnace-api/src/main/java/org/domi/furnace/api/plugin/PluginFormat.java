// 다른 서버 소프트웨어의 플러그인 형식(예: Bukkit·Paper 의 plugin.yml)을 읽어 주는 어댑터 SPI.
// 호환 모듈(furnace-compat-paper)이 java.util.ServiceLoader 로 등록한다. 어댑터가 없으면 서버는 Furnace 설명 파일만 읽는다.
package org.domi.furnace.api.plugin;

import java.util.List;

public interface PluginFormat {

    /** 로그에 쓰는 짧은 이름(예: "bukkit"). */
    String id();

    /** 이 형식의 설명 파일 이름(우선순위 순, jar 루트). */
    List<String> descriptorNames();

    /**
     * 설명 파일을 Furnace 의 PluginDescription 으로 바꾼다(이름·버전·메인·의존 순서·명령·권한). 읽을 수 없으면 IllegalArgumentException.
     * api-version 은 이 형식의 것이라 서버의 ApiVersion 검사에 걸리면 안 되므로 어댑터가 비워(null) 돌려주고 스스로 검사한다.
     *
     * @param fileName 읽은 설명 파일 이름(descriptorNames 중 하나)
     * @param text     설명 파일 내용(UTF-8)
     */
    PluginDescription readDescriptor(String fileName, String text);

    /**
     * 메인 클래스로 Furnace 플러그인 껍데기를 만든다. 서버는 이걸 다른 플러그인처럼 init·onLoad·onEnable·onDisable 로 다룬다.
     * 메인 클래스가 이 형식의 플러그인 클래스가 아니면 IllegalArgumentException.
     *
     * @param context 이 플러그인의 환경(설명·로거·폴더·jar·클래스로더). 서버가 init 에 넘기는 것과 같은 객체다.
     */
    FurnacePlugin create(PluginContext context, Class<?> main) throws ReflectiveOperationException;

    /**
     * 이 플러그인 jar 의 클래스를 올릴 때 거칠 바이트 변환기. 서버는 클래스로더를 만들 때 한 번 묻고, 그 jar(플러그인 jar 자체 — 자동으로 받은
     * {@code libraries} jar 는 아니다)에서 읽은 클래스마다 부른다. 기본은 null(변환 없음).
     * 서버 옵션 {@code plugins.compat.nms.enabled} 가 꺼져 있으면 서버는 이 메서드를 부르지 않는다(모든 형식의 변환기가 꺼진다).
     *
     * @param description {@link #readDescriptor} 가 돌려준 설명
     * @param jar         플러그인 jar(개발 모드에서는 사본)
     */
    default ClassTransformer transformer(PluginDescription description, java.nio.file.Path jar) { return null; }

    /**
     * {@link #transformer(PluginDescription, java.nio.file.Path)} 에 서버 설정을 더한 것. 서버는 이것을 부른다(기본은 설정 없이 위 메서드).
     * 이 메서드가 던지는 {@link RuntimeException} 은 그 플러그인을 불러오지 못한 것으로 친다(예: 엄격 모드에서 지원하지 않는 호출이 있다).
     *
     * @param settings 서버가 검사한 {@code plugins.compat.*} 설정(접두어 포함 키, 기본값과 같은 키는 빠진다, 바꿀 수 없는 보기)
     */
    default ClassTransformer transformer(PluginDescription description, java.nio.file.Path jar, java.util.Map<String, String> settings) {
        return transformer(description, jar);
    }

    /**
     * 서버 종료 중 모든 플러그인의 onDisable 이 끝난 직후에 한 번 불린다(리로드·플러그인 하나 끄기에서는 안 불린다). 어댑터가 자기 형식의
     * 플러그인이 남긴 일(예: Bukkit 비동기 작업)을 마무리할 시간을 주는 자리 — 오래 붙잡지 말 것(종료가 그만큼 늦어진다).
     * 어댑터를 아직 만들지 않았으면(그 형식의 플러그인이 없었으면) 불리지 않는다.
     */
    default void afterPluginsDisabled(org.domi.furnace.api.Furnace server) {}

    /**
     * 개발 모드 핫 리로드에서 플러그인 몇 개를 끈 직후, 다시 읽기 전에 불린다. 어댑터가 그 플러그인들이 남긴 일(예: Bukkit 비동기 작업)이 끝나기를
     * 최대 {@code maxMillis} 기다리는 자리다 — 서버 종료의 {@link #afterPluginsDisabled} 와 같은 규칙을 리로드에도 적용한다.
     * 전역 스레드에서 불리므로 오래 붙잡지 말 것. 어댑터를 아직 만들지 않았으면 불리지 않는다.
     *
     * @param plugins 방금 끈(그리고 곧 다시 읽을) 플러그인
     */
    default void awaitPluginTasks(org.domi.furnace.api.Furnace server, java.util.Collection<FurnacePlugin> plugins, long maxMillis) {}
}
