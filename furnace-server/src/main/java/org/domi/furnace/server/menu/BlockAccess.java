// 블록에 매인 메뉴가 블록(제작대·석재 절단기 등)에 바라는 것 — 블록을 가진 region 스레드에서만 부른다.
package org.domi.furnace.server.menu;

public interface BlockAccess {

    /** 그 좌표가 아직 그 블록인가. */
    boolean stillBlock();

    /** Level.getGameTime — 같은 틱에 소리를 겹쳐 내지 않으려는 용도. */
    long gameTime();

    /** 블록 가운데에서 소리(분류 blocks, 크기 1, 높이 1)를 낸다. */
    void playSound(String sound);
}
