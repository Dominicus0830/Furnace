package ca.spottedleaf.concurrentutil.executor;

/** Paper(concurrentutil) Cancellable 의 모양: 아직 돌지 않은 일을 취소한다. 취소돼 콜백이 불리지 않게 됐으면 참. */
public interface Cancellable {
    boolean cancel();
}
