package ca.spottedleaf.concurrentutil.executor.standard;

/**
 * Paper(concurrentutil) PrioritisedExecutor 의 모양(NMS shim 5e — voxy 가 디스크 읽기에 우선순위를 준다). Furnace 는 우선순위 실행기를 내주지 않아
 * 우선순위 열거만 있다(Paper jar 를 javap 로 대조한 값·판정).
 */
public interface PrioritisedExecutor {

    /** 우선순위: 숫자가 작을수록 높다. COMPLETING(-1)은 끝나 가는 일에만 쓰는 값이라 고를 수 없다. */
    enum Priority {
        COMPLETING(-1), BLOCKING, HIGHEST, HIGHER, HIGH, NORMAL, LOW, LOWER, LOWEST, IDLE;

        static final Priority[] PRIORITIES = values();
        public static final int TOTAL_PRIORITIES = PRIORITIES.length;
        public static final int TOTAL_SCHEDULABLE_PRIORITIES = TOTAL_PRIORITIES - 1;

        private static int priorityCounter;

        public final int priority;

        Priority() { this(nextCounter()); }

        Priority(int priority) { this.priority = priority; }

        private static int nextCounter() { return priorityCounter++; }

        public static boolean isValidPriority(Priority priority) { return priority != null && priority != COMPLETING; }

        public static Priority max(Priority a, Priority b) { return a.isHigherOrEqualPriority(b) ? a : b; }

        public static Priority min(Priority a, Priority b) { return a.isLowerOrEqualPriority(b) ? a : b; }

        public boolean isHigherOrEqualPriority(Priority than) { return priority <= than.priority; }

        public boolean isHigherPriority(Priority than) { return priority < than.priority; }

        public boolean isLowerOrEqualPriority(Priority than) { return priority >= than.priority; }

        public boolean isLowerPriority(Priority than) { return priority > than.priority; }

        public boolean isHigherOrEqualPriority(int than) { return priority <= than; }

        public boolean isHigherPriority(int than) { return priority < than; }

        public boolean isLowerOrEqualPriority(int than) { return priority >= than; }

        public boolean isLowerPriority(int than) { return priority > than; }

        public static boolean isHigherOrEqualPriority(int priority, int than) { return priority <= than; }

        public static boolean isHigherPriority(int priority, int than) { return priority < than; }

        public static boolean isLowerOrEqualPriority(int priority, int than) { return priority >= than; }

        public static boolean isLowerPriority(int priority, int than) { return priority > than; }

        public static Priority getPriority(int priority) { return PRIORITIES[priority + 1]; }
    }
}
