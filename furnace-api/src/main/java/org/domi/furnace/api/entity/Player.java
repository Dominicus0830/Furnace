// 접속한 플레이어. 읽기는 어느 스레드에서나 안전하고(최근 값), 상태를 바꾸는 호출도 스레드 안전하다 — 서버가 소유 스레드로 넘긴다.
package org.domi.furnace.api.entity;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.command.CommandSender;
import org.domi.furnace.api.inventory.Inventory;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.Location;

public interface Player extends CommandSender, LivingEntity {

    @Override
    UUID uniqueId();

    /** 바닐라 Player.getDisplayName: 이름 + "/tell 이름" 제안 클릭 + 삽입. */
    Text displayName();

    /**
     * 플러그인이 정한 표시 이름(없으면 null). 정해 두면 채팅의 "&lt;이름&gt;" 이 팀 서식 이름 대신 이것으로 나간다(Paper·Spigot 의 setDisplayName).
     * 접속을 끊으면 사라진다.
     */
    default Text customDisplayName() { return null; }

    /** {@link #customDisplayName()} 을 정한다(null = 되돌림). */
    default void customDisplayName(Text name) { throw new UnsupportedOperationException("customDisplayName"); }

    boolean isOnline();

    /** 접속 때 받은 게임 프로필 속성(스킨 "textures" 등, 서명 포함). 오프라인 모드면 비어 있다. 아무 스레드. */
    default java.util.List<ProfileProperty> profileProperties() { return java.util.List.of(); }

    /** 마지막으로 알려진 위치. */
    @Override
    Location location();

    /** 이 플레이어가 있는 region 의 최근 5초 TPS(그 플레이어가 겪는 틱 속도). 잴 수 없으면 empty. 아무 스레드. */
    default java.util.OptionalDouble regionTps() { return org.domi.furnace.api.Furnace.server().regionTps(location()); }

    GameMode gameMode();

    /** 바닐라 setGameMode: 같으면 false. PlayerGameModeChangeEvent 를 거친다. */
    boolean setGameMode(GameMode mode);

    @Override
    float health();

    /** 0..최대 체력으로 잘린다. 0 이면 죽는다. */
    void setHealth(float health);

    int foodLevel();

    void setFoodLevel(int food);

    int level();

    void setLevel(int level);

    /** 경험치 점수를 더한다(바닐라 giveExperiencePoints). */
    void giveExperience(int points);

    boolean isDead();

    /** 클라이언트 언어(client_information). */
    Locale locale();

    /** 핑(ms, 탭 목록 값). */
    int ping();

    InetSocketAddress address();

    /** 사유를 보여 주고 연결을 끊는다. */
    void kick(Text reason);

    /** 이 플레이어의 연결(접속 단계 이벤트와 같은 객체·같은 id). */
    org.domi.furnace.api.net.PlayerConnection connection();

    /** 클라이언트가 minecraft:brand 로 알린 이름(예: "vanilla"), 아직 모르면 null. */
    String clientBrand();

    /**
     * 플러그인 채널로 바이트를 보낸다(play 단계 custom_payload). 채널은 "네임스페이스:이름" 소문자 형식, 본문은 최대 1048576바이트.
     * 어느 스레드에서 불러도 된다. 접속이 끊겼으면 아무 일도 없다.
     */
    void sendPluginMessage(String channel, byte[] data);

    /**
     * 이 플레이어의 채팅 창에서 그 서명의 메시지를 지운다(delete_chat — Paper Player.deleteMessage). 서명은 AsyncChatEvent.signature() 의 256바이트.
     * 바닐라 서버는 이 패킷을 쓰지 않는다. 클라이언트가 그 메시지를 받지 않았으면 아무 일도 없다.
     */
    void deleteChatMessage(byte[] signature);

    /**
     * 패킷을 보낸다(Furnace.server().packets().send 와 같다). 어느 스레드에서 불러도 된다. 접속이 끊겼으면 아무 일도 없다.
     * 서버 상태는 바뀌지 않는다 — 이 플레이어 화면에만 보인다.
     */
    default void sendPacket(org.domi.furnace.api.packet.ClientboundPacket packet) {
        org.domi.furnace.api.Furnace.server().packets().send(this, packet);
    }

    /** 클라이언트가 minecraft:register 로 듣겠다고 알린 채널(사본). 바닐라 클라이언트는 비어 있다. */
    java.util.Set<String> listeningPluginChannels();

    /** 이 플레이어로서 명령을 실행 큐에 넣는다(앞의 '/' 는 없어도 된다). 접수 여부를 돌려준다(결과는 나중에 정해진다). */
    boolean performCommand(String command);

    /**
     * 메뉴 화면을 연다(열린 다른 화면은 닫힌다). 어느 스레드에서 불러도 되며 연결 스레드에서 이뤄진다.
     * InventoryOpenEvent 가 취소되면 열리지 않는다.
     */
    default void openMenu(org.domi.furnace.api.menu.Menu menu) { throw new UnsupportedOperationException("메뉴를 열 수 없는 플레이어"); }

