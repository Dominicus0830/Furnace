// 서버 게임 코드가 플러그인 이벤트를 부르는 좁은 입구. 기본(NONE)은 아무것도 하지 않고 모두 허용한다.
// 엔티티·Region 코드는 플러그인 구현을 모르고 LevelServices.gameEvents() 로만 접근한다. 호출 지점은 "허가 여부" 한 줄이다.
package org.domi.furnace.server.plugin;

import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.menu.AbstractContainerMenu;
import org.domi.furnace.server.menu.ClickType;

public interface GameEvents {

    GameEvents NONE = new GameEvents() {};

    /**
     * 피해 직전 — 돌려주는 값이 새 피해량, 음수면 취소. 소유 region 스레드에서.
     * causing·direct 는 책임자·직접 맞힌 것(없으면 null) — 원인 사슬은 구독자가 있을 때만 만든다.
     */
    default float entityDamage(LivingEntity e, String cause, float amount, org.domi.furnace.server.entity.Rider causing,
                               org.domi.furnace.server.entity.Rider direct) { return amount; }

    /** 플레이어판 entityDamage. */
    default float playerDamage(Player p, String cause, float amount, org.domi.furnace.server.entity.Rider causing,
                               org.domi.furnace.server.entity.Rider direct) { return amount; }

    /**
     * 원인 사슬(B4)을 엔티티에 적어 둘까 — 원인을 담는 이벤트(폭발·피해·스폰)를 받는 처리기가 있을 때만 참.
     * 거짓이면 호출하는 쪽은 사슬을 만들지 않는다(비용 0).
     */
    default boolean tracksCause() { return false; }

    /**
     * EntityDamageCalculateEvent 구독자가 있는가 — 없으면 호출하는 쪽이 바닐라 계산을 그대로 한다(규칙 객체를 만들지 않는다).
     */
    default boolean wantsDamageCalc() { return false; }

    /** 피해 계산 단계 이벤트를 부른다(몹 등). wantsDamageCalc 가 참일 때만. 돌려준 이벤트의 단계 값이 계산 결과다. */
    default org.domi.furnace.api.event.entity.EntityDamageCalculateEvent entityDamageCalc(LivingEntity e, String cause,
            org.domi.furnace.server.entity.Rider causing, org.domi.furnace.server.entity.Rider direct, float base, float absorption,
            org.domi.furnace.api.event.entity.DamageRules rules) { return null; }

    /** 플레이어판 entityDamageCalc. */
    default org.domi.furnace.api.event.entity.EntityDamageCalculateEvent playerDamageCalc(Player p, String cause,
            org.domi.furnace.server.entity.Rider causing, org.domi.furnace.server.entity.Rider direct, float base, float absorption,
            org.domi.furnace.api.event.entity.DamageRules rules) { return null; }

    default void entityDeath(LivingEntity e) {}

    /** false 면 넣지 않는다. */
    default boolean entitySpawn(Entity e) { return true; }

    /** false 면 from 으로 되돌린다. */
    default boolean playerMove(Player p, double fx, double fy, double fz, float fyaw, float fpitch,
                               double tx, double ty, double tz, float tyaw, float tpitch) { return true; }

    /** false 면 옮기지 않는다. 호출한 스레드에서. */
    default boolean playerTeleport(Player p, double x, double y, double z) { return true; }

    /** blockPos 가 null 이면 공중 클릭. false 면 취소. */
    default boolean playerInteract(Player p, int hand, int[] blockPos, int blockState, int face, ItemStack inHand) { return true; }

    /** PlayerInteractEvent 구독자가 있는가 — 발화 전에 비싼 계산(시선 추적)을 할지 정한다. */
    default boolean wantsInteract() { return false; }

    /** 왼쪽 클릭: blockPos 가 null 이면 허공(LEFT_CLICK_AIR), 아니면 부수기 시작(LEFT_CLICK_BLOCK). false 면 취소. */
    default boolean playerLeftClick(Player p, int[] blockPos, int blockState, int face, ItemStack inHand) { return true; }

    /** 엔티티 오른쪽 클릭(INTERACT). false 면 취소. target 의 region 스레드에서. */
    default boolean playerInteractEntity(Player p, Entity target, int hand, ItemStack inHand) { return true; }

    default boolean playerDrop(Player p, ItemStack stack) { return true; }

    default boolean playerPickup(Player p, ItemStack stack) { return true; }

    default boolean inventoryClick(Player p, AbstractContainerMenu menu, int slot, int button, ClickType type) { return true; }

    default boolean inventoryOpen(Player p, AbstractContainerMenu menu) { return true; }

    default void inventoryClose(Player p, AbstractContainerMenu menu) {}

    default void chunkLoad(String dimension, int cx, int cz) {}

