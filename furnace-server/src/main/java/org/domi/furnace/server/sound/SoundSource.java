// 바닐라 SoundSource — 선언 순서가 네트워크 번호, key 가 명령 리터럴.
package org.domi.furnace.server.sound;

public enum SoundSource {
    MASTER("master"), MUSIC("music"), RECORDS("record"), WEATHER("weather"), BLOCKS("block"),
    HOSTILE("hostile"), NEUTRAL("neutral"), PLAYERS("player"), AMBIENT("ambient"), VOICE("voice");

    private final String key;

    SoundSource(String key) { this.key = key; }

    public String key() { return key; }
}
