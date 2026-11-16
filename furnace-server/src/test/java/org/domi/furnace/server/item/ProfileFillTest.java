// E11d: 이름·UUID 만 있는 플레이어 머리의 스킨 채우기(바닐라 PlayerHeadItem.verifyComponentsAfterLoad · ResolvableProfile.resolve · SkullBlockEntity.setOwner).
// 바깥 조회(사용자 캐시·세션 서버)는 가짜 Backend 로 바꾸고, 조회 풀은 곧바로 도는 실행기로 둔다. 조회가 끝나는 때는 Future 로 시험이 정한다.
package org.domi.furnace.server.item;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.server.ItemContextExtension;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.ShulkerBoxBlockEntity;
import org.domi.furnace.server.block.entity.SkullBlockEntity;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.players.GameProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ItemContextExtension.class)
class ProfileFillTest {

    private static final UUID NIL = new UUID(0L, 0L);
    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final GameProfile.Property TEXTURES = new GameProfile.Property("textures", "VALUE", "SIG");

    /** 가짜 사용자 캐시·세션 서버. 세션 조회는 시험이 Future 를 끝내 줄 때까지 기다린다(미리 끝내 두면 바로 끝난다). */
    static final class Fake implements ProfileFill.Backend {
        final Map<String, GameProfile> names = new java.util.HashMap<>();
        final Map<UUID, CompletableFuture<Optional<GameProfile>>> sessions = new java.util.HashMap<>();
        final AtomicInteger nameCalls = new AtomicInteger(), sessionCalls = new AtomicInteger();

        @Override
        public CompletableFuture<Optional<GameProfile>> profileByName(String name) {
            nameCalls.incrementAndGet();
            return CompletableFuture.completedFuture(Optional.ofNullable(names.get(name)));
        }

        @Override
        public CompletableFuture<Optional<GameProfile>> fetchProfile(UUID id) {
            sessionCalls.incrementAndGet();
            return sessions.computeIfAbsent(id, k -> new CompletableFuture<>());
        }

        void sessionDone(UUID id, GameProfile p) { sessions.computeIfAbsent(id, k -> new CompletableFuture<>()).complete(Optional.ofNullable(p)); }
    }

    private Fake backend;
    private ProfileFill.Service svc;

    @BeforeEach
    void install() {
        backend = new Fake();
        svc = ProfileFill.install(ProfileFill.Service.forTests(backend, Runnable::run));
    }

    @AfterEach
    void uninstall() { ProfileFill.uninstall(svc); }

    private static ItemStack head(Object profile) {
        return ItemTools.with(new ItemStack(ItemContext.id("minecraft:item", "minecraft:player_head"), 1), "minecraft:profile", profile);
    }

