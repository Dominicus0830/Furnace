package net.minecraft.network.chat;

import java.util.Objects;

/**
 * 글을 눌렀을 때 하는 일(Mojang 이름 shim, 불변). 값은 Furnace 클릭 이벤트({@link org.domi.furnace.api.text.ClickEvent})로 옮겨 가며,
 * {@link Style#withClickEvent} 가 그 길이다. 바닐라의 CODEC 상수는 없다(호출 위치에서 실패).
 */
class ClickEvent {

    private final Action action;
    private final String value;

    public ClickEvent(Action action, String value) {
        this.action = action;
        this.value = value;
    }

    public Action getAction() { return action; }

    public String getValue() { return value; }

    /** 호환 모듈 전용. */
    org.domi.furnace.api.text.ClickEvent furnace() {
        return new org.domi.furnace.api.text.ClickEvent(org.domi.furnace.api.text.ClickEvent.Action.byKey(action.getSerializedName()), value);
    }

    /** 호환 모듈 전용. */
    static ClickEvent fromFurnace(org.domi.furnace.api.text.ClickEvent e) {
        return e == null ? null : new ClickEvent(Action.byName(e.action().key()), e.value());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClickEvent c = (ClickEvent) o;
        return action == c.action && Objects.equals(value, c.value);
    }

    @Override
    public String toString() { return "ClickEvent{action=" + action + ", value='" + value + "'}"; }

    @Override
    public int hashCode() {
        int r = action.hashCode();
        return 31 * r + (value != null ? value.hashCode() : 0);
    }

    /** 바닐라 1.21.1 순서·이름·"서버가 보내도 되는가". */
    public enum Action {
        OPEN_URL("open_url", true),
        OPEN_FILE("open_file", false),
        RUN_COMMAND("run_command", true),
        SUGGEST_COMMAND("suggest_command", true),
        CHANGE_PAGE("change_page", true),
        COPY_TO_CLIPBOARD("copy_to_clipboard", true);

        private final boolean allowFromServer;
        private final String name;

        Action(String name, boolean allowFromServer) {
            this.name = name;
            this.allowFromServer = allowFromServer;
        }

        public boolean isAllowedFromServer() { return allowFromServer; }

        public String getSerializedName() { return name; }

        static Action byName(String name) {
            for (Action a : values()) if (a.name.equals(name)) return a;
            throw new IllegalArgumentException("Unknown click action: " + name);
        }
    }
}
