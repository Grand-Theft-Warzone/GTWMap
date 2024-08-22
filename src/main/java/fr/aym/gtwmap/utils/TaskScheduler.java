package fr.aym.gtwmap.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A simple delayed tasks scheduler <br>
 * Tasks are executed in the server thread in single player and dedicated servers
 *
 * @see ScheduledTask
 */
public class TaskScheduler {
    private static final ConcurrentLinkedQueue<ScheduledTask> tasks = new ConcurrentLinkedQueue<>();

    public static void schedule(ScheduledTask task) {
        tasks.add(task);
    }

    public static void tick() {
        if (!tasks.isEmpty()) {
            List<ScheduledTask> rm = new ArrayList<>();
            tasks.forEach(t -> {
                if (t.execute())
                    rm.add(t);
            });
            rm.forEach(tasks::remove);
        }
    }

    /**
     * A delayed Runnable to execute with the {@link TaskScheduler}
     */
    public abstract static class ScheduledTask implements Runnable {
        private short timeLeft;

        /**
         * @param timeLeft The delay in ticks
         */
        public ScheduledTask(short timeLeft) {
            this.timeLeft = timeLeft;
        }

        public boolean execute() {
            timeLeft--;
            if (timeLeft <= 0) {
                this.run();
                return true;
            }
            return false;
        }
    }
}