    private static Map<String, Object> profileOf(ItemStack s) {
        @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) s.patch().entries().get("minecraft:profile");
        return m;
    }

    private static String name(Map<String, Object> p) { return (String) p.get("name"); }

    private static UUID id(Map<String, Object> p) {
        int[] a = (int[]) p.get("id");
        return new UUID(((long) a[0] << 32) | (a[1] & 0xFFFFFFFFL), ((long) a[2] << 32) | (a[3] & 0xFFFFFFFFL));
    }

    private static boolean hasTextures(Map<String, Object> p) { return p.get("properties") instanceof List<?> l && !l.isEmpty(); }

    // ------------------------------------------------------------------ isResolved

    @Test
    void isResolvedFollowsVanilla() {
        assertFalse(ProfileFill.isResolved(profileOf(head("Notch"))), "이름만 = 확인 안 됨");
        assertFalse(ProfileFill.isResolved(profileOf(head(Map.of("id", List.of(0, 0, 0, 1))))), "UUID 만 = 확인 안 됨");
        assertTrue(ProfileFill.isResolved(profileOf(head(Map.of("name", "Notch", "id", List.of(0, 0, 0, 1))))), "이름과 UUID 둘 다 = 확인됨(setOwner(name) 의 UUID 0 도 그렇다)");
        assertTrue(ProfileFill.isResolved(profileOf(head(Map.of("name", "Notch", "properties", List.of(Map.of("name", "textures", "value", "v")))))), "속성이 있으면 확인됨");
        assertTrue(ProfileFill.isResolved(profileOf(head(Map.of()))), "둘 다 없으면 확인됨(할 것이 없다)");
        assertNull(ProfileFill.unresolvedProfile(ItemStack.EMPTY));
        assertNull(ProfileFill.unresolvedProfile(B_STONE()), "머리가 아니면 대상이 아니다");
        assertNotNull(ProfileFill.unresolvedProfile(head("Notch")));
    }

    private static ItemStack B_STONE() { return new ItemStack(ItemContext.id("minecraft:item", "minecraft:stone"), 1); }

    // ------------------------------------------------------------------ resolve

    @Test
    void nameIsLookedUpInTheCacheThenTheSessionServerGivesTextures() {
        backend.names.put("Notch", new GameProfile(NOTCH, "Notch"));
        var f = svc.resolve(profileOf(head("Notch")));
        assertFalse(f.isDone(), "세션 서버 답을 기다린다");
        backend.sessionDone(NOTCH, new GameProfile(NOTCH, "Notch", List.of(TEXTURES)));
        Map<String, Object> r = f.join();
        assertEquals("Notch", name(r));
        assertEquals(NOTCH, id(r));
        assertTrue(hasTextures(r));
        assertTrue(ProfileFill.isResolved(r));
    }

    @Test
    void sessionServerFailureFallsBackToTheNameLookupProfile() {
        backend.names.put("Notch", new GameProfile(NOTCH, "Notch"));
        backend.sessionDone(NOTCH, null);
        Map<String, Object> r = svc.resolve(profileOf(head("Notch"))).join();
        assertEquals(NOTCH, id(r));
        assertFalse(hasTextures(r), "스킨은 못 얻었지만 이름 조회의 UUID 는 남는다(authlib .or(() -> 이름 조회 결과))");
    }

    @Test
    void unknownNameBecomesNilUuidPlusName() {
        Map<String, Object> r = svc.resolve(profileOf(head("Nobody"))).join();
        assertEquals("Nobody", name(r));
        assertEquals(NIL, id(r));
        assertEquals(0, backend.sessionCalls.get());
        assertTrue(ProfileFill.isResolved(r), "한 번 시도하면 확인된 것이 되어 다시 시작되지 않는다");
    }

    @Test
    void invalidPlayerNameIsNeverLookedUp() {
        // 17자: 올바른 이름이 아니다(코덱은 이런 이름을 받지 않으니 서비스의 이름 조회를 직접 부른다 — 바닐라 fetchGameProfile(String) 의 isValidPlayerName 검사)
        assertTrue(svc.fetchByName("abcdefghijklmnopq").join().isEmpty());
        assertTrue(svc.fetchByName("with space").join().isEmpty());
        assertEquals(0, backend.nameCalls.get());
    }

    @Test
    void uuidOnlyProfileAsksTheSessionServerByUuid() {
        int[] ints = Codecs.uuidInts(NOTCH);
        var f = svc.resolve(profileOf(head(Map.of("id", List.of(ints[0], ints[1], ints[2], ints[3])))));
        backend.sessionDone(NOTCH, new GameProfile(NOTCH, "Notch", List.of(TEXTURES)));
        Map<String, Object> r = f.join();
        assertEquals("Notch", name(r));
        assertTrue(hasTextures(r));
        assertEquals(0, backend.nameCalls.get());
    }

    @Test
    void uuidOnlyProfileNotFoundKeepsTheUuidWithAnEmptyName() {
        int[] ints = Codecs.uuidInts(NOTCH);
        backend.sessionDone(NOTCH, null);
        Map<String, Object> r = svc.resolve(profileOf(head(Map.of("id", List.of(ints[0], ints[1], ints[2], ints[3]))))).join();
        assertEquals(NOTCH, id(r));
        assertEquals("", name(r));
    }

    @Test
    void sameNameIsLookedUpOnce() {
        backend.names.put("Notch", new GameProfile(NOTCH, "Notch"));
        var a = svc.resolve(profileOf(head("Notch")));
        var b = svc.resolve(profileOf(head("Notch")));
        backend.sessionDone(NOTCH, new GameProfile(NOTCH, "Notch", List.of(TEXTURES)));
        assertTrue(a.isDone() && b.isDone());
        assertEquals(1, backend.nameCalls.get());
        assertEquals(1, backend.sessionCalls.get());
    }

    // ------------------------------------------------------------------ 플레이어 인벤토리(/give·불러오기·크리에이티브 칸 쓰기)

    private void notchAnswers() {
        backend.names.put("Notch", new GameProfile(NOTCH, "Notch"));
    }

    private void notchSessionDone() { backend.sessionDone(NOTCH, new GameProfile(NOTCH, "Notch", List.of(TEXTURES))); }

    @Test
    void headStoredInAnInventorySlotIsFilledAfterTheLookup() {
        notchAnswers();
        var inv = new PlayerInventory();
        inv.set(36, head("Notch"));
        assertEquals("Notch", name(profileOf(inv.get(36))));
        assertFalse(hasTextures(profileOf(inv.get(36))), "조회가 끝나기 전에는 그대로");
        notchSessionDone();
        assertTrue(hasTextures(profileOf(inv.get(36))));
        assertEquals(NOTCH, id(profileOf(inv.get(36))));
    }

    @Test
    void addingStacksFillsEveryMergedStack() {
        notchAnswers();
        var inv = new PlayerInventory();
        // /give 두 번: 같은 확인 안 된 머리라 한 칸에 합쳐진다
        inv.add(head("Notch"), false, 64, false);
        inv.add(head("Notch"), false, 64, false);
        int slot = inv.slotWithRemainingSpace(head("Notch"), 64);
        assertTrue(slot >= 0);
        notchSessionDone();
        ItemStack merged = inv.get(PlayerInventory.windowSlot(slot));
        assertEquals(2, merged.count());
        assertTrue(hasTextures(profileOf(merged)));
        assertEquals(1, backend.nameCalls.get(), "같은 프로필 확인은 나눈다");
    }

    @Test
    void movingTheHeadToAnotherSlotWhileLookingStillFillsIt() {
        notchAnswers();
        var inv = new PlayerInventory();
        ItemStack h = head("Notch");
        inv.set(36, h);
        inv.set(10, h);
        inv.set(36, ItemStack.EMPTY);
        notchSessionDone();
        assertTrue(hasTextures(profileOf(inv.get(10))));
        assertTrue(inv.get(36).isEmpty());
    }

    @Test
    void aSlotThatChangedToAnotherProfileIsLeftAlone() {
        notchAnswers();
        var inv = new PlayerInventory();
        inv.set(36, head("Notch"));
        inv.set(36, head("Dinnerbone"));
        notchSessionDone();
        Map<String, Object> p = profileOf(inv.get(36));
        assertEquals("Dinnerbone", name(p), "그 칸은 다른 프로필 머리가 되었으니 Notch 의 결과로 덮지 않는다");
        assertNotEquals(NOTCH, id(p));
        assertFalse(hasTextures(p));
    }

    @Test
    void anAlreadyResolvedHeadStartsNoLookup() {
        var inv = new PlayerInventory();
        inv.set(36, head(Map.of("name", "Notch", "id", List.of(0, 0, 0, 1))));
        inv.set(37, B_STONE());
        assertEquals(0, backend.nameCalls.get());
        assertEquals(0, backend.sessionCalls.get());
    }

    @Test
    void loadingASavedInventoryFillsHeads() {
        notchAnswers();
        var inv = new PlayerInventory();
        ItemStack[] saved = new ItemStack[PlayerInventory.SIZE];
        java.util.Arrays.fill(saved, ItemStack.EMPTY);
        saved[20] = head("Notch");
        inv.load(saved);
        notchSessionDone();
        assertTrue(hasTextures(profileOf(inv.get(20))));
    }

    @Test
    void failedLookupLeavesTheNilUuidProfileAndNeverLoopsBack() {
        var inv = new PlayerInventory();
        inv.set(36, head("Nobody"));   // 사용자 캐시에 없고 세션 서버에도 없다
        Map<String, Object> p = profileOf(inv.get(36));
        assertEquals(NIL, id(p));
        assertEquals("Nobody", name(p));
        inv.set(36, inv.get(36));      // 확인된 것을 다시 써도 조회는 더 없다
        assertEquals(1, backend.nameCalls.get());
    }

    @Test
    void aLookupThatFailedLeavesTheHeadUnresolvedForALaterTry() {
        // 일시 장애(429 등)로 조회가 실패하면 스킨 없는 결과를 굳히지 않는다 — 머리는 확인 안 됨 그대로
        backend.names.put("Notch", new GameProfile(NOTCH, "Notch"));
        var inv = new PlayerInventory();
        inv.set(36, head("Notch"));
        backend.sessions.computeIfAbsent(NOTCH, k -> new CompletableFuture<>()).completeExceptionally(new java.io.IOException("429"));
        assertFalse(hasTextures(profileOf(inv.get(36))));
        assertNotNull(ProfileFill.unresolvedProfile(inv.get(36)), "아직 확인 안 됨 — 다음에 놓일 때 다시 시도된다");
    }

    @Test
    void noServiceInstalledMeansNoFillAndNoError() {
        ProfileFill.uninstall(svc);
        var inv = new PlayerInventory();
        inv.set(36, head("Notch"));
        assertFalse(hasTextures(profileOf(inv.get(36))));
        svc = ProfileFill.install(ProfileFill.Service.forTests(backend, Runnable::run));
    }

    // ------------------------------------------------------------------ 떨어진 아이템

    @Test
    void droppedHeadEntityIsFilledOnceItIsInTheLevel() {
        notchAnswers();
        var level = new BlockPlacementTest.TestLevel();
        var e = new ItemEntity(level, level.nextEntityId(), 0.5, -60, 0.5, head("Notch"), 0, 0, 0);
        assertTrue(level.addFreshEntity(e));
        notchSessionDone();
        assertTrue(hasTextures(profileOf(e.getItem())));
    }

    @Test
    void headEntityRemovedBeforeTheAnswerIsNotTouched() {
        notchAnswers();
        var level = new BlockPlacementTest.TestLevel();
        var e = new ItemEntity(level, level.nextEntityId(), 0.5, -60, 0.5, head("Notch"), 0, 0, 0);
        assertTrue(level.addFreshEntity(e));
        e.discard();
        ItemStack before = e.getItem();
        notchSessionDone();
        assertSame(before, e.getItem());
    }

    // ------------------------------------------------------------------ 상자·머리 블록

    private static final Pos AT = new Pos(1, -60, 1);

    @Test
    void headsInAContainerAreFilledWhenItJoinsTheLevelAndOnlyIfStillThere() {
        notchAnswers();
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(AT, Blocks.get().defaultState("minecraft:shulker_box"), 3);
        var be = (ShulkerBoxBlockEntity) level.getBlockEntity(AT);
        be.items[2] = head("Notch");
        be.items[5] = head("Notch");
        be.onAddedToLevel(level);
        be.items[5] = ItemStack.EMPTY;   // 확인 중에 꺼냈다
        notchSessionDone();
        assertTrue(hasTextures(profileOf(be.items[2])));
        assertTrue(be.items[5].isEmpty(), "꺼낸 칸은 건드리지 않는다");
    }

    @Test
    void containerReadFromNbtStartsTheFillThroughLoadWithComponentsInLevel() {
        notchAnswers();
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(AT, Blocks.get().defaultState("minecraft:shulker_box"), 3);
        var be = (ShulkerBoxBlockEntity) level.getBlockEntity(AT);
        NbtCompound item = new NbtCompound();
        item.putByte("Slot", (byte) 4);
        item.putString("id", "minecraft:player_head");
        item.putInt("count", 1);
        NbtCompound comps = new NbtCompound();
        comps.putString("minecraft:profile", "Notch");
        item.putCompound("components", comps);
        var list = new org.domi.furnace.server.net.io.nbt.NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_COMPOUND);
        list.add(item);
        NbtCompound tag = new NbtCompound();
        tag.put("Items", list);
        be.loadWithComponentsInLevel(level, tag);
        notchSessionDone();
        assertTrue(hasTextures(profileOf(be.items[4])));
    }

    @Test
    void skullBlockEntityOwnerIsResolvedOnlyWhileTheOwnerIsUnchanged() {
        notchAnswers();
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(AT, Blocks.get().defaultState("minecraft:player_head"), 3);
        var skull = (SkullBlockEntity) level.getBlockEntity(AT);
        NbtCompound tag = new NbtCompound();
        tag.putString("profile", "Notch");
        skull.loadWithComponentsInLevel(level, tag);
        assertFalse(hasTextures(skullProfile(skull)));
        notchSessionDone();
        assertTrue(hasTextures(skullProfile(skull)));
        assertEquals(NOTCH, id(skullProfile(skull)));
    }

    @Test
    void skullWhoseOwnerWasReplacedMeanwhileKeepsTheNewOwner() {
        notchAnswers();
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(AT, Blocks.get().defaultState("minecraft:player_head"), 3);
        var skull = (SkullBlockEntity) level.getBlockEntity(AT);
        NbtCompound tag = new NbtCompound();
        tag.putString("profile", "Notch");
        skull.loadWithComponentsInLevel(level, tag);
        NbtCompound other = new NbtCompound();
        other.putString("profile", "Dinnerbone");
        skull.loadWithComponentsInLevel(level, other);
        notchSessionDone();
        assertEquals("Dinnerbone", name(skullProfile(skull)), "뒤에 온 소유자를 덮지 않는다");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> skullProfile(SkullBlockEntity s) { return (Map<String, Object>) s.profile(); }

    @Test
    void everyResultingProfileIsAlreadyResolved() {
        List<String> names = new ArrayList<>(List.of("Notch", "Nobody"));
        backend.names.put("Notch", new GameProfile(NOTCH, "Notch"));
        backend.sessionDone(NOTCH, null);
        for (String n : names) assertTrue(ProfileFill.isResolved(svc.resolve(profileOf(head(n))).join()), n);
    }
}
