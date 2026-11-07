// 콘솔 출력 길목. JLine 줄 편집기가 켜져 있으면 로그 줄을 입력 줄 위에 찍어(printAbove) 치던 글자가 로그에 섞이지 않게 하고,
// 꺼져 있으면(터미널이 아니거나 console.jline=false) 그냥 표준 출력으로 보낸다.
package org.domi.furnace.server.log;

import java.util.function.Consumer;

public final class ConsoleBridge {

    private ConsoleBridge() {}

    /** 편집기가 켜져 있으면 여기로 줄을 보낸다(줄 끝 개행 없이). */
    private static volatile Consumer<String> sink;

    public static void install(Consumer<String> s) { sink = s; }

    public static void uninstall() { sink = null; }

    /** 로그 한 건(여러 줄일 수 있다, 끝 개행 포함 가능)을 콘솔에 낸다. */
    public static void print(String text) {
        Consumer<String> s = sink;
        if (s == null) {
            System.out.print(text);
            System.out.flush();
            return;
        }
        int end = text.length();
        while (end > 0 && (text.charAt(end - 1) == '\n' || text.charAt(end - 1) == '\r')) end--;
        s.accept(text.substring(0, end));
    }
}
