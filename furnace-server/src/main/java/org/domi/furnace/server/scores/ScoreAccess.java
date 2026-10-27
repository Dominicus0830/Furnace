// 바닐라 ScoreAccess — getOrCreatePlayerScore 가 돌려주는 점수 손잡이. 값을 바꾸면 점수판이 갱신 패킷을 보낸다.
package org.domi.furnace.server.scores;

import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;

public interface ScoreAccess {

    int get();

    /** 읽기 전용 기준인데 강제가 아니면 IllegalStateException(바닐라와 같다). */
    void set(int value);

    default int add(int amount) {
        int v = get() + amount;
        set(v);
        return v;
    }

    default int increment() { return add(1); }

    default void reset() { set(0); }

    boolean locked();

    void unlock();

    void lock();

    Text display();

    void display(Text display);

    void numberFormatOverride(NumberFormat format);

    /** 명령 연산용(바닐라 OperationArgument 의 함수형). */
    @FunctionalInterface
    interface Operation {
        void apply(ScoreAccess target, ScoreAccess source) throws CommandSyntaxException;
    }
}
