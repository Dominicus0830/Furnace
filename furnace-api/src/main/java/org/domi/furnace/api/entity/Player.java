// 접속한 플레이어. 읽기는 어느 스레드에서나 안전하며(최근 값), 상태를 바꾸는 호출도 스레드 안전하다. 서버가 소유 스레드로 넘겨 준다.
package org.domi.furnace.api.entity;

import java.net.InetSocketAddress;
import java.util.List;
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

    /** 게임 프로필 속성(세션 서버가 준 스킨 "textures" 등. 오프라인 서버면 빈 목록). 탭 목록 패킷의 ADD_PLAYER 와 같은 값이다. */
    default java.util.List<ProfileProperty> profileProperties() { return java.util.List.of(); }

    /**
     * 지금의 게임 프로필(Paper {@code getPlayerProfile}·{@code getGameProfile}).
     *
     * <p>이름과 속성은 {@link #name()}·{@link #profileProperties()} 와 같다.
     * <p>id 는 엔티티 UUID({@link #uniqueId()}, 파일·접속자 조회·탭 UUID 칸에 쓰인다)와 다를 수 있다. op·화이트리스트·밴은 이 id 로 본다.
     */
    default ProfileData profile() { return new ProfileData(uniqueId(), name(), profileProperties()); }

    /**
     * 게임 프로필(id·이름·속성)을 바꾼다(Paper {@code setPlayerProfile}).
     *
     * <p>{@link #uniqueId()}(엔티티 UUID)는 그대로다. {@link #name()}·{@link #profileProperties()}·{@link #profile()} 과 탭 목록의 이름·스킨이 새 값이 된다.
     * <p>다른 플레이어에게는 탭과 엔티티를 한 번 지웠다가 다시 올린다. 본인 화면은 같은 차원으로 리스폰해 갱신한다(Paper 와 같은 패킷).
     * id 를 엔티티 UUID 와 다르게 주면, Paper 처럼 그때 접속해 있던 사람의 화면에서 이 플레이어 엔티티가 보이지 않을 수 있다.
     * <p>접속 처리 중(PlayerJoinEvent 안)에 부르면 프로필만 바꾸고, 탭은 처음부터 새 프로필로 나간다.
     * <p>소유 스레드나 전역 동기 작업에서 부르면 바로 적용된다. 다른 스레드에서 부르면 이 플레이어의 다음 틱에 적용되므로, 그 직후 {@link #name()} 은 옛 값일 수 있다.
     * <p>접속자 이름 조회는 새 이름으로 찾는다. Paper 는 옛 이름으로 찾는데, 이것은 의도한 차이다.
     */
    default void profile(ProfileData profile) { throw new UnsupportedOperationException("profile"); }

    /** 바닐라 Player.getDisplayName. 이름에 "/tell 이름" 제안 클릭과 삽입이 붙은 값이다. */
    Text displayName();

    /**
     * 플러그인이 정한 표시 이름(없으면 null). 정해 두면 채팅의 "&lt;이름&gt;" 에 팀 서식 이름 대신 이 이름이 나간다(Paper·Spigot 의 setDisplayName).
     * 접속을 끊으면 사라진다.
     */
    default Text customDisplayName() { return null; }

    /** {@link #customDisplayName()} 을 정한다. null 이면 되돌린다. */
    default void customDisplayName(Text name) { throw new UnsupportedOperationException("customDisplayName"); }

    /**
     * 탭 목록에 보이는 이름(바닐라 ServerPlayer.listName, Paper {@code playerListName}). 정하지 않았으면 null(탭에는 프로필 이름이 나간다).
     * 채팅 표시 이름({@link #customDisplayName()})과 따로 간다.
     */
    default Text listName() { return null; }

    /**
     * {@link #listName()} 을 정한다(null 이면 기본 이름으로 되돌린다). 이 플레이어를 볼 수 있는 모든 접속자의 탭에 이름 갱신 패킷
     * (PlayerInfoUpdate UPDATE_DISPLAY_NAME)이 나간다. 접속 중이 아니어도 값은 남는다.
     * <p>아무 스레드에서 부를 수 있다. 패킷은 각 관찰자의 다음 틱에 나간다(Paper 는 부른 자리에서 바로 보낸다).
     */
    default void listName(Text name) { throw new UnsupportedOperationException("listName"); }

    boolean isOnline();

    /** 마지막으로 알려진 위치. */
    @Override
    Location location();

    /** 이 플레이어가 있는 region 의 최근 5초 TPS(그 플레이어가 겪는 틱 속도). 잴 수 없으면 empty. 아무 스레드에서나 부른다. */
    default java.util.OptionalDouble regionTps() { return org.domi.furnace.api.Furnace.server().regionTps(location()); }

    GameMode gameMode();

    /** 웅크리기 키를 누르고 있는가(바닐라 isShiftKeyDown). 클라이언트 player_command 로 바뀌며 자세 CROUCHING 과는 다르다. 아무 스레드. */
    default boolean isSneaking() { return false; }

    /** 달리는 중인가(바닐라 isSprinting). 클라이언트 player_command 로 바뀐다. 아무 스레드. */
    default boolean isSprinting() { return false; }

    /** 바닐라 setGameMode. 이미 같은 모드면 false 다. PlayerGameModeChangeEvent 를 거친다. */
    boolean setGameMode(GameMode mode);

    /** 비행 허가(바닐라 Abilities.mayfly). 게임 모드를 바꾸면 그 모드의 값으로 돌아간다. */
    default boolean allowFlight() { throw new UnsupportedOperationException("allowFlight"); }

    /** 비행 허가를 바꾼다. false 면 날고 있던 것도 멈춘다. */
    default void setAllowFlight(boolean allow) { throw new UnsupportedOperationException("setAllowFlight"); }

    /** 지금 날고 있나(바닐라 Abilities.flying). */
    default boolean isFlying() { throw new UnsupportedOperationException("isFlying"); }

    /** 비행을 켜거나 끈다. 허가 없이 true 면 IllegalArgumentException. */
    default void setFlying(boolean flying) { throw new UnsupportedOperationException("setFlying"); }

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

    /** 클라이언트가 알린 렌더 거리(client_information, 2~32). 아직 모르면 32 다. */
    default int clientViewDistance() { throw new UnsupportedOperationException("clientViewDistance"); }

    /**
     * 이 플레이어의 시야 거리. Paper 1.21.1 Player#getViewDistance 와 같은 값으로, 불러오는 거리 − 2 다.
     * Paper 와 같으므로 기본은 서버 시야 거리 − 1 이다.
     *
     * <p>플러그인 값이 없으면 서버 값에서 나온다. 거리 규칙은 docs/api/view-distance.md.
     */
    default int viewDistance() { throw new UnsupportedOperationException("viewDistance"); }

    /**
     * 이 플레이어만의 시야 거리(Paper setViewDistance). 불러오는 거리는 값 + 1 이고, 음수(−1 이하)면 서버 값으로 돌린다.
     *
     * <p>지금은 서버 시야 거리보다 작은 값만 반영된다. 크게 주면 서버 값으로 잘리고 경고를 한 번 남긴다.
     * <p>보내는 거리가 바뀌면 다음 틱에 청크를 더 보내거나 내린다. 아무 스레드.
     */
    default void setViewDistance(int chunks) { throw new UnsupportedOperationException("setViewDistance"); }

    /** 지금 이 플레이어에게 청크를 보내는 거리. 서버 시야, 클라이언트 렌더 거리, 플러그인 값 중 가장 작은 값이며 2 이상이다. */
    default int sendViewDistance() { throw new UnsupportedOperationException("sendViewDistance"); }

    /** 이 플레이어만의 보내는 거리(Paper setSendViewDistance, 음수면 정하지 않음). 서버 시야 거리보다 크면 잘리고 경고를 한 번 남긴다. 아무 스레드. */
    default void setSendViewDistance(int chunks) { throw new UnsupportedOperationException("setSendViewDistance"); }

    /** 이 플레이어 둘레에서 블록과 엔티티가 틱하는 거리. 지금은 늘 서버 시뮬레이션 거리다(플레이어별 틱 거리는 아직 없다). */
    default int simulationDistance() { throw new UnsupportedOperationException("simulationDistance"); }

    /**
     * 이 플레이어만의 시뮬레이션 거리(Paper setSimulationDistance, 음수면 정하지 않음).
     *
     * <p>불러오는 거리와 보내는 거리를 계산할 때(Paper 공식)는 쓰인다.
     * <p>서버 틱 범위는 아직 서버 값 그대로라, 다른 값을 주면 경고를 한 번 남긴다. 아무 스레드.
     */
    default void setSimulationDistance(int chunks) { throw new UnsupportedOperationException("setSimulationDistance"); }

    /** 핑(ms, 탭 목록 값). */
    int ping();

    InetSocketAddress address();

    /**
     * 소켓 상대 주소(연결 그 자체). BungeeCord·Velocity 포워딩이나 PROXY 프로토콜을 쓰면 프록시의 주소이고,
     * {@link #address()} 는 프록시가 알려 준 플레이어 주소다. 포워딩이 없으면 둘이 같다.
     */
    default InetSocketAddress rawAddress() { return address(); }

    /** 사유를 보여 주고 연결을 끊는다. */
    void kick(Text reason);

    /**
     * 이 플레이어의 채팅 입력을 가로챈다(Bukkit Conversation 처럼 플러그인이 다음 채팅 줄을 대답으로 받을 때).
     *
     * <p>handler 가 있으면 그 채팅 줄은 채팅으로 처리되지 않는다. {@code AsyncChatEvent}, 방송, 콘솔 로그 없이 handler 가 원문을 받는다.
     * <p>handler 는 전역 스레드에서 그 플레이어의 채팅 순서대로 불린다. 무거운 일은 스케줄러로 넘긴다. 빈 줄은 오지 않는다.
     * <p>명령({@code /...})은 가로채지 않는다. null 이면 푼다. 퇴장하면 저절로 풀린다. 마지막에 정한 handler 하나만 쓴다.
     */
    default void chatInputHandler(java.util.function.Consumer<String> handler) { throw new UnsupportedOperationException("chatInputHandler"); }

    /** 이 플레이어의 연결(접속 단계 이벤트와 같은 객체·같은 id). */
    org.domi.furnace.api.net.PlayerConnection connection();

    /** 클라이언트가 minecraft:brand 로 알린 이름(예: "vanilla"). 아직 모르면 null. */
    String clientBrand();

    /**
     * 플러그인 채널로 바이트를 보낸다(play 단계 custom_payload). 채널은 "네임스페이스:이름" 소문자 형식이고, 본문은 최대 1048576바이트다.
     * 어느 스레드에서 불러도 된다. 접속이 끊겼으면 아무 일도 없다.
     */
    void sendPluginMessage(String channel, byte[] data);

    /**
     * 이 플레이어의 채팅 창에서 그 서명의 메시지를 지운다(delete_chat, Paper Player.deleteMessage).
     * 서명은 AsyncChatEvent.signature() 의 256바이트다.
     *
     * <p>바닐라 서버는 이 패킷을 쓰지 않는다. 클라이언트가 그 메시지를 받지 않았으면 아무 일도 없다.
     */
    void deleteChatMessage(byte[] signature);

    /**
     * 패킷을 보낸다(Furnace.server().packets().send 와 같다). 어느 스레드에서 불러도 된다. 접속이 끊겼으면 아무 일도 없다.
     * 서버 상태는 바뀌지 않고, 이 플레이어 화면에만 보인다.
     */
    default void sendPacket(org.domi.furnace.api.packet.ClientboundPacket packet) {
        org.domi.furnace.api.Furnace.server().packets().send(this, packet);
    }

    /**
     * 여러 패킷을 번들 하나로 보낸다(Furnace.server().packets().sendBundle 과 같다).
     * 클라이언트가 한 프레임에 함께 처리하므로 깜박임이 없다.
     *
     * <p>예) player.sendPacketBundle(addEntity, entityData, equipment);
     */
    default void sendPacketBundle(org.domi.furnace.api.packet.ClientboundPacket... packets) {
        org.domi.furnace.api.Furnace.server().packets().sendBundle(this, java.util.List.of(packets));
    }

    /** 클라이언트가 minecraft:register 로 듣겠다고 알린 채널(사본). 바닐라 클라이언트는 비어 있다. */
    java.util.Set<String> listeningPluginChannels();

    /** 이 플레이어로서 명령을 실행 큐에 넣는다(앞의 '/' 는 없어도 된다). 접수 여부를 돌려주며, 실행 결과는 나중에 정해진다. */
    boolean performCommand(String command);

    /**
     * 메뉴 화면을 연다. 열려 있던 다른 화면은 닫힌다. 어느 스레드에서 불러도 되며, 실제 동작은 연결 스레드에서 이뤄진다.
     * InventoryOpenEvent 가 취소되면 열리지 않는다.
     */
    default void openMenu(org.domi.furnace.api.menu.Menu menu) { throw new UnsupportedOperationException("this player cannot open menus"); }

    /** 열린 메뉴 화면이 이 플레이어의 것이면 닫는다. 다른 화면이면 건드리지 않는다. */
    default void closeMenu() {}

    /** 지금 보고 있는 메뉴, 없으면 null. */
    default org.domi.furnace.api.menu.Menu currentMenu() { return null; }

    /**
     * 이 사람에게 열린(또는 openMenu 로 여는 중인) 메뉴 창의 제목만 바꾼다. Paper InventoryView#setTitle 과 같다.
     *
     * <p>Menu 의 제목은 바뀌지 않으므로 같은 메뉴를 보는 다른 사람은 그대로다.
     * <p>창을 닫으면 이 제목은 사라지고, 다시 열면 Menu 제목이 나온다. 바꾼 동안에는 Menu#setTitle 보다 이 값이 우선한다.
     * <p>열린 메뉴가 없으면 아무것도 하지 않는다. 어느 스레드에서든 부를 수 있다.
     */
    default void retitleMenu(org.domi.furnace.api.text.Text title) {}

    /** 이 사람이 menu 를 열고 있으면(여는 중 포함) 그 사람 화면의 제목을 돌려준다(retitleMenu 값, 없으면 Menu 제목). 아니면 null. */
    default org.domi.furnace.api.text.Text menuTitle(org.domi.furnace.api.menu.Menu menu) {
        return menu != null && menu == currentMenu() ? menu.title() : null;
    }

    /** 열려 있는 화면(상자·제작대·메뉴 등 어떤 창이든)을 닫아 플레이어 인벤토리만 보이는 상태로 만든다. 연결 스레드에서 이뤄진다. */
    default void closeContainer() {}

    /**
     * 지금 열린 화면의 종류 키(InventorySnapshot#type 과 같은 이름. minecraft:chest, minecraft:crafting 등).
     * 열린 화면이 없으면(플레이어 인벤토리만 보이면) null. 읽는 순간의 값이다.
     */
    default String openContainerType() { return null; }

    /** 이 플레이어에게서 그 엔티티(플레이어 포함)를 숨긴다. {@link EntityViews#hide} 를 본다. */
    default void hideEntity(org.domi.furnace.api.plugin.FurnacePlugin plugin, Entity target) {
        org.domi.furnace.api.Furnace.server().entityViews().hide(plugin, this, target);
    }

    /** 숨겼던 엔티티를 다시 보이게 한다. {@link EntityViews#show} 를 본다. */
    default void showEntity(org.domi.furnace.api.plugin.FurnacePlugin plugin, Entity target) {
        org.domi.furnace.api.Furnace.server().entityViews().show(plugin, this, target);
    }

    /** 규칙상 그 엔티티를 볼 수 있는가. {@link EntityViews#canSee} 를 본다. */
    default boolean canSee(Entity target) { return org.domi.furnace.api.Furnace.server().entityViews().canSee(this, target); }

    /** 플레이어 인벤토리 핸들(비동기 접근). */
    Inventory inventory();

    /** 지금 고른 단축 칸(0..8). 어느 스레드에서든 읽을 수 있다. 구현이 없으면 UnsupportedOperationException 을 던진다. */
    default int heldSlot() { throw new UnsupportedOperationException("heldSlot"); }

    /**
     * 단축 칸 선택을 바꾸고 클라이언트에도 알린다. 소유 스레드에서 실행되며, 이미 그 스레드면 곧바로 끝난다.
     * 0..8 밖이면 IllegalArgumentException 으로 완료한다.
     */
    default CompletableFuture<Void> setHeldSlot(int slot) { return CompletableFuture.failedFuture(new UnsupportedOperationException("setHeldSlot")); }

    @Override
    default String type() { return "minecraft:player"; }

    @Override
    default Text customName() { return null; }

    @Override
    default boolean isValid() { return isOnline(); }

    /** 플레이어 핸들은 항상 최신 값을 읽으므로 자기 자신을 돌려준다. */
    @Override
    default CompletableFuture<Player> refresh() { return CompletableFuture.completedFuture(this); }

    @Override
    default CompletableFuture<Void> updateHealth(float health) {
        setHealth(health);
        return CompletableFuture.completedFuture(null);
    }

    // ------------------------------------------------------------------ 상태 효과 (플레이어는 상태 손잡이가 없어 핸들이 직접 준다)

    /** 지금 걸린 상태 효과(잠금 안에서 뜬 사본). 어느 스레드에서나 읽는다. */
    default List<MobEffect> effects() { throw new UnsupportedOperationException("effects"); }

    /**
     * 바닐라 addEffect 를 소유 스레드에서 한다. 새로 걸렸거나 더 센 쪽으로 바뀌었으면 true 로 완료(이미 같거나 약하면 false, 죽은 플레이어도 false).
     * 지금 이 스레드가 소유 스레드면 부르는 자리에서 끝난 채로 돌아온다.
     */
    default CompletableFuture<Boolean> addEffect(MobEffect effect) { return CompletableFuture.failedFuture(new UnsupportedOperationException("addEffect")); }

    /** 그 종류를 걷는다(소유 스레드). 있었으면 true. 종류 키는 {@code minecraft:speed} 꼴(네임스페이스를 빼도 된다). */
    default CompletableFuture<Boolean> removeEffect(String type) { return CompletableFuture.failedFuture(new UnsupportedOperationException("removeEffect")); }

    /** 모든 효과를 걷는다(소유 스레드). 걷은 개수. */
    default CompletableFuture<Integer> clearEffects() { return CompletableFuture.failedFuture(new UnsupportedOperationException("clearEffects")); }

    /** 플레이어 인벤토리. */
    @Override
    default CompletableFuture<Optional<Inventory>> container() { return CompletableFuture.completedFuture(Optional.of(inventory())); }

    @Override
    default CompletableFuture<Boolean> remove() {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("a player cannot be removed - use kick"));
    }

    // ------------------------------------------------------------------ 레시피 북

    /**
     * 레시피 키(minecraft:oak_planks 처럼 레지스트리에 있는 것)를 레시피 북에 배우게 한다(/recipe give 와 같다).
     * 모르는 키와 특수 레시피는 건너뛴다. 새로 배운 수로 완료하며, 플레이어를 가진 region 스레드에서 실행된다.
     */
    default CompletableFuture<Integer> discoverRecipes(java.util.Collection<String> keys) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("discoverRecipes"));
    }

    /** 레시피 북에서 잊게 한다(/recipe take 와 같다). 잊은 수로 완료한다. */
    default CompletableFuture<Integer> undiscoverRecipes(java.util.Collection<String> keys) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("undiscoverRecipes"));
    }

    /** 지금 레시피 북에 있는가(최근 값). */
    default boolean hasDiscoveredRecipe(String key) { return false; }
}
