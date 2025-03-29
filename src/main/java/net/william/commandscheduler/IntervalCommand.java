package net.william.commandscheduler;

import java.util.List;

public class IntervalCommand extends BaseScheduledCommand {

    private TimeUnit unit;
    private int interval;
    private int tickCounter;

    private boolean runInstantly = false;
    private boolean hasRun = false;

    public IntervalCommand(String ID, String command, int interval, String unit, boolean runInstantly) {
        super(ID, true, command);
        this.setInterval(interval);
        this.setUnit(unit);
        this.setRunInstantly(runInstantly);
        resetTickCounter();
    }

    public static List<IntervalCommand> defaultList() {
        IntervalCommand cmd = new IntervalCommand("fallbackIntervalScheduler",
                "say this is a fallback interval scheduler", 1, "seconds", false);
        cmd.setDescription(
                "This is a fallback description. A bug has likely happened, as this scheduler should not exist!");
        return List.of(cmd);
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