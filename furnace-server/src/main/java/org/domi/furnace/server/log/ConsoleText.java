// 로그에 텍스트를 넘길 때 쓰는 감싸개 — 파일 로그는 toString()(평문), 콘솔은 AnsiMessageConverter 가 색을 입힌다(Paper 콘솔처럼).
package org.domi.furnace.server.log;

import org.domi.furnace.api.text.Text;

public final class ConsoleText {

    private final Text text;
    private String plain;

    public ConsoleText(Text text) { this.text = text; }

    public Text text() { return text; }

    @Override
    public String toString() {
        String s = plain;
        if (s == null) plain = s = text.getString();
        return s;
    }
}
