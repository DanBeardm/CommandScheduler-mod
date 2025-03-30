package net.william.commandscheduler;

public class Interval extends Scheduler {

    private TimeUnit unit;
    private int interval;
    private boolean runInstantly = false;

    private transient int tickCounter;
    private transient boolean hasRun = false;

    public Interval(String ID, String command, int interval, String unit, boolean runInstantly) {
        super(ID, true, command);
        this.setInterval(interval);
        this.setUnit(unit);
        this.setRunInstantly(runInstantly);
        resetTickCounter();
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) throws IllegalArgumentException {
        if (!isValidInterval(interval)) {
            throw new IllegalArgumentException("Interval must be positive.");
        }
        this.interval = interval;
    }

    public TimeUnit getUnit() {
        return unit;
    }

    public void setUnit(String unitStr) throws IllegalArgumentException {
        this.unit = TimeUnit.fromString(unitStr);
    }

    public boolean shouldRunInstantly() {
        return runInstantly;
    }

    public void setRunInstantly(boolean runInstantly) {
        this.runInstantly = runInstantly;
    }

    public static boolean isValidInterval(int interval) {
        // Must be positive
        return interval > 0;
    }

    public void resetTickCounter() {
        tickCounter = 0;
    }

    public int getTickCounter() {
        return tickCounter;
    }

    public void tick() {
        tickCounter++;
    }

    public void run() {
        this.hasRun = true;
        this.resetTickCounter();
    }

    public boolean hasRan() {
        return hasRun;
    }

    public void fastForwardUntilNextRun() {
        tickCounter = TimeUnit.getTickCountForUnits(unit, interval);
    }

    @Override
    public String toString() {
        return String.format(
                "IntervalCommand{id='%s', active=%s, interval=%d, unit=%s, runAtStart=%s}",
                ID, active, interval, unit.toString().toLowerCase(), runInstantly);
    }

}