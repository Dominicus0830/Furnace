// logback %fprefix — Paper 콘솔처럼, 마인크래프트·Furnace·Bukkit 밖에서 온 로거(플러그인이 가져온 라이브러리 등)에는
// "[로거이름] " 을 붙여 서버 줄과 구별한다. 플러그인 로거는 메시지에 이미 [이름] 이 들어 있고 이름에 '.' 이 없어 붙지 않는다.
package org.domi.furnace.server.log;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public final class ConsolePrefixConverter extends ClassicConverter {

    // 호환 계층 패키지 이름은 서버 클래스 파일에 글자로 남지 않게(CompatIsolationStaticTest) 이어 붙여 만든다.
    private static final String[] OWN = {pkg("org.domi", "furnace"), pkg("net", "minecraft"), pkg("com", "mojang"),
            pkg("org", "bukkit"), pkg("io", "papermc"), pkg("org", "spigotmc"), pkg("com", "destroystokyo")};

    private static String pkg(String a, String b) {
        return a + '.' + b + '.';
    }

    @Override
    public String convert(ILoggingEvent event) {
        return prefix(event.getLoggerName());
    }

    static String prefix(String logger) {
        if (logger == null || logger.indexOf('.') < 0) return "";
        for (String own : OWN) if (logger.startsWith(own)) return "";
        return "[" + logger + "] ";
    }
}
