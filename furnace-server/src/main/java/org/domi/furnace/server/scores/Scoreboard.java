// 바닐라 Scoreboard — 목표·점수·표시 슬롯·팀. 서버 전체가 하나를 함께 쓴다(차원 공유).
// 스레드: 명령(전역·네트워크 스레드)·선택자 조건(region 스레드)·플레이어 틱(region 스레드)이 모두 만지므로 이 객체를 잠그고 쓴다.
// 갱신 알림(on*)은 잠금 안에서 불린다 — 하위 클래스는 패킷을 큐에 넣기만 하고 다른 잠금을 잡지 않는다.
// 차이: 바닐라 맵은 fastutil 해시 순서라 목록·저장 순서가 다를 수 있다(여기는 삽입 순서).
package org.domi.furnace.server.scores;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Scoreboard {

    private static final Logger LOG = LoggerFactory.getLogger("Scoreboard");

    /** 바닐라 HIDDEN_SCORE_PREFIX: "#" 로 시작하는 이름은 목록에 보이지 않는다. */
    public static final String HIDDEN_SCORE_PREFIX = "#";

    private final Map<String, Objective> objectivesByName = new LinkedHashMap<>();
    private final Map<ObjectiveCriteria, List<Objective>> objectivesByCriteria = new IdentityHashMap<>();
    private final Map<String, Map<Objective, Score>> playerScores = new LinkedHashMap<>();
    private final Map<DisplaySlot, Objective> displayObjectives = new EnumMap<>(DisplaySlot.class);
    private final Map<String, PlayerTeam> teamsByName = new LinkedHashMap<>();
    private final Map<String, PlayerTeam> teamsByPlayer = new HashMap<>();

    // ------------------------------------------------------------------ 목표

    public synchronized Objective getObjective(String name) { return name == null ? null : objectivesByName.get(name); }

    /** 바닐라 addObjective: 같은 이름이 있으면 IllegalArgumentException. */
    public synchronized Objective addObjective(String name, ObjectiveCriteria criteria, Text displayName, ObjectiveCriteria.RenderType renderType,
                                               boolean displayAutoUpdate, NumberFormat numberFormat) {
        if (objectivesByName.containsKey(name)) throw new IllegalArgumentException("An objective with the name '" + name + "' already exists!");
        Objective o = new Objective(this, name, criteria, displayName, renderType, displayAutoUpdate, numberFormat);
        objectivesByCriteria.computeIfAbsent(criteria, k -> new ArrayList<>()).add(o);
        objectivesByName.put(name, o);
        onObjectiveAdded(o);
        return o;
    }

    /** 바닐라 forAllObjectives: 그 기준의 모든 목표에서 주인의 점수(쓰기 강제)를 건넨다. */
    public synchronized void forAllObjectives(ObjectiveCriteria criteria, ScoreHolder holder, Consumer<ScoreAccess> action) {
        List<Objective> list = objectivesByCriteria.get(criteria);
        if (list == null) return;
        for (Objective o : new ArrayList<>(list)) action.accept(getOrCreatePlayerScore(holder, o, true));
    }

    private Map<Objective, Score> getOrCreatePlayerInfo(String name) {
        return playerScores.computeIfAbsent(name, k -> new LinkedHashMap<>());
    }

    public ScoreAccess getOrCreatePlayerScore(ScoreHolder holder, Objective objective) { return getOrCreatePlayerScore(holder, objective, false); }

    /** 바닐라 getOrCreatePlayerScore: 없으면 0 점(잠김)으로 만들고, 처음 값을 쓸 때 갱신을 알린다. */
    public synchronized ScoreAccess getOrCreatePlayerScore(ScoreHolder holder, Objective objective, boolean forceWritable) {
        boolean writable = forceWritable || !objective.criteria().isReadOnly();
        Map<Objective, Score> info = getOrCreatePlayerInfo(holder.scoreboardName());
        boolean[] created = new boolean[1];
        Score score = info.computeIfAbsent(objective, k -> {
            created[0] = true;
            return new Score();
        });
        Scoreboard board = this;
        return new ScoreAccess() {
            public int get() {
                synchronized (board) {
                    return score.value;
                }
            }

            public void set(int value) {
                synchronized (board) {
                    if (!writable) throw new IllegalStateException("Cannot modify read-only score");
                    boolean changed = created[0];
                    if (objective.displayAutoUpdate()) {
                        Text d = holder.displayName();
                        if (d != null && !d.equals(score.display)) {
                            score.display = d;
                            changed = true;
                        }
                    }
                    if (value != score.value) {
                        score.value = value;
                        changed = true;
                    }
                    if (changed) sendUpdate();
                }
            }

            public Text display() {
                synchronized (board) {
                    return score.display;
                }
            }

            public void display(Text text) {
                synchronized (board) {
                    if (created[0] || !Objects.equals(text, score.display)) {
                        score.display = text;
                        sendUpdate();
                    }
                }
            }

            public void numberFormatOverride(NumberFormat format) {
                synchronized (board) {
                    score.numberFormat = format;
                    sendUpdate();
                }
            }

            public boolean locked() {
                synchronized (board) {
                    return score.locked;
                }
            }

            public void unlock() { setLocked(false); }

            public void lock() { setLocked(true); }

            private void setLocked(boolean locked) {
                synchronized (board) {
                    score.locked = locked;
                    if (created[0]) sendUpdate();
                    onScoreLockChanged(holder, objective);
                }
            }

            private void sendUpdate() {
                onScoreChanged(holder, objective, score);
                created[0] = false;
            }
        };
    }

    /** 바닐라 getPlayerScoreInfo(없으면 null). 돌려준 점수는 읽기만 한다. */
    public synchronized Score getPlayerScoreInfo(ScoreHolder holder, Objective objective) {
        Map<Objective, Score> info = playerScores.get(holder.scoreboardName());
        return info != null ? info.get(objective) : null;
    }

    /** 바닐라 listPlayerScores(목표): 주인 이름별 점수. */
    public synchronized List<PlayerScoreEntry> listPlayerScores(Objective objective) {
        List<PlayerScoreEntry> out = new ArrayList<>();
        playerScores.forEach((name, info) -> {
            Score s = info.get(objective);
            if (s != null) out.add(new PlayerScoreEntry(name, s.value, s.display, s.numberFormat));
        });
        return out;
    }

    /** 바닐라 PlayerScoreEntry. */
    public record PlayerScoreEntry(String owner, int value, Text display, NumberFormat numberFormatOverride) {
        public boolean isHidden() { return owner.startsWith(HIDDEN_SCORE_PREFIX); }
    }

    public synchronized List<Objective> getObjectives() { return new ArrayList<>(objectivesByName.values()); }

    public synchronized List<String> getObjectiveNames() { return new ArrayList<>(objectivesByName.keySet()); }

    /** 바닐라 getTrackedPlayers. */
    public synchronized List<ScoreHolder> getTrackedPlayers() {
        List<ScoreHolder> out = new ArrayList<>();
        for (String n : playerScores.keySet()) out.add(ScoreHolder.forNameOnly(n));
        return out;
    }

    /** 바닐라 resetAllPlayerScores. */
    public synchronized void resetAllPlayerScores(ScoreHolder holder) {
        Map<Objective, Score> removed = playerScores.remove(holder.scoreboardName());
        if (removed != null) onPlayerRemoved(holder);
    }

    /** 바닐라 resetSinglePlayerScore. */
    public synchronized void resetSinglePlayerScore(ScoreHolder holder, Objective objective) {
        Map<Objective, Score> info = playerScores.get(holder.scoreboardName());
        if (info == null) return;
        boolean removed = info.remove(objective) != null;
        if (info.isEmpty()) {
            if (playerScores.remove(holder.scoreboardName()) != null) onPlayerRemoved(holder);
        } else if (removed) {
            onPlayerScoreRemoved(holder, objective);
        }
    }

    /** 바닐라 listPlayerScores(주인): 목표 → 값. */
    public synchronized Map<Objective, Integer> listPlayerScores(ScoreHolder holder) {
        Map<Objective, Score> info = playerScores.get(holder.scoreboardName());
        Map<Objective, Integer> out = new LinkedHashMap<>();
        if (info != null) info.forEach((o, s) -> out.put(o, s.value));
        return out;
    }

    /** 바닐라 removeObjective. */
    public synchronized void removeObjective(Objective objective) {
        objectivesByName.remove(objective.name());
        for (DisplaySlot slot : DisplaySlot.values()) if (getDisplayObjective(slot) == objective) setDisplayObjective(slot, null);
        List<Objective> list = objectivesByCriteria.get(objective.criteria());
        if (list != null) list.remove(objective);
        for (Map<Objective, Score> info : playerScores.values()) info.remove(objective);
        onObjectiveRemoved(objective);
    }

    public synchronized void setDisplayObjective(DisplaySlot slot, Objective objective) { displayObjectives.put(slot, objective); }

    public synchronized Objective getDisplayObjective(DisplaySlot slot) { return displayObjectives.get(slot); }

    // ------------------------------------------------------------------ 팀

    public synchronized PlayerTeam getPlayerTeam(String name) { return teamsByName.get(name); }

    /** 바닐라 addPlayerTeam: 이미 있으면 그 팀(경고). */
    public synchronized PlayerTeam addPlayerTeam(String name) {
        PlayerTeam t = getPlayerTeam(name);
        if (t != null) {
            LOG.warn("Requested creation of existing team '{}'", name);
            return t;
        }
        t = new PlayerTeam(this, name);
        teamsByName.put(name, t);
        onTeamAdded(t);
        return t;
    }

    public synchronized void removePlayerTeam(PlayerTeam team) {
        teamsByName.remove(team.name());
        for (String p : team.players()) teamsByPlayer.remove(p);
        onTeamRemoved(team);
    }

    /** 바닐라 addPlayerToTeam: 다른 팀에 있으면 먼저 뺀다. 새로 들어갔으면 참. */
    public synchronized boolean addPlayerToTeam(String player, PlayerTeam team) {
        if (getPlayersTeam(player) != null) removePlayerFromTeam(player);
        teamsByPlayer.put(player, team);
        return team.players().add(player);
    }

    /** 바닐라 removePlayerFromTeam(이름): 팀에 있었으면 참. */
    public synchronized boolean removePlayerFromTeam(String player) {
        PlayerTeam t = getPlayersTeam(player);
        if (t != null) {
            removePlayerFromTeam(player, t);
            return true;
        }
        return false;
    }

    public synchronized void removePlayerFromTeam(String player, PlayerTeam team) {
        if (getPlayersTeam(player) != team) {
            throw new IllegalStateException("Player is either on another team or not on any team. Cannot remove from team '" + team.name() + "'.");
        }
        teamsByPlayer.remove(player);
        team.players().remove(player);
    }

    public synchronized List<String> getTeamNames() { return new ArrayList<>(teamsByName.keySet()); }

    public synchronized List<PlayerTeam> getPlayerTeams() { return new ArrayList<>(teamsByName.values()); }

    public synchronized PlayerTeam getPlayersTeam(String player) { return teamsByPlayer.get(player); }

    // ------------------------------------------------------------------ 갱신 알림(서버 점수판이 패킷으로 바꾼다)

    protected void onObjectiveAdded(Objective o) { }

    protected void onObjectiveChanged(Objective o) { }

    protected void onObjectiveRemoved(Objective o) { }

    protected void onScoreChanged(ScoreHolder holder, Objective o, Score score) { }

    protected void onScoreLockChanged(ScoreHolder holder, Objective o) { }

    protected void onPlayerRemoved(ScoreHolder holder) { }

    protected void onPlayerScoreRemoved(ScoreHolder holder, Objective o) { }

    protected void onTeamAdded(PlayerTeam t) { }

    protected void onTeamChanged(PlayerTeam t) { }

    protected void onTeamRemoved(PlayerTeam t) { }

    /** 바닐라 entityRemoved: 플레이어가 아닌 엔티티가 죽어(지워져) 사라지면 점수와 팀을 지운다. */
    public synchronized void entityRemoved(String scoreboardName) {
        ScoreHolder h = ScoreHolder.forNameOnly(scoreboardName);
        resetAllPlayerScores(h);
        removePlayerFromTeam(scoreboardName);
    }

    // ------------------------------------------------------------------ 저장(바닐라 ScoreboardSaveData: data/scoreboard.dat 의 data)

    public synchronized NbtCompound save() {
        NbtCompound out = new NbtCompound();
        out.putList("Objectives", saveObjectives());
        out.putList("PlayerScores", savePlayerScores());
        out.putList("Teams", saveTeams());
        NbtCompound slots = new NbtCompound();
        for (DisplaySlot slot : DisplaySlot.values()) {
            Objective o = getDisplayObjective(slot);
            if (o != null) slots.putString(slot.key(), o.name());
        }
        if (!slots.tags().isEmpty()) out.putCompound("DisplaySlots", slots);
        return out;
    }

    private NbtList saveObjectives() {
        NbtList list = new NbtList(NbtIo.TAG_COMPOUND);
        for (Objective o : objectivesByName.values()) {
            NbtCompound t = new NbtCompound();
            t.putString("Name", o.name());
            t.putString("CriteriaName", o.criteria().name());
            t.putString("DisplayName", o.displayName().toJson());
            t.putString("RenderType", o.renderType().id());
            t.putBoolean("display_auto_update", o.displayAutoUpdate());
            if (o.numberFormat() != null) t.putCompound("format", NumberFormat.save(o.numberFormat()));
            list.add(t);
        }
        return list;
    }

    private NbtList savePlayerScores() {
        NbtList list = new NbtList(NbtIo.TAG_COMPOUND);
        playerScores.forEach((name, info) -> info.forEach((o, s) -> {
            NbtCompound t = s.write();
            t.putString("Name", name);
            t.putString("Objective", o.name());
            list.add(t);
        }));
        return list;
    }

    private NbtList saveTeams() {
        NbtList list = new NbtList(NbtIo.TAG_COMPOUND);
        for (PlayerTeam team : teamsByName.values()) {
            NbtCompound t = new NbtCompound();
            t.putString("Name", team.name());
            t.putString("DisplayName", team.displayName().toJson());
            if (team.color().id() >= 0) t.putString("TeamColor", team.color().key());
            t.putBoolean("AllowFriendlyFire", team.isAllowFriendlyFire());
            t.putBoolean("SeeFriendlyInvisibles", team.canSeeFriendlyInvisibles());
            t.putString("MemberNamePrefix", team.playerPrefix().toJson());
            t.putString("MemberNameSuffix", team.playerSuffix().toJson());
            t.putString("NameTagVisibility", team.nameTagVisibility().name);
            t.putString("DeathMessageVisibility", team.deathMessageVisibility().name);
            t.putString("CollisionRule", team.collisionRule().name);
            NbtList players = new NbtList(NbtIo.TAG_STRING);
            for (String p : team.players()) players.add(p);
            t.putList("Players", players);
            list.add(t);
        }
        return list;
    }

    /** 바닐라 ScoreboardSaveData.load 순서: 목표 → 점수 → 표시 슬롯 → 팀. 읽는 동안 알림(패킷)은 보내지 않는다(아직 접속자가 없다). */
    public synchronized void load(NbtCompound data) {
        if (data == null) return;
        for (NbtCompound t : compounds(data, "Objectives")) {
            String criteriaName = str(t, "CriteriaName");
            ObjectiveCriteria criteria = ObjectiveCriteria.byName(criteriaName).orElseGet(() -> {
                LOG.warn("Unknown scoreboard criteria {}, replacing with {}", criteriaName, ObjectiveCriteria.DUMMY.name());
                return ObjectiveCriteria.DUMMY;
            });
            String name = str(t, "Name");
            Text display = json(str(t, "DisplayName"));
            ObjectiveCriteria.RenderType rt = ObjectiveCriteria.RenderType.byId(str(t, "RenderType"));
            boolean autoUpdate = t.getBoolean("display_auto_update");
            NumberFormat fmt = t.tags().get("format") instanceof NbtCompound f ? NumberFormat.load(f) : null;
            addObjective(name, criteria, display == null ? Text.text(name) : display, rt, autoUpdate, fmt);
        }
        for (NbtCompound t : compounds(data, "PlayerScores")) {
            Score s = Score.read(t);
            String name = str(t, "Name");
            String objName = str(t, "Objective");
            Objective o = getObjective(objName);
            if (o == null) {
                LOG.error("Unknown objective {} for name {}, ignoring", objName, name);
            } else {
                getOrCreatePlayerInfo(name).put(o, s);
            }
        }
        if (data.tags().get("DisplaySlots") instanceof NbtCompound slots) {
            for (var e : slots.tags().entrySet()) {
                DisplaySlot slot = DisplaySlot.byName(e.getKey());
                if (slot != null && e.getValue() instanceof String objName) setDisplayObjective(slot, getObjective(objName));
            }
        }
        if (data.tags().get("Teams") instanceof NbtList) {
            for (NbtCompound t : compounds(data, "Teams")) {
                String name = str(t, "Name");
                PlayerTeam team = addPlayerTeam(name);
                Text display = json(str(t, "DisplayName"));
                if (display != null) team.displayName(display);
                if (t.tags().get("TeamColor") instanceof String c) {
                    ChatFormatting f = ChatFormatting.byName(c);
                    if (f != null) team.color(f);
                }
                if (t.tags().get("AllowFriendlyFire") instanceof Number) team.allowFriendlyFire(t.getBoolean("AllowFriendlyFire"));
                if (t.tags().get("SeeFriendlyInvisibles") instanceof Number) team.seeFriendlyInvisibles(t.getBoolean("SeeFriendlyInvisibles"));
                if (t.tags().get("MemberNamePrefix") instanceof String s) {
                    Text p = json(s);
                    if (p != null) team.playerPrefix(p);
                }
                if (t.tags().get("MemberNameSuffix") instanceof String s) {
                    Text p = json(s);
                    if (p != null) team.playerSuffix(p);
                }
                if (t.tags().get("NameTagVisibility") instanceof String s) {
                    PlayerTeam.Visibility v = PlayerTeam.Visibility.byName(s);
                    if (v != null) team.nameTagVisibility(v);
                }
                if (t.tags().get("DeathMessageVisibility") instanceof String s) {
                    PlayerTeam.Visibility v = PlayerTeam.Visibility.byName(s);
                    if (v != null) team.deathMessageVisibility(v);
                }
                if (t.tags().get("CollisionRule") instanceof String s) {
                    PlayerTeam.CollisionRule v = PlayerTeam.CollisionRule.byName(s);
                    if (v != null) team.collisionRule(v);
                }
                if (t.tags().get("Players") instanceof NbtList players) {
                    for (Object p : players.items()) if (p instanceof String n) addPlayerToTeam(n, team);
                }
            }
        }
    }

    private static List<NbtCompound> compounds(NbtCompound data, String key) {
        List<NbtCompound> out = new ArrayList<>();
        if (data.tags().get(key) instanceof NbtList l) for (Object o : l.items()) if (o instanceof NbtCompound c) out.add(c);
        return out;
    }

    private static String str(NbtCompound t, String key) { return t.tags().get(key) instanceof String s ? s : ""; }

    private static Text json(String s) {
        try {
            return org.domi.furnace.api.text.serializer.JsonTextSerializer.json().deserialize(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 점수 목록을 가진 주인 수(테스트·진단용). */
    public synchronized Collection<String> trackedNames() { return new ArrayList<>(playerScores.keySet()); }
}
