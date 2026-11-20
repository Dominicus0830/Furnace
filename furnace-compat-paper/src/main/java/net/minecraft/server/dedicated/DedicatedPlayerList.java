package net.minecraft.server.dedicated;

import net.minecraft.server.players.PlayerList;

/** 전용 서버의 접속자 목록 shim(Mojang 이름). 상태 없는 보기라 하나뿐이다. */
public class DedicatedPlayerList extends PlayerList {

    /** 호환 모듈 전용. */
    static final DedicatedPlayerList INSTANCE = new DedicatedPlayerList();

    private DedicatedPlayerList() {}
}
