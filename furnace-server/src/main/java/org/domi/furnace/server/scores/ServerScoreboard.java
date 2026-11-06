// 바닐라 ServerScoreboard — 바뀔 때마다 접속자 전원에게 패킷을 보내고, 표시 중인(어느 슬롯에 걸린) 목표만 점수를 보낸다.
// 저장은 <월드>/data/scoreboard.dat(gzip {data:{Objectives, PlayerScores, Teams, DisplaySlots}, DataVersion}).
package org.domi.furnace.server.scores;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.play.ResetScore;
import org.domi.furnace.server.net.protocol.play.SetDisplayObjective;
import org.domi.furnace.server.net.protocol.play.SetObjective;
import org.domi.furnace.server.net.protocol.play.SetPlayerTeam;
import org.domi.furnace.server.net.protocol.play.SetScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServerScoreboard extends Scoreboard {

    private static final Logger LOG = LoggerFactory.getLogger("Scoreboard");
    private static final int DATA_VERSION = 3955;

    private final Set<Objective> trackedObjectives = new HashSet<>();
    private final Supplier<Collection<Player>> players;
    private final Path file;
    private volatile boolean dirty;

    /**
     * @param players 지금 접속한 플레이어들(패킷 대상)
     * @param file    저장 파일(null 이면 메모리에서만)
     */
    public ServerScoreboard(Supplier<Collection<Player>> players, Path file) {
        this.players = players;
        this.file = file;
    }

    /** <월드>/data/scoreboard.dat 을 읽는다(없거나 깨졌으면 빈 점수판). */
    public static ServerScoreboard open(Supplier<Collection<Player>> players, Path worldDir) {
        Path f = worldDir == null ? null : worldDir.resolve("data").resolve("scoreboard.dat");
        ServerScoreboard b = new ServerScoreboard(players, f);
        if (f != null && Files.isRegularFile(f)) {
            try (InputStream in = Files.newInputStream(f)) {
                NbtCompound root = NbtIo.readGzip(in);
                b.load(root.tags().get("data") instanceof NbtCompound d ? d : root);
            } catch (IOException | RuntimeException e) {
                LOG.warn("scoreboard.dat 읽기 실패 — 빈 점수판으로 시작", e);
            }
        }
        b.dirty = false;
        return b;
    }

    private void broadcast(Packet p) {
        for (Player pl : players.get()) pl.send(p);
    }

    private void setDirty() { dirty = true; }

    public boolean isDirty() { return dirty; }

    @Override
    protected void onScoreChanged(ScoreHolder holder, Objective o, Score score) {
        if (trackedObjectives.contains(o)) broadcast(new SetScore(holder.scoreboardName(), o.name(), score.value, score.display, score.numberFormat));
        setDirty();
    }

    @Override
    protected void onScoreLockChanged(ScoreHolder holder, Objective o) { setDirty(); }

    @Override
    protected void onPlayerRemoved(ScoreHolder holder) {
        broadcast(new ResetScore(holder.scoreboardName(), null));
        setDirty();
    }

    @Override
    protected void onPlayerScoreRemoved(ScoreHolder holder, Objective o) {
        if (trackedObjectives.contains(o)) broadcast(new ResetScore(holder.scoreboardName(), o.name()));
        setDirty();
    }

    /** 바닐라 setDisplayObjective: 빠진 목표가 더 이상 어디에도 없으면 추적을 끊고, 새 목표는 처음이면 전부 보낸다. */
    @Override
    public synchronized void setDisplayObjective(DisplaySlot slot, Objective objective) {
        Objective old = getDisplayObjective(slot);
        super.setDisplayObjective(slot, objective);
        if (old != objective && old != null) {
            if (getObjectiveDisplaySlotCount(old) > 0) broadcast(SetDisplayObjective.of(slot, objective));
            else stopTrackingObjective(old);
        }
        if (objective != null) {
            if (trackedObjectives.contains(objective)) broadcast(SetDisplayObjective.of(slot, objective));
            else startTrackingObjective(objective);
        }
        setDirty();
    }

    @Override
    public synchronized boolean addPlayerToTeam(String player, PlayerTeam team) {
        if (super.addPlayerToTeam(player, team)) {
            broadcast(SetPlayerTeam.player(team, player, true));
            setDirty();
            return true;
        }
        return false;
    }

    @Override
    public synchronized void removePlayerFromTeam(String player, PlayerTeam team) {
        super.removePlayerFromTeam(player, team);
        broadcast(SetPlayerTeam.player(team, player, false));
        setDirty();
    }

    @Override
    protected void onObjectiveAdded(Objective o) { setDirty(); }

    @Override
    protected void onObjectiveChanged(Objective o) {
        if (trackedObjectives.contains(o)) broadcast(SetObjective.of(o, SetObjective.METHOD_CHANGE));
        setDirty();
    }

    @Override
    protected void onObjectiveRemoved(Objective o) {
        if (trackedObjectives.contains(o)) stopTrackingObjective(o);
        setDirty();
    }

    @Override
    protected void onTeamAdded(PlayerTeam t) {
        broadcast(SetPlayerTeam.addOrModify(t, true));
        setDirty();
    }

    @Override
    protected void onTeamChanged(PlayerTeam t) {
        broadcast(SetPlayerTeam.addOrModify(t, false));
        setDirty();
    }

    @Override
    protected void onTeamRemoved(PlayerTeam t) {
        broadcast(SetPlayerTeam.remove(t));
        setDirty();
    }

    /** 바닐라 getStartTrackingPackets: 목표 추가, 걸린 슬롯들, 점수들. */
    public synchronized List<Packet> startTrackingPackets(Objective o) {
        List<Packet> out = new ArrayList<>();
        out.add(SetObjective.of(o, SetObjective.METHOD_ADD));
        for (DisplaySlot slot : DisplaySlot.values()) if (getDisplayObjective(slot) == o) out.add(SetDisplayObjective.of(slot, o));
        for (PlayerScoreEntry e : listPlayerScores(o)) out.add(new SetScore(e.owner(), o.name(), e.value(), e.display(), e.numberFormatOverride()));
        return out;
    }

    private void startTrackingObjective(Objective o) {
        List<Packet> ps = startTrackingPackets(o);
        for (Player p : players.get()) for (Packet pk : ps) p.send(pk);
        trackedObjectives.add(o);
    }

    /** 바닐라 getStopTrackingPackets: 목표 제거 + 걸린 슬롯들. */
    private List<Packet> stopTrackingPackets(Objective o) {
        List<Packet> out = new ArrayList<>();
        out.add(SetObjective.of(o, SetObjective.METHOD_REMOVE));
        for (DisplaySlot slot : DisplaySlot.values()) if (getDisplayObjective(slot) == o) out.add(SetDisplayObjective.of(slot, o));
        return out;
    }

    private void stopTrackingObjective(Objective o) {
        List<Packet> ps = stopTrackingPackets(o);
        for (Player p : players.get()) for (Packet pk : ps) p.send(pk);
        trackedObjectives.remove(o);
    }

    public synchronized int getObjectiveDisplaySlotCount(Objective o) {
        int n = 0;
        for (DisplaySlot slot : DisplaySlot.values()) if (getDisplayObjective(slot) == o) n++;
        return n;
    }

    /** 바닐라 PlayerList.updateEntireScoreboard: 접속한 플레이어에게 팀 전부, 그다음 슬롯에 걸린 목표(중복 없이)마다 시작 패킷. */
    public synchronized void sendTo(Player p) { for (Packet pk : entireScoreboardPackets()) p.send(pk); }

    public synchronized List<Packet> entireScoreboardPackets() {
        List<Packet> out = new ArrayList<>();
        for (PlayerTeam t : getPlayerTeams()) out.add(SetPlayerTeam.addOrModify(t, true));
        Set<Objective> sent = new HashSet<>();
        for (DisplaySlot slot : DisplaySlot.values()) {
            Objective o = getDisplayObjective(slot);
            if (o != null && sent.add(o)) out.addAll(startTrackingPackets(o));
        }
        return out;
    }

    /** 플레이어가 아닌 대상에게 점수를 보내는 일(사망 메시지 팀 전용 등)에서 쓰는 이름 → 팀. */
    public PlayerTeam teamOf(Player p) { return getPlayersTeam(p.name()); }

    /** 바뀐 것이 있으면 scoreboard.dat 을 쓴다. */
    public void saveIfDirty() {
        if (file == null || !dirty) return;
        NbtCompound data;
        synchronized (this) {
            dirty = false;
            data = save();
        }
        NbtCompound root = new NbtCompound();
        root.putCompound("data", data);
        root.putInt("DataVersion", DATA_VERSION);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream os = Files.newOutputStream(tmp)) {
                NbtIo.writeGzip(os, root, "");
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            dirty = true;
            LOG.error("scoreboard.dat 저장 실패", e);
        }
    }

    /** 통계가 올라갈 때(바닐라 ServerPlayer.awardStat 의 forAllObjectives(stat, …, add)). */
    public void onStat(Player p, String stat, int amount) {
        ObjectiveCriteria c = ObjectiveCriteria.forStat(stat);
        if (c != null) forAllObjectives(c, ScoreHolder.of(p), s -> s.add(amount));
    }

    /** 바닐라 ServerPlayer.resetStat 의 forAllObjectives(stat, …, ScoreAccess::reset). */
    public void onStatReset(Player p, String stat) {
        ObjectiveCriteria c = ObjectiveCriteria.forStat(stat);
        if (c != null) forAllObjectives(c, ScoreHolder.of(p), ScoreAccess::reset);
    }

    /** 바닐라 updateScoreForCriteria: 기준의 모든 목표에서 값을 정한다. */
    public void updateForCriteria(Player p, ObjectiveCriteria c, int value) {
        forAllObjectives(c, ScoreHolder.of(p), s -> s.set(value));
    }

    /** 바닐라 forAllObjectives(c, holder, ScoreAccess::increment). */
    public void incrementCriteria(Player p, ObjectiveCriteria c) {
        forAllObjectives(c, ScoreHolder.of(p), ScoreAccess::increment);
    }

    /** 바닐라 handleTeamKill: killedName 의 팀 색에 맞는 기준(teamkill.색/killedByTeam.색)을 holder 에게 1 올린다. */
    public void handleTeamKill(ScoreHolder holder, String teamOfName, ObjectiveCriteria[] byColor) {
        PlayerTeam t = getPlayersTeam(teamOfName);
        if (t == null) return;
        int id = t.color().id();
        if (id >= 0 && id < byColor.length) forAllObjectives(byColor[id], holder, ScoreAccess::increment);
    }

    Consumer<Packet> broadcaster() { return this::broadcast; }
}
