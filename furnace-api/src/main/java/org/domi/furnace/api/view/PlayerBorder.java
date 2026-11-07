// 한 플레이어에게만 보이는 월드 보더 — PlayerViews.border(plugin, viewer). 서버의 진짜 보더는 그대로이고 이 사람 화면(벽·경고 빨간 테두리)만 바뀐다.
// 값을 바꾸면 바로 그 사람에게 가며 아무 스레드에서 불러도 된다. 만든 값은 그 접속 동안만 산다(다시 접속하면 서버 보더로 돌아온다).
// enforce(true) 면 서버 판정(보더 밖 피해·보더 밖 블록 상호작용)도 이 값을 쓴다 — 기본은 보이는 것만.
package org.domi.furnace.api.view;

public interface PlayerBorder {

    double centerX();

    double centerZ();

    /** 중심을 옮긴다(±29999984 로 자른다). */
    void center(double x, double z);

    /** 지금 크기(보간 중이면 지금 시각의 값). */
    double size();

    /** 한 번에 이 크기로(보간 중이었으면 멈춘다). */
    void size(double blocks);

    /** 지금 크기에서 toSize 까지 millis 동안 선형으로 바꾼다(0 이하면 한 번에). 보간 목표 크기. */
    void lerpSize(double toSize, long millis);

    /** 보간 목표 크기(보간 중이 아니면 {@link #size()}). */
    double lerpTarget();

    /** 경계에서 이만큼 안으로 들어오면 화면이 붉게 번쩍인다(블록). */
    int warningDistance();

    void warningDistance(int blocks);

    /** 줄어드는 경계가 이만큼 안에 오면 붉게 번쩍인다(초). */
    int warningTime();

    void warningTime(int seconds);

    /** 보더 밖 피해(블록당)·안전 거리 — {@link #enforce} 일 때만 쓰인다(화면에는 영향 없음). */
    double damagePerBlock();

    void damagePerBlock(double damage);

    double damageSafeZone();

    void damageSafeZone(double blocks);

    /** 서버도 이 보더로 판정하는가(보더 밖 피해, 보더 밖에서 블록 부수기·놓기 막기). 기본 false. */
    boolean enforced();

    void enforce(boolean enforce);

    /** 지금 (x, z) 가 경계 안인가. */
    boolean contains(double x, double z);
}
