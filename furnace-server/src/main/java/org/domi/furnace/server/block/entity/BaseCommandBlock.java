// 바닐라 BaseCommandBlock: 명령 블록·명령 블록 수레가 공유하는 명령 한 줄 + 마지막 출력 + 성공 횟수 + 저장.
// Furnace 구조: 명령은 전역 스레드의 명령 큐에서 돈다(바닐라는 그 자리에서 동기). performCommand 는 그 틱 안에서 결과를 기다리고
// (기다리는 동안 명령이 이 region 에 보낸 작업을 돌린다 — Region.awaitUrgent) 끝나면 바닐라처럼 RAN 이다. 틱 예산을 넘기면 끝났음을 알리는
// 콜백으로 소유 region 스레드에서 성공 횟수를 반영하고 이어 간다. lastExecution 은 실행 시작 때 기록한다(같은 틱 재실행 방지는 그대로).
package org.domi.furnace.server.block.entity;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.BlockCommandSink;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.item.FlatText;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public abstract class BaseCommandBlock implements BlockCommandSink {

    /** performCommand 의 결과. */
    public enum Performed {
        /** 바닐라의 false: 이번 틱에 이미 실행했다(체인은 여기서 끊긴다). */
        NOT_RUN,
        /** 바닐라의 true, 그 자리에서 끝났다(빈 명령·꺼짐·Searge 포함). */
        RAN,
        /** 명령이 전역 스레드에서 도는 중 — 끝나면 소유 스레드에서 콜백이 불린다. */
        PENDING,
        /** 앞선 실행이 아직 도는 중이라 건너뛴다(겹침 방지 — 호출자는 아무것도 바꾸지 않는다). */
        BUSY
    }

    private static final Text AT = Text.text("@");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    /** 마지막 출력의 시각 문자열(시험에서 고정). */
    public static volatile Supplier<String> clock = () -> LocalTime.now().format(TIME);

    private volatile long lastExecution = -1L;
    private volatile boolean updateLastExecution = true;
    private volatile int successCount;
    private volatile boolean trackOutput = true;
    private volatile Text lastOutput;
    private volatile String command = "";
    private volatile Text customName;
    /** 마지막으로 performCommand 에 들어온 레벨(전역 스레드에서 게임룰을 읽는 데 쓴다). */
    private volatile BlockLevel activeLevel;
    /** 비동기 실행이 진행 중인가(재진입 방지). */
    private final java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean();

    public boolean isRunning() { return running.get(); }

    // ------------------------------------------------------------------ 호스트(블록 엔티티/수레)가 채울 것

    /** 소유 region 판정·스레드 넘김에 쓰는 칸. */
    protected abstract Pos blockPos();

    /** isValid: 호스트가 아직 살아 있는가(지워진 블록 엔티티·엔티티는 결과를 반영하지 않는다). */
    protected abstract boolean isValid();

    /** onUpdated: 소유 스레드에서 클라이언트에 알릴 일(블록 갱신 / 엔티티 데이터). */
    protected abstract void onUpdated(BlockLevel level);

    // ------------------------------------------------------------------ 상태

    public int successCount() { return successCount; }

    public void setSuccessCount(int n) { successCount = n; }

    /** getLastOutput: 없으면 빈 텍스트. */
    public Text lastOutput() { Text t = lastOutput; return t == null ? Text.empty() : t; }

    /** 바닐라 lastOutput 필드 그대로(null 가능). */
    public Text rawLastOutput() { return lastOutput; }

    public void setLastOutput(Text t) { lastOutput = t; }

    public String command() { return command; }

    /** setCommand: 명령을 바꾸고 성공 횟수를 0 으로. */
    public void setCommand(String c) {
        command = c;
        successCount = 0;
    }

    public boolean isTrackOutput() { return trackOutput; }

    public void setTrackOutput(boolean b) { trackOutput = b; }

    public Text customName() { return customName; }

    public void setCustomName(Text t) { customName = t; }

    /** getName: 사용자 지정 이름 또는 "@". */
    public Text name() { Text t = customName; return t != null ? t : AT; }

    public long lastExecution() { return lastExecution; }

    public boolean updateLastExecution() { return updateLastExecution; }

    public void setUpdateLastExecution(boolean b) { updateLastExecution = b; }

    // ------------------------------------------------------------------ 저장(키 이름은 바닐라 그대로)

    public NbtCompound save(NbtCompound tag) {
        tag.putString("Command", command);
        tag.putInt("SuccessCount", successCount);
        if (customName != null) tag.putString("CustomName", FlatText.stable(customName));
        tag.putBoolean("TrackOutput", trackOutput);
        if (lastOutput != null && trackOutput) tag.putString("LastOutput", FlatText.stable(lastOutput));
        tag.putBoolean("UpdateLastExecution", updateLastExecution);
        if (updateLastExecution && lastExecution > 0L) tag.putLong("LastExecution", lastExecution);
        return tag;
    }

    public void load(NbtCompound tag) {
        command = tag.getString("Command");
        successCount = tag.getInt("SuccessCount");
        if (tag.tags().get("CustomName") instanceof String s) {
            Text n;
            try {
                n = FlatText.parse(s);
            } catch (RuntimeException e) {
                n = null;   // 읽을 수 없는 이름은 없는 것으로(parseCustomNameSafe)
            }
            customName = n;
        } else {
            customName = null;
        }
        if (tag.tags().get("TrackOutput") instanceof Byte) trackOutput = tag.getBoolean("TrackOutput");
        if (tag.tags().get("LastOutput") instanceof String s && trackOutput) {
            try {
                lastOutput = FlatText.parse(s);
            } catch (Throwable t) {
                lastOutput = Text.text(String.valueOf(t.getMessage()));
            }
        } else {
            lastOutput = null;
        }
        if (tag.contains("UpdateLastExecution")) updateLastExecution = tag.getBoolean("UpdateLastExecution");
        lastExecution = updateLastExecution && tag.contains("LastExecution") ? tag.getLong("LastExecution") : -1L;
    }

    // ------------------------------------------------------------------ 실행

    /**
     * performCommand(level): 이번 틱에 이미 실행했으면 NOT_RUN. "Searge" 면 고정 출력·성공 1. 아니면 성공 횟수를 0 으로 하고,
     * 서버에서 명령 블록이 켜져 있고 명령이 비지 않았으면 명령을 돌린다(PENDING — 끝나면 소유 스레드에서 after 가 레벨과 함께 불린다).
     * 그 자리에서 끝나는 경우(빈 명령·꺼짐·Searge)는 RAN, after 는 부르지 않는다.
     * 시험용 레벨처럼 결과가 곧바로 돌아오면 RAN 으로 돌려주고 after 는 부르지 않는다(호출자가 이어서 처리).
     */
    public Performed performCommand(BlockLevel level, Consumer<BlockLevel> after) {
        if (running.get()) return Performed.BUSY;
        activeLevel = level;
        if (level.gameTime() == lastExecution) return Performed.NOT_RUN;
        if ("Searge".equalsIgnoreCase(command)) {
            lastOutput = Text.text("#itzlipofutzli");
            successCount = 1;
            return Performed.RAN;
        }
        successCount = 0;
        if (level.commandBlockEnabled() && !command.isEmpty()) {
            lastOutput = null;
            lastExecution = updateLastExecution ? level.gameTime() : -1L;
            var returned = new java.util.concurrent.atomic.AtomicBoolean();   // performCommand 가 이미 돌아왔나
            var early = new java.util.concurrent.atomic.AtomicBoolean();      // 돌아오기 전에 결과가 왔나
            running.set(true);
            level.runBlockCommand(this, command, n -> level.runOnOwner(blockPos(), lv -> {
                running.set(false);
                if (!isValid()) return;
                successCount = n;
                if (returned.get()) after.accept(lv);
                else early.set(true);
            }));
            // 바닐라는 그 자리에서 끝난다: 이 틱 안에서 결과를 기다린다(명령이 이 region 에 보낸 왕복과 결과 콜백을 기다리며 돌린다).
            // 예산을 넘기면 예전처럼 PENDING — 결과는 소유 스레드로 돌아와 after 로 잇는다.
            level.awaitCommand(() -> early.get() || !running.get());
            returned.set(true);
            return early.get() ? Performed.RAN : Performed.PENDING;
        }
        lastExecution = updateLastExecution ? level.gameTime() : -1L;
        return Performed.RAN;
    }

    // ------------------------------------------------------------------ 출력(BlockCommandSink)

    @Override public String textName() { return name().getString(); }

    @Override public Text displayName() { return name(); }

    @Override
    public boolean acceptsSuccess() {
        BlockLevel l = activeLevel;
        return trackOutput && l != null && l.gameRule("sendCommandFeedback");
    }

    @Override public boolean acceptsFailure() { return trackOutput; }

    @Override
    public boolean shouldInformAdmins() {
        BlockLevel l = activeLevel;
        return l != null && l.gameRule("commandBlockOutput");
    }

    @Override
    public void sendSystemMessage(Text message) {
        if (!trackOutput) return;
        lastOutput = Text.text("[" + clock.get() + "] ").append(message);
        BlockLevel l = activeLevel;
        if (l != null) l.runOnOwner(blockPos(), lv -> { if (isValid()) onUpdated(lv); });
    }
}
