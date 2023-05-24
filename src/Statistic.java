import java.util.concurrent.atomic.AtomicInteger;

public class Statistic {
    private String ip;
    private AtomicInteger delayCount;
    private AtomicInteger dropCount;

    public Statistic(String ip) {
        this.ip = ip;
        this.delayCount = new AtomicInteger(0);
        this.dropCount = new AtomicInteger(0);
    }

    public int getDelayCount() {
        return delayCount.get();
    }

    public int getDropCount() {
        return dropCount.get();
    }

    public void incrementDelayCount() {
        this.delayCount.incrementAndGet();
    }

    public void incrementDropCount() {
        this.dropCount.incrementAndGet();
    }
}
