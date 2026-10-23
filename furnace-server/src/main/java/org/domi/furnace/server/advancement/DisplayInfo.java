// 바닐라 DisplayInfo — 아이콘·제목·설명·배경·틀(task/challenge/goal)·토스트·채팅 알림·숨김, 배치 좌표(x = 깊이, y).
package org.domi.furnace.server.advancement;

import com.google.gson.JsonElement;

public final class DisplayInfo {

    public enum Frame {
        TASK("task", "green"), CHALLENGE("challenge", "dark_purple"), GOAL("goal", "green");

        final String name;
        final String color;

        Frame(String name, String color) {
            this.name = name;
            this.color = color;
        }
    }

    final String iconItem;
    final int iconCount;
    final java.util.Map<String, Object> iconComponents;
    final JsonElement title;
    final JsonElement description;
    final String background;
    final Frame frame;
    final boolean showToast;
    final boolean announceChat;
    final boolean hidden;
    float x;
    float y;

    DisplayInfo(String iconItem, int iconCount, java.util.Map<String, Object> iconComponents, JsonElement title, JsonElement description,
                String background, Frame frame, boolean showToast, boolean announceChat, boolean hidden) {
        this.iconItem = iconItem;
        this.iconCount = iconCount;
        this.iconComponents = iconComponents;
        this.title = title;
        this.description = description;
        this.background = background;
        this.frame = frame;
        this.showToast = showToast;
        this.announceChat = announceChat;
        this.hidden = hidden;
    }

    public Frame frame() { return frame; }

    public boolean hidden() { return hidden; }

    public boolean announceChat() { return announceChat; }

    public JsonElement title() { return title; }

    public JsonElement description() { return description; }

    public float x() { return x; }

    public float y() { return y; }
}
