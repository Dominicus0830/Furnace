// 바닐라 ScoreHolder — 점수의 주인. 플레이어는 접속 이름, 일반 엔티티는 UUID 문자열, 그 밖에는 임의 이름("#가짜" 포함).
package org.domi.furnace.server.scores;

import org.domi.furnace.api.text.HoverEvent;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;

public interface ScoreHolder {

    String WILDCARD_NAME = "*";

    /** 바닐라 getScoreboardName. */
    String scoreboardName();

    /** 바닐라 getDisplayName(엔티티면 그 표시 이름, 이름만 있으면 null). */
    default Text displayName() { return null; }

    /** 바닐라 getFeedbackDisplayName: 표시 이름에 점수판 이름 호버를 붙인 것(없으면 이름 글자). */
    default Text feedbackDisplayName() {
        Text d = displayName();
        return d != null ? d.hoverEvent(HoverEvent.showText(Text.text(scoreboardName()))) : Text.text(scoreboardName());
    }

    ScoreHolder WILDCARD = () -> WILDCARD_NAME;

    /** 바닐라 forNameOnly. */
    static ScoreHolder forNameOnly(String name) {
        if (name.equals(WILDCARD_NAME)) return WILDCARD;
        Text t = Text.text(name);
        return new ScoreHolder() {
            public String scoreboardName() { return name; }
            @Override public Text feedbackDisplayName() { return t; }
        };
    }

    /** 플레이어·엔티티의 점수판 이름(바닐라 Entity.getScoreboardName). */
    static String nameOf(Rider r) {
        return r instanceof Player p ? p.name() : ((Entity) r).uuid().toString();
    }

    /** 플레이어·엔티티를 점수 주인으로. */
    static ScoreHolder of(Rider r) {
        String name = nameOf(r);
        return new ScoreHolder() {
            public String scoreboardName() { return name; }
            @Override public Text displayName() { return r.displayName(); }
        };
    }
}