    default void chunkUnload(String dimension, int cx, int cz) {}

    // ------------------------------------------------------------------ 3단계: 플레이어

    /** 이 종류(또는 상위 타입) 플러그인 이벤트를 받는 쪽이 있는가 — 발화 전에 스레드를 옮길지 정할 때. */
    default boolean has(Class<? extends org.domi.furnace.api.event.Event> type) { return false; }

    /** 웅크림·달리기·날기 전환(kind 0·1·2). false 면 상태를 바꾸지 않는다. 플레이어 소유 스레드에서. */
    default boolean playerToggle(Player p, int kind, boolean on) { return true; }

    /** 단축 칸 변경. false 면 되돌린다. */
    default boolean playerItemHeld(Player p, int previous, int next) { return true; }

    /** F 키 손 바꾸기: 바꾼 뒤 {주 손, 보조 손}, null 이면 취소. */
    default ItemStack[] playerSwapHands(Player p, ItemStack newMain, ItemStack newOff) { return new ItemStack[] {newMain, newOff}; }

    /** 다 먹기 직전. false 면 취소. */
    default boolean playerConsume(Player p, ItemStack item, int hand) { return true; }

    /** 다른 차원 도착 뒤. */
    default void playerChangedWorld(Player p, String from) {}

    /** 내보내기 직전: 보낼 사유, null 이면 취소. 요청한 스레드에서. */
    default org.domi.furnace.api.text.Text playerKick(Player p, org.domi.furnace.api.text.Text reason) { return reason; }

    /** 구슬 경험치를 받기 직전: 받을 양. */
    default int playerExpChange(Player p, int amount) { return amount; }

    /** 레벨이 바뀐 뒤(틱 끝). */
    default void playerLevelChange(Player p, int oldLevel, int newLevel) {}

    /** 발전 과제 달성: 알릴 문구(null = 알리지 않음). */
    default org.domi.furnace.api.text.Text playerAdvancementDone(Player p, String advancement, org.domi.furnace.api.text.Text message) { return message; }

    /** 잠들기 직전. false 면 취소. */
    default boolean playerBedEnter(Player p, String dimension, int x, int y, int z, int bedState) { return true; }

    /** 일어날 때(pos 는 모르면 null). */
    default void playerBedLeave(Player p, String dimension, org.domi.furnace.server.block.Pos pos) {}

    // ------------------------------------------------------------------ 3단계: 엔티티·블록·서버

    /**
     * 폭발이 블록을 부수기 직전. 원인(source 또는 sourcePlayer)이 없으면 BlockExplodeEvent(중심 칸·blockState). blocks 를 그 자리에서 고친다. false 면 취소.
     */
    default boolean explode(String dimension, Entity source, Player sourcePlayer, double x, double y, double z, int blockState,
                            java.util.List<org.domi.furnace.server.block.Pos> blocks, org.domi.furnace.server.block.Explosion explosion) { return true; }

    /** 투사체 적중 직전(hitEntity 나 hitPlayer, 또는 hitBlock). false 면 무시하고 계속 난다. */
    default boolean projectileHit(Entity projectile, Entity hitEntity, Player hitPlayer, org.domi.furnace.server.block.Pos hitBlock, int blockState, int face) { return true; }

    /** 길들이기 직전. false 면 실패. */
    default boolean entityTame(Entity e, Player owner) { return true; }

    /** 번식 직전(child 는 아직 월드 밖). false 면 취소. */
    default boolean entityBreed(Entity child, LivingEntity mother, LivingEntity father, Player breeder) { return true; }

    /** 공격 대상 변경 직전(targetEntity·targetPlayer 둘 다 null 이면 대상 잃음). false 면 그대로. */
    default boolean entityTarget(Entity e, Entity targetEntity, Player targetPlayer) { return true; }

    /** 서버 목록 응답: 바꿀 값, null 이면 그대로. 네트워크 스레드에서. */
    default StatusOverride serverListPing(java.net.InetAddress address, String motd, int online, int max, boolean hidePlayers) { return null; }

    /** 데이터 팩 함수 실행 직전(구독자가 없으면 호출되지 않는 것과 같다): false 면 함수 본문을 건너뛴다. */
    default boolean functionExecute(org.domi.furnace.server.command.CommandSource source, String function,
                                    org.domi.furnace.api.event.server.FunctionExecuteEvent.Trigger trigger, int depth) { return true; }

    /** 함수 실행 구독자가 있는가 — 함수 호출마다 부르는 길목의 빠른 검사. */
    default boolean wantsFunctionExecute() { return false; }

    /** 함수 줄 하나 실행 직전: false 면 그 줄만 건너뛴다. */
    default boolean functionCommand(org.domi.furnace.server.command.CommandSource source, String command, int depth) { return true; }

