// OfflinePlayer 구현 — 접속 중이면 접속자 핸들로 넘기고, 아니면 playerdata 파일과 서버 목록(ops/banned-players/whitelist)을 직접 다룬다.
package org.domi.furnace.server.plugin;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.domi.furnace.api.access.BanEntry;
import org.domi.furnace.api.entity.OfflinePlayer;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.persistence.PersistentDataContainer;
import org.domi.furnace.api.persistence.PersistentDataView;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.builtin.PlayerListCommands;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerData;
import org.domi.furnace.server.net.protocol.play.Disconnect;
import org.domi.furnace.server.players.GameProfile;
import org.domi.furnace.server.players.PlayerLists;
import org.domi.furnace.server.players.UserBanListEntry;
import org.domi.furnace.server.players.UserWhiteListEntry;

final class OfflinePlayerImpl implements OfflinePlayer {

    private final PluginHost host;
    private final UUID id;

    OfflinePlayerImpl(PluginHost host, UUID id) {
        this.host = host;
        this.id = java.util.Objects.requireNonNull(id, "id");
    }

    private ServerCore core() { return host.core(); }

    private PlayerLists lists() { return core().lists(); }

    private Player online() { return core().playerByUuid(id); }

    @Override
    public UUID uniqueId() { return id; }

    @Override
    public String name() {
        Player p = online();
        if (p != null) return p.name();
        var cached = core().profileCache().get(id);
        if (cached.isPresent()) return cached.get().name();
        // 캐시가 비었어도 목록 파일에는 이름이 남아 있다
        GameProfile probe = new GameProfile(id, "");
        var ban = lists().getBans().get(probe);
        if (ban != null && ban.getUser() != null) return ban.getUser().name();
        var op = lists().getOps().get(probe);
        if (op != null && op.getUser() != null) return op.getUser().name();
        var wl = lists().getWhiteList().get(probe);
        if (wl != null && wl.getUser() != null) return wl.getUser().name();
        return null;
    }

    /** 목록 조회용(키는 UUID 라 이름은 쓰이지 않는다). */
    private GameProfile lookupProfile() {
        String n = name();
        return new GameProfile(id, n == null ? "" : n);
    }

    /** 목록에 쓸 프로필 — 이름을 모르면 쓸 수 없다. */
    private GameProfile writeProfile() {
        Player p = online();
        if (p != null) return ServerCore.profileOf(p);
        String n = name();
        if (n == null) throw new IllegalStateException("이름을 모르는 플레이어는 목록에 쓸 수 없다: " + id);
        return new GameProfile(id, n);
    }

    @Override
    public boolean isOnline() { return online() != null; }

    @Override
    public org.domi.furnace.api.entity.Player player() {
        Player p = online();
        return p == null ? null : host.player(p);
    }

    // ------------------------------------------------------------------ 저장 데이터

    @Override
    public CompletableFuture<Boolean> hasPlayedBefore() {
        if (online() != null) return RegionFuture.wrap(CompletableFuture.completedFuture(true));
        return io(() -> PlayerData.exists(core().world().worldDir(), id));
    }

    @Override
    public CompletableFuture<NbtCompound> readNbt() {
        Player p = online();
        if (p != null) return host.player(p).readNbt();
        return io(() -> {
            var root = PlayerData.readRoot(core().world().worldDir(), id);
            return root == null ? null : ApiNbt.toApi(root);
        });
    }

    @Override
    public CompletableFuture<PersistentDataView> persistentData() {
        Player p = online();
        if (p != null) return RegionFuture.wrap(CompletableFuture.completedFuture(host.player(p).persistentData()));
        return io(() -> {
            var root = PlayerData.readRoot(core().world().worldDir(), id);
            if (root == null) return null;
            return (PersistentDataView) NbtPersistentData.snapshot(root.tags().get("BukkitValues") instanceof org.domi.furnace.server.net.io.nbt.NbtCompound c ? c : null);
        });
    }

