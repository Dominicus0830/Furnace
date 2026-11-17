// 임시 월드 다리(T-203). 호환 계층의 NMS ServerLevel 생성자(FAWE //regen 이 쓰는 Paper 의 따로 떨어진 레벨)가 쓴다 — 불안정 다리.
package org.domi.furnace.api.internal;

import org.domi.furnace.api.world.World;

public interface ScratchWorlds {

    /**
     * original 과 같은 차원 타입·생성 설정(노이즈 설정·고정 바이옴·평지 층·구조물 켬/끔·플러그인 월드젠 훅)에 시드만 seed 인 임시 월드를 만든다.
     * 생성기는 처음 상태라 그 월드의 청크는 그 시드의 새 월드에서 처음 만드는 청크와 같다. 게임 틱이 돌지 않고, 저장하지 않으며, 월드 목록·명령·포털·
     * 플러그인 이벤트에 나오지 않는다. 청크는 보통 월드처럼 불러온다(불러오면 생성). 다 쓰면 {@link #close}. 아무 스레드.
     *
     * @param name 월드 이름({@link World#name()})
     * @throws UnsupportedOperationException 복사할 수 없는 생성기
     */
    World create(World original, long seed, String name);

    /** 월드 생성기 종류: "noise"(노이즈), "flat"(평지·빈 월드), "debug"(모든 상태 격자), 그 밖은 "other". 아무 스레드. */
    String generatorKind(World world);

    /** 서버 기본 게임 모드 이름(server.properties gamemode: "survival" 등) — 임시 월드의 레벨 설정에 옮긴다. 아무 스레드. */
    String defaultGameType();

    /** 이 다리로 만든 임시 월드인가. */
    boolean isScratch(World world);

    /** 임시 월드를 버린다(청크를 저장하지 않는다). 아무 스레드, 두 번 불러도 된다. 임시 월드가 아니면 IllegalArgumentException. */
    void close(World scratch);
}
