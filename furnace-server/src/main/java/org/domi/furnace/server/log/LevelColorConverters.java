// logback %fcolor … %freset — 터미널일 때 WARN 줄은 노랑, ERROR 줄은 빨강으로 감싼다(Paper 콘솔의 highlightError 와 같다).
// 색을 쓸지는 AnsiMessageConverter 와 같은 판단(대화형 터미널, NO_COLOR·-Dfurnace.console.colors=false 로 끔).
// (logback 의 %name(...) 묶음 변환기는 바로 뒤의 %n 을 글자 그대로 찍어 버려서, 시작·끝 코드를 따로 내는 두 변환기로 나눴다.)
package org.domi.furnace.server.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public final class LevelColorConverters {

    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String RESET = "\u001B[0m";

    private LevelColorConverters() {}

    private static String colorCode(ILoggingEvent event) {
        if (!AnsiMessageConverter.colorsEnabled()) return "";
        int level = event.getLevel().toInt();
        if (level >= Level.ERROR_INT) return RED;
        return level == Level.WARN_INT ? YELLOW : "";
    }

    /** 줄 맨 앞: 레벨 색 시작 코드(INFO 이하·색 끔이면 빈 글자). */
    public static final class Start extends ClassicConverter {
        @Override
        public String convert(ILoggingEvent event) { return colorCode(event); }
    }

    /** 줄 맨 끝(개행 앞): 색을 썼으면 되돌림 코드. */
    public static final class End extends ClassicConverter {
        @Override
        public String convert(ILoggingEvent event) { return colorCode(event).isEmpty() ? "" : RESET; }
    }
}
