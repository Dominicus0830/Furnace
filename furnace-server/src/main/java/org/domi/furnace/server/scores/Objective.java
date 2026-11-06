// 바닐라 Objective — 이름·기준·표시 이름·표시 방식·자동 표시 이름 갱신·기본 숫자 형식. 바꾸면 점수판에 알린다.
package org.domi.furnace.server.scores;

import org.domi.furnace.api.text.HoverEvent;
import org.domi.furnace.api.text.Text;

public final class Objective {

    private final Scoreboard scoreboard;
    private final String name;
    private final ObjectiveCriteria criteria;
    private Text displayName;
    private Text formattedDisplayName;
    private ObjectiveCriteria.RenderType renderType;
    private boolean displayAutoUpdate;
    private NumberFormat numberFormat;

    Objective(Scoreboard scoreboard, String name, ObjectiveCriteria criteria, Text displayName, ObjectiveCriteria.RenderType renderType,
              boolean displayAutoUpdate, NumberFormat numberFormat) {
        this.scoreboard = scoreboard;
        this.name = name;
        this.criteria = criteria;
        this.displayName = displayName;
        this.formattedDisplayName = createFormattedDisplayName();
        this.renderType = renderType;
        this.displayAutoUpdate = displayAutoUpdate;
        this.numberFormat = numberFormat;
    }

    public Scoreboard scoreboard() { return scoreboard; }

    public String name() { return name; }

    public ObjectiveCriteria criteria() { return criteria; }

    public Text displayName() { return displayName; }

    public boolean displayAutoUpdate() { return displayAutoUpdate; }

    public NumberFormat numberFormat() { return numberFormat; }

    public NumberFormat numberFormatOrDefault(NumberFormat fallback) { return numberFormat != null ? numberFormat : fallback; }

    /** 바닐라 createFormattedDisplayName: [표시 이름] 에 이름 호버. */
    private Text createFormattedDisplayName() {
        return Text.translatable("chat.square_brackets", displayName.hoverEvent(HoverEvent.showText(Text.text(name))));
    }

    /** 바닐라 getFormattedDisplayName. */
    public Text formattedDisplayName() { return formattedDisplayName; }

    public void displayName(Text name) {
        synchronized (scoreboard) {
            this.displayName = name;
            this.formattedDisplayName = createFormattedDisplayName();
            scoreboard.onObjectiveChanged(this);
        }
    }

    public ObjectiveCriteria.RenderType renderType() { return renderType; }

    public void renderType(ObjectiveCriteria.RenderType type) {
        synchronized (scoreboard) {
            this.renderType = type;
            scoreboard.onObjectiveChanged(this);
        }
    }

    public void displayAutoUpdate(boolean v) {
        synchronized (scoreboard) {
            this.displayAutoUpdate = v;
            scoreboard.onObjectiveChanged(this);
        }
    }

    public void numberFormat(NumberFormat f) {
        synchronized (scoreboard) {
            this.numberFormat = f;
            scoreboard.onObjectiveChanged(this);
        }
    }
}
