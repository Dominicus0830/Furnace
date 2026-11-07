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
}