    @Override
    public PersistentDataView persistentDataNow() {
        Player p = online();
        if (p != null) return host.player(p).persistentData();
        org.domi.furnace.server.net.io.nbt.NbtCompound root;
        try {
            root = PlayerData.readRoot(core().world().worldDir(), id);
        } catch (RuntimeException e) {
            // Paper 처럼 읽지 못하는 파일은 경고만 남기고 빈 보기로 — 한 파일 때문에 플레이어를 훑는 플러그인이 끊기지 않게
            org.slf4j.LoggerFactory.getLogger(OfflinePlayerImpl.class).warn("플레이어 데이터를 읽지 못해 영속 데이터를 빈 값으로 본다 ({}): {}", id, e.toString());
            return null;
        }
        if (root == null) return null;
        return NbtPersistentData.snapshot(root.tags().get("BukkitValues") instanceof org.domi.furnace.server.net.io.nbt.NbtCompound c ? c : null);
    }

    @Override
    public CompletableFuture<Boolean> editPersistentData(Consumer<PersistentDataContainer> edit) {
        java.util.Objects.requireNonNull(edit, "edit");
        Player p = online();
        if (p != null) return host.player(p).editPersistentData(edit);
        RegionFuture<Boolean> out = new RegionFuture<>();
        PlayerData.io().execute(() -> {
            try {
                var r = PlayerData.editBukkitValues(core().world().worldDir(), id, () -> online() == null,
                        tag -> edit.accept(new NbtPersistentData(tag, false)));
                switch (r) {
                    case EDITED -> out.complete(true);
                    case MISSING -> out.complete(false);
                    case ONLINE -> editPersistentData(edit).whenComplete((v, e) -> {   // 그 사이 접속했다 — 접속자 쪽에서 고친다
                        if (e != null) out.completeExceptionally(e);
                        else out.complete(v);
                    });
                }
            } catch (Throwable t) {
                out.completeExceptionally(t);
            }
        });
        return out;
    }

    private <T> CompletableFuture<T> io(java.util.function.Supplier<T> task) {
        RegionFuture<T> out = new RegionFuture<>();
        PlayerData.io().execute(() -> {
            try {
                out.complete(task.get());
            } catch (Throwable t) {
                out.completeExceptionally(t);
            }
        });
        return out;
    }

    // ------------------------------------------------------------------ OP

    @Override
    public int opLevel() { return lists().opLevel(lookupProfile()); }

    @Override
    public void setOpLevel(int level) {
        if (level < 0 || level > 4) throw new IllegalArgumentException("OP 레벨은 0~4: " + level);
        if (level == 0) {
            if (!lists().isOp(lookupProfile())) return;
            lists().deop(lookupProfile());
        } else {
            lists().op(writeProfile(), level);
        }
        Player p = online();
        if (p != null) core().sendPlayerPermissionLevel(p);
        if (level == 0) PlayerListCommands.kickUnlistedPlayers(core());
    }

    // ------------------------------------------------------------------ 밴

    @Override
    public boolean isBanned() { return lists().getBans().isListed(lookupProfile()); }

    @Override
    public BanEntry banEntry() {
        var e = lists().getBans().get(lookupProfile());
        return e == null ? null : AccessListsImpl.convert(e);
    }

    @Override
    public void ban(String reason, Instant expires, String source) {
        UserBanListEntry entry = new UserBanListEntry(writeProfile(), null, source, expires == null ? null : Date.from(expires), reason);
        lists().getBans().add(entry);
        Player p = online();
        if (p != null) p.kick(new Disconnect(Text.translatable("multiplayer.disconnect.banned")));
    }

    @Override
    public boolean unban() {
        GameProfile profile = lookupProfile();
        if (!lists().getBans().isListed(profile)) return false;
        lists().getBans().remove(profile);
        return true;
    }

    // ------------------------------------------------------------------ 화이트리스트

    @Override
    public boolean isWhitelisted() { return lists().getWhiteList().isListed(lookupProfile()); }

    @Override
    public void setWhitelisted(boolean listed) {
        if (listed) {
            lists().getWhiteList().add(new UserWhiteListEntry(writeProfile()));
        } else {
            GameProfile profile = lookupProfile();
            if (!lists().getWhiteList().isListed(profile)) return;
            lists().getWhiteList().remove(profile);
            PlayerListCommands.kickUnlistedPlayers(core());
        }
    }

    @Override
    public boolean equals(Object o) { return o instanceof OfflinePlayerImpl other && other.id.equals(id); }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() { return "OfflinePlayer{" + id + (name() == null ? "" : "," + name()) + "}"; }
}