    /** 열린 메뉴 화면이 이 플레이어의 것이면 닫는다(다른 화면이면 건드리지 않는다). */
    default void closeMenu() {}

    /** 지금 보고 있는 메뉴, 없으면 null. */
    default org.domi.furnace.api.menu.Menu currentMenu() { return null; }

    /**
     * 이 사람에게 열린(또는 openMenu 로 여는 중인) 메뉴 창의 제목만 바꾼다 — Menu 의 제목과 같은 메뉴를 보는 다른 사람은 그대로이고,
     * 창을 닫으면 사라진다(다시 열면 Menu 제목). Paper InventoryView#setTitle 과 같다. 바꾼 동안에는 Menu#setTitle 보다 이 값이 이긴다.
     * 열린 메뉴가 없으면 아무것도 하지 않는다. 어느 스레드에서든.
     */
    default void retitleMenu(org.domi.furnace.api.text.Text title) {}

    /** 이 사람이 menu 를 열고 있으면(여는 중 포함) 그 사람 화면의 제목(retitleMenu 값, 없으면 Menu 제목), 아니면 null. */
    default org.domi.furnace.api.text.Text menuTitle(org.domi.furnace.api.menu.Menu menu) {
        return menu != null && menu == currentMenu() ? menu.title() : null;
    }

    /** 열려 있는 화면(상자·제작대·메뉴 등 어떤 창이든)을 닫는다 — 플레이어 인벤토리만 보이는 상태로. 연결 스레드에서 이뤄진다. */
    default void closeContainer() {}

    /**
     * 지금 열린 화면의 종류 키(InventorySnapshot#type 과 같은 이름 — minecraft:chest, minecraft:crafting …), 열린 화면이 없으면(플레이어
     * 인벤토리만) null. 읽는 순간의 값이다.
     */
    default String openContainerType() { return null; }

    /** 이 플레이어에게서 그 엔티티(플레이어 포함)를 숨긴다 — {@link EntityViews#hide}. */
    default void hideEntity(org.domi.furnace.api.plugin.FurnacePlugin plugin, Entity target) {
        org.domi.furnace.api.Furnace.server().entityViews().hide(plugin, this, target);
    }

    /** {@link EntityViews#show}. */
    default void showEntity(org.domi.furnace.api.plugin.FurnacePlugin plugin, Entity target) {
        org.domi.furnace.api.Furnace.server().entityViews().show(plugin, this, target);
    }

    /** 규칙상 그 엔티티를 볼 수 있는가 — {@link EntityViews#canSee}. */
    default boolean canSee(Entity target) { return org.domi.furnace.api.Furnace.server().entityViews().canSee(this, target); }

    /** 플레이어 인벤토리 핸들(비동기 접근). */
    Inventory inventory();

    /** 지금 고른 단축 칸(0..8). 어느 스레드에서든 읽을 수 있다. 구현이 없으면 UnsupportedOperationException. */
    default int heldSlot() { throw new UnsupportedOperationException("heldSlot"); }

    /**
     * 단축 칸 선택을 바꾼다(클라이언트에도 알린다). 소유 스레드에서 실행되고 그 스레드면 곧바로 끝난다.
     * 0..8 밖이면 IllegalArgumentException 으로 완료한다.
     */
    default CompletableFuture<Void> setHeldSlot(int slot) { return CompletableFuture.failedFuture(new UnsupportedOperationException("setHeldSlot")); }

    @Override
    default String type() { return "minecraft:player"; }

    @Override
    default Text customName() { return null; }

    @Override
    default boolean isValid() { return isOnline(); }

    /** 플레이어 핸들은 항상 최신 값을 읽으므로 자기 자신. */
    @Override
    default CompletableFuture<Player> refresh() { return CompletableFuture.completedFuture(this); }

    @Override
    default CompletableFuture<Void> updateHealth(float health) {
        setHealth(health);
        return CompletableFuture.completedFuture(null);
    }

    /** 플레이어 인벤토리. */
    @Override
    default CompletableFuture<Optional<Inventory>> container() { return CompletableFuture.completedFuture(Optional.of(inventory())); }

    @Override
    default CompletableFuture<Boolean> remove() {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("플레이어는 remove 할 수 없다 — kick 을 쓴다"));
    }

    // ------------------------------------------------------------------ 레시피 북

    /**
     * 레시피 키(minecraft:oak_planks 처럼 레지스트리에 있는 것)를 레시피 북에 배우게 한다(/recipe give 와 같다). 모르는 키·특수 레시피는 건너뛴다.
     * 새로 배운 수로 완료. 플레이어를 가진 region 스레드에서 실행된다.
     */
    default CompletableFuture<Integer> discoverRecipes(java.util.Collection<String> keys) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("discoverRecipes"));
    }

    /** 레시피 북에서 잊게 한다(/recipe take 와 같다). 잊은 수로 완료. */
    default CompletableFuture<Integer> undiscoverRecipes(java.util.Collection<String> keys) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("undiscoverRecipes"));
    }

    /** 지금 레시피 북에 있는가(최근 값). */
    default boolean hasDiscoveredRecipe(String key) { return false; }
}
