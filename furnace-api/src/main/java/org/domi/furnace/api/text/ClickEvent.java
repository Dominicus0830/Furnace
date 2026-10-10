// 클릭 이벤트(바닐라 ClickEvent, 1.21.1: action + 문자열 value).
package org.domi.furnace.api.text;

import java.util.Objects;

public record ClickEvent(Action action, String value) {

    public enum Action {
        OPEN_URL("open_url"), OPEN_FILE("open_file"), RUN_COMMAND("run_command"), SUGGEST_COMMAND("suggest_command"),
        CHANGE_PAGE("change_page"), COPY_TO_CLIPBOARD("copy_to_clipboard");

        private final String key;

        Action(String key) { this.key = key; }

        public String key() { return key; }

        public static Action byKey(String key) {
            for (Action a : values()) if (a.key.equals(key)) return a;
            return null;
        }
    }

    public ClickEvent {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(value, "value");
    }

    public static ClickEvent openUrl(String url) { return new ClickEvent(Action.OPEN_URL, url); }

    public static ClickEvent openFile(String path) { return new ClickEvent(Action.OPEN_FILE, path); }

    public static ClickEvent runCommand(String command) { return new ClickEvent(Action.RUN_COMMAND, command); }

    public static ClickEvent suggestCommand(String command) { return new ClickEvent(Action.SUGGEST_COMMAND, command); }

    public static ClickEvent changePage(int page) { return new ClickEvent(Action.CHANGE_PAGE, Integer.toString(page)); }

    public static ClickEvent copyToClipboard(String text) { return new ClickEvent(Action.COPY_TO_CLIPBOARD, text); }
}
