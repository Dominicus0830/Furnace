package org.domi.furnace.api.plugin;

/**
 * 플러그인 jar 의 클래스 바이트를 정의하기 직전에 바꾼다({@link PluginFormat#transformer}). 다른 서버 소프트웨어용으로 컴파일된 플러그인이
 * 부르는 이름을 이 서버의 이름으로 바꾸는 데 쓴다(예: Bukkit 호환 계층의 CraftBukkit 버전 접미사 풀기).
 * 여러 스레드가 동시에 부를 수 있다(플러그인 클래스로더는 병렬 로딩).
 */
@FunctionalInterface
public interface ClassTransformer {

    /**
     * @param className 정의할 클래스의 이진 이름(점 구분, 예: com.foo.Bar$Inner)
     * @param bytes     jar 에 있는 클래스 파일 바이트(바꾸지 말 것)
     * @return 바꾼 바이트, 바꿀 것이 없으면 null
     */
    byte[] transform(String className, byte[] bytes);
}
