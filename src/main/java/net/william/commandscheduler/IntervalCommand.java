package net.william.commandscheduler;

import java.util.List;

public class IntervalCommand implements ScheduledCommandInfo {
    private String ID;
    private boolean active;
    private String command;
    private int interval;
    private TimeUnit unit;
    private String description;
    private int tickCounter;

    private boolean runInstantly = false;
    private boolean hasRun = false;

    public IntervalCommand() {
        resetTickCounter();
    }

    public IntervalCommand(String ID, String command, int interval, String unit, boolean runInstantly) {
        resetTickCounter();
        this.setID(ID);
        this.setCommand(command);
        this.setInterval(interval);
        this.setUnit(unit);
        this.setActive(true);
        this.setDescription("");
        this.setRunInstantly(runInstantly);
    }

    public static List<IntervalCommand> defaultList() {
        IntervalCommand cmd = new IntervalCommand();
        cmd.setID("test");
        cmd.setActive(true);
        cmd.setCommand("say This is a fallback interval command");
        cmd.setInterval(60);
        cmd.setUnit("seconds");
        cmd.setRunInstantly(false);
        cmd.setDescription("this is a fallback description");
        return List.of(cmd);
    }

    public boolean setID(String ID) {
        if (ID.matches("^[a-zA-Z0-9._-]+$")) {
            this.ID = ID;
            return true;
        }
        return false;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public TimeUnit getUnit() {
        return unit;
    }

    public boolean setUnit(String unitStr) {
        try {
            this.unit = TimeUnit.fromString(unitStr);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean shouldRunInstantly() {
        return runInstantly;
    }

    public void setRunInstantly(boolean runInstantly) {
        this.runInstantly = runInstantly;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String desc) {
        this.description = desc;
    }

    public static boolean isValidID(String id) {
        // Example: no spaces, only alphanumeric, dash or underscore
        return id != null && id.matches("^[a-zA-Z0-9_-]+$");
    }

    public static boolean isValidCommand(String command) {
        // Basic null/empty check — you could expand this depending on server command
        // rules
        return command != null && !command.trim().isEmpty();
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

    @Override
    public String getID() {
        return this.ID;
    }

    @Override
    public boolean isActive() {
        return this.active;
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

}