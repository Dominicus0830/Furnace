// 플레이어에게 들려줄 소리 — 소리 이름(리소스 id)·분류·크기·높이. 분류는 클라이언트 음량 슬라이더가 따르는 묶음.
package org.domi.furnace.api.audience;

import java.util.Objects;
import java.util.regex.Pattern;

public record Sound(String key, Source source, float volume, float pitch) {

    private static final Pattern KEY = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");

    /** 바닐라 SoundSource(선언 순서 = 네트워크 번호). */
    public enum Source { MASTER, MUSIC, RECORD, WEATHER, BLOCK, HOSTILE, NEUTRAL, PLAYER, AMBIENT, VOICE }

    public Sound {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(source, "source");
        if (!isValidKey(key)) throw new IllegalArgumentException("잘못된 소리 이름: " + key);
        if (!(volume >= 0.0F) || Float.isInfinite(volume)) throw new IllegalArgumentException("크기는 0 이상: " + volume);
        if (!(pitch >= 0.0F && pitch <= 2.0F)) throw new IllegalArgumentException("높이는 0~2: " + pitch);
    }

    /** 바닐라 클라이언트가 소리 이름을 읽는 길이 상한(readUtf(32767), 생략한 minecraft: 를 붙인 뒤). */
    private static final int MAX_KEY_LENGTH = 32767;

    /**
     * 리소스 id 로 쓸 수 있는 이름인가(바닐라 ResourceLocation 규칙: 소문자·숫자·_ . - 와 경로의 /, 이름공간은 생략 가능).
     * 생략한 이름공간 minecraft: 을 붙여 32767 자를 넘으면 안 된다(넘으면 클라이언트가 접속을 끊는다).
     */
    public static boolean isValidKey(String key) {
        if (key == null) return false;
        int len = key.length() + (key.indexOf(':') < 0 ? "minecraft:".length() : 0);
        return len <= MAX_KEY_LENGTH && KEY.matcher(key).matches();
    }

    /** 크기 1, 높이 1. */
    public static Sound sound(String key, Source source) { return new Sound(key, source, 1.0F, 1.0F); }

    public static Sound sound(String key, Source source, float volume, float pitch) { return new Sound(key, source, volume, pitch); }
}
