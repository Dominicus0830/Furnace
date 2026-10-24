// 블록에 매인 메뉴가 블록(제작대·석재 절단기 등)에 바라는 것 — 블록을 가진 region 스레드에서만 부른다.
package org.domi.furnace.server.menu;

public interface BlockAccess {

    /** 그 좌표가 아직 그 블록인가. */
    boolean stillBlock();

    /** Level.getGameTime — 같은 틱에 소리를 겹쳐 내지 않으려는 용도. */
    long gameTime();

    /** 블록 가운데에서 소리(분류 blocks, 크기 1, 높이 1)를 낸다. */
    void playSound(String sound);

    /** 블록 가운데에서 level event(예: 1042 숫돌 사용음) — 보낸 플레이어도 받는다. */
    default void levelEvent(int type) { }

    /** Level.random.nextInt(bound). */
    default int nextInt(int bound) { return 0; }

    /** 블록 가운데에 경험치 구슬(ExperienceOrb.award). TODO(엔티티): 구슬 엔티티가 생기면 실제로 만든다. */
    default void awardExperience(int amount) { }

    /** 지도 저장 데이터의 잠금·배율. */
    record MapInfo(boolean locked, int scale) { }

    /** MapItem.getSavedData(stack, level): 그 지도의 저장 데이터(없으면 null). TODO(지도): 지도 저장 데이터가 생기면 연결. */
    default MapInfo mapInfo(org.domi.furnace.server.entity.ItemStack map) { return null; }

    /** 모루를 쓴 뒤: 창의 모루가 12% 확률로 손상된다(만들기 모드면 소리만). */
    default void anvilUsed(boolean infiniteMaterials) { }

    /** 마법 부여대 주변의 유효한 책장 수(EnchantingTableBlock.isValidBookShelf 를 센 값). */
    default int enchantingPower() { return 0; }

    /** Level.random.nextFloat(). */
    default float nextFloat() { return 0.5F; }

    /** 소리(크기·높이 지정). */
    default void playSound(String sound, float volume, float pitch) { playSound(sound); }
}