    /** 구독자가 있는가 — 함수 줄마다 부르는 길목의 빠른 검사. */
    default boolean wantsFunctionCommands() { return false; }

    /** 서버 목록 응답 덮어쓰기(motdJson 이 null 이면 MOTD 는 그대로). */
    record StatusOverride(String motdJson, int online, int max, boolean hidePlayers) {}

    // ------------------------------------------------------------------ 3단계 둘째 묶음: 블록(모두 그 블록의 region 스레드)

    /** 이웃·모양 갱신 직전(sourceBlock 은 블록 id, 상태 아님). false 면 이 갱신을 무시한다. 아주 자주 불리므로 구독자가 없으면 곧바로 true. */
    default boolean blockPhysics(String dimension, Pos pos, int state, Pos sourcePos, int sourceBlock) { return true; }

    /** 레드스톤 세기 변화 직전: 적용할 새 세기. */
    default int blockRedstone(String dimension, Pos pos, int state, int oldCurrent, int newCurrent) { return newCurrent; }

    /** 피스톤 움직임 직전(extending 이 아니면 줄어듦, blocks 는 밀·부술 칸). false 면 취소. */
    default boolean piston(String dimension, Pos pos, int dir, boolean sticky, boolean extending, java.util.List<Pos> blocks) { return true; }

    /** 유체가 pos 에서 to 로 흐르기 직전(dir 은 Dir 번호). false 면 취소. */
    default boolean blockFromTo(String dimension, Pos pos, int state, Pos to, int dir) { return true; }

    /** 자라기 직전(pos 의 oldState 가 newState 로). false 면 취소. */
    default boolean blockGrow(String dimension, Pos pos, int oldState, int newState) { return true; }

    /** 불이 pos 를 태우기 직전. false 면 취소. */
    default boolean blockBurn(String dimension, Pos pos, int state, Pos fire) { return true; }

    /** 불이 붙기 직전(cause: IGNITE_*). false 면 취소. */
    default boolean blockIgnite(String dimension, Pos pos, int cause, Pos ignitingPos, Player player) { return true; }

    int IGNITE_SPREAD = 0, IGNITE_LAVA = 1, IGNITE_FLINT_AND_STEEL = 2, IGNITE_FIREBALL = 3;

    /** 잎이 사라지기 직전. false 면 취소. */
    default boolean leavesDecay(String dimension, Pos pos, int state) { return true; }

    // ------------------------------------------------------------------ 3단계 둘째 묶음: 인벤토리

    /** 제작 결과를 정하기 직전(recipe 는 없으면 null): 결과 칸에 둘 스택. 메뉴 스레드에서. */
    default ItemStack prepareCraft(Player p, AbstractContainerMenu menu, String recipe, ItemStack result) { return result; }

    /** 화로가 연료를 태우기 직전: null 이면 취소. 화로 region 스레드에서. */
    default FurnaceBurn furnaceBurn(String dimension, Pos pos, int state, ItemStack fuel, int burnTime) { return new FurnaceBurn(burnTime, true); }

    /** 화로 연료 이벤트 결과(consumeFuel 이 false 면 연료를 줄이지 않는다). */
    record FurnaceBurn(int burnTime, boolean consumeFuel) {}

    /** 화로가 굽기 직전: 나올 스택, null 이면 취소. */
    default ItemStack furnaceSmelt(String dimension, Pos pos, int state, ItemStack source, ItemStack result) { return result; }

    /** 깔때기가 옮기기 직전: 옮길 스택, null 이면 취소. 좌표는 각 용기를 찾은 자리. */
    default ItemStack inventoryMove(String dimension, org.domi.furnace.server.block.entity.HopperBlockEntity.Inv source, double sx, double sy, double sz,
                                    org.domi.furnace.server.block.entity.HopperBlockEntity.Inv destination, double dx, double dy, double dz,
                                    ItemStack item, boolean initiatorIsSource) { return item; }

    // ------------------------------------------------------------------ 3단계 셋째 묶음: 블록 번짐·형성·소멸(그 블록의 region 스레드)

    /** 눈·얼음·흑요석·콘크리트처럼 블록이 만들어지기 직전(pos 의 oldState 가 newState 로). false 면 취소. */
    default boolean blockForm(String dimension, Pos pos, int oldState, int newState) { return true; }

    /** 풀·균사체·버섯·덩굴·불이 source 에서 pos 로 번지기 직전. false 면 취소. */
    default boolean blockSpread(String dimension, Pos pos, Pos source, int oldState, int newState) { return true; }

    /** 얼음·눈 녹음, 불 꺼짐, 산호 죽음, 풀→흙 같은 소멸 직전(newState 는 공기일 수 있음). false 면 취소. */
    default boolean blockFade(String dimension, Pos pos, int oldState, int newState) { return true; }

