package net.william.commandscheduler;

import java.util.List;
import java.util.ArrayList;

public class Interval extends Scheduler {

    private TimeUnit unit;
    private int interval;
    private boolean random;
    private boolean runInstantly = false;
    private List<List<String>> commandGroups; // ✅ Only groups now

    private transient int tickCounter;
    private transient boolean hasRun = false;

    // Legacy single command constructor
    public Interval(String ID, String command, int interval, String unit, boolean runInstantly) {
        super(ID, true, command);
        this.commandGroups = new ArrayList<>();
        this.commandGroups.add(List.of(command)); // wrap single command into a group
        this.random = false;
        this.setInterval(interval);
        this.setUnit(unit);
        this.setRunInstantly(runInstantly);
        resetTickCounter();
    }

    // Modern grouped commands constructor
    public Interval(String ID, List<List<String>> commandGroups, int interval, String unit, boolean runInstantly, boolean random) {
        super(ID, true, (commandGroups.isEmpty() || commandGroups.get(0).isEmpty()) ? "" : commandGroups.get(0).get(0));
        this.commandGroups = new ArrayList<>(commandGroups);
        this.random = random;
        this.setInterval(interval);
        this.setUnit(unit);
        this.setRunInstantly(runInstantly);
        resetTickCounter();
    }
    // ✅ No more stray "commands" field
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

    // Pick a group (random or first)
    public java.util.List<String> getCommandGroup() {
        if (commandGroups != null && !commandGroups.isEmpty()) {
            if (random) {
                return commandGroups.get(new java.util.Random().nextInt(commandGroups.size()));
            }
            return commandGroups.get(0);
        }
        return new java.util.ArrayList<>();
    }

    public boolean isRandom() {
        return random;
    }

    @Override
    public String toString() {
        return String.format(
                "IntervalCommand{id='%s', active=%s, interval=%d, unit=%s, runAtStart=%s, random=%s, groups=%s}",
                ID, active, interval, unit.toString().toLowerCase(), runInstantly, random, commandGroups
        );
    }
}
