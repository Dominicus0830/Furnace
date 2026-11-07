// 플레이어별 화면 덮어쓰기 — Furnace.server().playerViews(). 서버 상태(진짜 블록·바이옴·장비·보더)는 그대로 두고 한 사람에게 가는 패킷만 바꾼다.
// 가상 블록·바이옴은 "층"이다: Bukkit sendBlockChange 와 달리 청크를 다시 받거나(재접속·시야 밖에 갔다 옴·차원 이동) 진짜 블록이 바뀌어도
// 층이 지울 때까지 유지된다(서버가 보내는 청크·블록 변경 패킷을 보내는 순간에 층을 덧입힌다).
// 층은 그 사람이 접속해 있는 동안만 살고(다시 접속하면 없다), 건 플러그인 이름으로 남아 플러그인이 꺼지면 함께 거둔다.
// 모든 메서드는 아무 스레드에서 불러도 된다. 화면에는 바로(청크 전송 순서 안에서) 반영된다.
package org.domi.furnace.api.view;

import java.util.Map;
import java.util.Optional;
import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.entity.EquipmentSlot;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.World;

public interface PlayerViews {

    /** {@link #fillBlocks} 한 번에 덮을 수 있는 칸 수(메모리 보호). */
    int MAX_FILL = 65_536;

    // ------------------------------------------------------------------ 가상 블록

    /**
     * viewer 에게만 pos 를 data 로 보인다. 같은 칸에 다른 플러그인의 층이 있으면 이 플러그인 것으로 바뀐다.
     * 서버의 진짜 블록·충돌·물리는 그대로다 — 클라이언트는 층 모양으로 충돌을 계산하므로 서버와 어긋나면 되돌려질 수 있다(진짜가 공기인 곳에 층으로 만든 바닥을 밟는 등은 서버가 낙하·이동 검증으로 거절할 수 있다).
     * 알 수 없는 블록·속성이면 IllegalArgumentException, 높이 밖이면 무시.
     */
    void setBlock(FurnacePlugin plugin, Player viewer, World world, BlockPos pos, BlockData data);

    /** 여러 칸을 한 번에(칸이 많으면 이쪽이 싸다). */
    void setBlocks(FurnacePlugin plugin, Player viewer, World world, Map<BlockPos, BlockData> blocks);

    /** 두 모서리(포함) 사이의 모든 칸을 data 로. {@link #MAX_FILL} 개를 넘으면 IllegalArgumentException. */
    void fillBlocks(FurnacePlugin plugin, Player viewer, World world, BlockPos min, BlockPos max, BlockData data);

    /** 그 칸의 층을 걷고 진짜 블록을 다시 보낸다(이 플러그인이 건 층만). */
    void clearBlock(FurnacePlugin plugin, Player viewer, World world, BlockPos pos);

    /** 이 플러그인이 viewer 의 world 에 건 블록 층을 모두 걷는다. */
    void clearBlocks(FurnacePlugin plugin, Player viewer, World world);

    /** 이 플러그인이 viewer 에게 건 블록·바이옴 층을 모든 월드에서 걷는다. */
    void clearLayers(FurnacePlugin plugin, Player viewer);

    /** viewer 가 그 칸에서 보는 가상 블록(층이 없으면 비어 있음). */
    Optional<BlockData> virtualBlock(Player viewer, World world, BlockPos pos);

    // ------------------------------------------------------------------ 가상 바이옴

    /**
     * viewer 에게만 pos 가 든 바이옴 칸(4×4×4)의 바이옴을 biome 으로 보인다(풀·물 색, 하늘·안개, 소리, 날씨 입자가 클라이언트 바이옴을 따른다).
     * biome 은 "minecraft:desert" 처럼 서버 레지스트리에 있는 이름(플러그인 바이옴 포함). 모르면 IllegalArgumentException.
     */
    void setBiome(FurnacePlugin plugin, Player viewer, World world, BlockPos pos, String biome);

    /** 두 모서리(포함)를 덮는 모든 바이옴 칸을. 칸 수가 {@link #MAX_FILL}/64 를 넘으면 IllegalArgumentException. */
    void fillBiome(FurnacePlugin plugin, Player viewer, World world, BlockPos min, BlockPos max, String biome);

    /** 그 칸의 바이옴 층을 걷는다(이 플러그인 것만). */
    void clearBiome(FurnacePlugin plugin, Player viewer, World world, BlockPos pos);

    /** viewer 가 그 칸에서 보는 가상 바이옴 이름. */
    Optional<String> virtualBiome(Player viewer, World world, BlockPos pos);

    // ------------------------------------------------------------------ 월드 보더

    /**
     * viewer 만의 보더. 처음 부르면 서버 보더의 지금 값으로 시작해 viewer 에게 보내고, 그 뒤 값을 바꿀 때마다 viewer 에게 간다.
     * 이 사람에게는 서버의 보더 변경(명령·플러그인)이 가지 않는다. 다른 플러그인이 이미 만들었으면 같은 보더를 돌려주고 주인이 이 플러그인으로 바뀐다.
     */
    PlayerBorder border(FurnacePlugin plugin, Player viewer);

    /** viewer 에게 만든 보더가 있는가. */
    boolean hasBorder(Player viewer);

    /** 만든 보더를 걷고 서버의 보더를 다시 보낸다. */
    void resetBorder(Player viewer);

    // ------------------------------------------------------------------ 장비 표시

    /**
     * viewer 화면에서 target 의 장비 칸을 item 으로 보인다(빈 스택이면 비어 보인다). target 의 진짜 장비·방어력·손에 든 것은 그대로다.
     * 진짜 장비가 바뀌어도 덮어쓴 칸은 유지된다. 자기 자신의 장비(viewer == target)는 클라이언트가 인벤토리로 그리므로 바뀌지 않는다.
     * 알 수 없는 아이템이면 IllegalArgumentException. 몸 칸(말 갑옷 등)은 다루지 않는다.
     */
    void setEquipment(FurnacePlugin plugin, Player viewer, Entity target, EquipmentSlot slot, ItemStack item);

    /** 그 칸의 덮어쓰기를 걷고 진짜 장비를 다시 보낸다(이 플러그인 것만). */
    void clearEquipment(FurnacePlugin plugin, Player viewer, Entity target, EquipmentSlot slot);

    /** target 의 모든 칸에서 이 플러그인의 덮어쓰기를 걷는다. */
    void clearEquipment(FurnacePlugin plugin, Player viewer, Entity target);

    /** viewer 가 target 의 그 칸에서 보는 덮어쓴 아이템(없으면 비어 있음). */
    Optional<ItemStack> virtualEquipment(Player viewer, Entity target, EquipmentSlot slot);
}