    // ------------------------------------------------------------------ 3단계 셋째 묶음: 마법 부여·모루·양조

    /** 마법 부여대의 제안 한 칸(단서 마법 부여 id·레벨·비용). */
    record EnchantOffer(String enchantment, int level, int cost) {}

    /** 제안 세 칸(해당 없으면 null 칸)을 정한 뒤: 최종 제안, null 이면 취소. 메뉴 스레드에서. */
    default EnchantOffer[] prepareEnchant(Player p, AbstractContainerMenu menu, int[] table, ItemStack item, int bookshelves, EnchantOffer[] offers) { return offers; }

    /** 마법 부여 결과(비용·부여할 마법). */
    record EnchantApply(int expLevelCost, java.util.Map<String, Integer> enchants) {}

    /** 제안을 눌러 부여하기 직전: 최종 값, null 이면 취소. */
    default EnchantApply enchantItem(Player p, AbstractContainerMenu menu, int[] table, ItemStack item, int button, int cost, java.util.Map<String, Integer> enchants) {
        return new EnchantApply(cost, enchants);
    }

    /** 모루 결과 값. */
    record AnvilPrepared(ItemStack result, int repairCost) {}

    /** 모루 결과를 정한 직후: 바꾼 값, null 이면 그대로(구독자 없음 포함). */
    default AnvilPrepared prepareAnvil(Player p, AbstractContainerMenu menu, ItemStack result, int repairCost, String renameText) { return null; }

    /** 양조가 끝나 병을 바꾸기 직전(items 는 양조대 다섯 칸). false 면 취소. */
    default boolean brew(String dimension, Pos pos, ItemStack[] items, int fuelLevel) { return true; }

    /** 양조대 연료 결과(consuming 이 false 면 블레이즈 가루를 줄이지 않는다). */
    record BrewFuel(int fuelPower, boolean consuming) {}

    /** 블레이즈 가루를 연료로 쓰기 직전: null 이면 취소(그 틱 전체를 멈춘다). */
    default BrewFuel brewingFuel(String dimension, Pos pos, ItemStack fuel, int fuelPower) { return new BrewFuel(fuelPower, true); }

    // ------------------------------------------------------------------ 3단계 셋째 묶음: 탈것

    /** 탈것에 타기 직전(탈것의 region 스레드). false 면 취소. */
    default boolean entityMount(org.domi.furnace.server.entity.Rider rider, Entity vehicle) { return true; }

    /** 탈것에서 내리기 직전(cancellable 이 false 면 취소해도 소용없다 — 호출한 쪽이 반환값을 무시한다). false 면 취소. */
    default boolean entityDismount(org.domi.furnace.server.entity.Rider rider, Entity vehicle, boolean cancellable) { return true; }

    // ------------------------------------------------------------------ 3단계 셋째 묶음: 낚시·차원문·책

    int FISH_FISHING = 0, FISH_CAUGHT_FISH = 1, FISH_CAUGHT_ENTITY = 2, FISH_IN_GROUND = 3, FISH_FAILED_ATTEMPT = 4, FISH_REEL_IN = 5, FISH_BITE = 6, FISH_LURED = 7;

    /** 낚시 상태 변화(hook 의 region 스레드). 취소면 -1, 아니면 떨어뜨릴 경험치. caughtEntity·caughtPlayer 는 얻은 아이템·걸린 것(없으면 둘 다 null). */
    default int playerFish(Player p, Entity hook, Entity caughtEntity, Player caughtPlayer, int state, int exp) { return exp; }

    int PORTAL_NETHER = 0, PORTAL_END = 1, PORTAL_GATEWAY = 2;

    /** 플레이어가 차원문으로 넘어가기 직전(목적 차원만 안다). false 면 취소. */
    default boolean playerPortal(Player p, String toDimension, int cause) { return true; }

    /** 플레이어가 아닌 엔티티가 차원문으로 넘어가기 직전. false 면 취소. */
    default boolean entityPortal(Entity e, String toDimension, int cause) { return true; }

    /** 불로 지옥문을 만들기 직전(blocks 는 채울 칸). false 면 취소. */
    default boolean portalCreate(String dimension, java.util.List<Pos> blocks) { return true; }

    /** 책 편집 결과(제목이 null 이 아니면 서명). */
    record BookEdit(java.util.List<String> pages, String title) {}

    /** 깃펜 책 저장·서명 직전(연결 스레드): 최종 값, null 이면 취소. */
    default BookEdit editBook(Player p, int slot, java.util.List<String> previousPages, java.util.List<String> pages, String title) { return new BookEdit(pages, title); }
}
