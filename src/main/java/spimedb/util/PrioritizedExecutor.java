package spimedb.util;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;

/**
 * Created by me on 2/3/17.
 */
public class PrioritizedExecutor implements Executor {

    final static Logger logger = LoggerFactory.getLogger(PrioritizedExecutor.class);

    private static final float DEFAULT_PRIORITY = 0.5f;
    private static final long DEFAULT_TIMEOUT_ms = 30 * 1000; //30 SECONDS

    public final PriorityBlockingQueue pq = new PriorityBlockingQueue<>(
            64 * 1024,
            runCompare);

    private final Executor exe;

    public PrioritizedExecutor(int threads) {
        //similar to Fixed-Size threadpool
        this.exe = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS, pq);

        Runtime.getRuntime().addShutdownHook(new Thread(this::onShutDown));
    }

    protected void onShutDown() {
        pq.forEach(x -> {
            logger.info("not executed: {}", x);
        });
    }

    public void run(float pri, Runnable r) {
        run(new MyRunWithPriority(pri, r, DEFAULT_TIMEOUT_ms));
    }

    public void run(RunWithPriority r) {
        exe.execute(r);
    }

    @Override
    public void execute(@NotNull Runnable command) {
        run(DEFAULT_PRIORITY, command);
    }


    public interface RunWithPriority extends Runnable {
        float pri();
    }

    static final Comparator<RunWithPriority> runCompare = new Comparator<RunWithPriority>() {

        @Override
        public int compare(RunWithPriority x, RunWithPriority y) {
            if (x == y) return 0;

            int c = Float.compare(y.pri(), x.pri());
            if (c == 0) {
                return Integer.compare(x.hashCode(), y.hashCode());
            }
            return c;
        }
    };

    final Timer timer = new Timer(true);

    class MyRunWithPriority implements RunWithPriority {
        private final float pri;
        private final Runnable r;
        private final long timeoutMS;

        public MyRunWithPriority(float pri, Runnable r, long timeoutMS) {
            this.pri = pri;
            this.timeoutMS = timeoutMS;
            this.r = r;
        }

        @Override
        public float pri() {
            return pri;
        }

        @Override
        public void run() {

            TimeOutTask timeout = new TimeOutTask(Thread.currentThread());
            timer.schedule(timeout, timeoutMS);

            try {
                r.run();
            } catch (ThreadDeath ie) {
                logger.error("{} interrupted {}", r, ie.getMessage());
            }

            timeout.cancel();

        }
    }


    static class TimeOutTask extends TimerTask {
        final Thread t;

        TimeOutTask(Thread t) {
            this.t = t;
        }

        public void run() {
            if (t != null && t.isAlive()) {
                t.stop();
            }
        }
    }


//    public static void main(String args[]) {
//        new PoolTest();
//    }

//        public PoolTest()
//        {
//            try
//            {
//                PooledExecutor pe = new PooledExecutor(3);
//                pe.execute(new MyRunnable());
//                pe.shutdownAfterProcessingCurrentlyQueuedTasks();
//            }
//            catch (Exception e)
//            {
//                e.printStackTrace();
//            }
//        }

}
