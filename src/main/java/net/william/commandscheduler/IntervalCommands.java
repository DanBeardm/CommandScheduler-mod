package net.william.commandscheduler;

import com.google.gson.annotations.Expose;
import java.util.List;

public class IntervalCommands {
    private String ID;
    private boolean active;
    private String command;
    private int interval;
    public static final List<String> ALLOWED_UNITS = List.of("ticks", "seconds", "minutes", "hours");
    private String unit = "ticks"; // ticks, seconds, minutes, hours
    private String description;

    private boolean run_at_start = false;

    @Expose(deserialize = false, serialize = false)
    public int tickCounter = 0;

    public static List<IntervalCommands> defaultList() {
        IntervalCommands cmd = new IntervalCommands();
        cmd.ID = "test";
        cmd.active = true;
        cmd.command = "say This is a fallback interval command";
        cmd.interval = 60;
        cmd.unit = "seconds";
        cmd.run_at_start = false;
        cmd.setDescription("this is a description");
        return List.of(cmd);
    }

    public String getID() {
        return ID;
    }

    public boolean setID(String ID) {
        if (ID.matches("^[a-zA-Z0-9._-]+$")) {
            this.ID = ID;
            return true;
        }
        return false;
    }

    public boolean isActive() {
        return active;
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

    public String getUnit() {
        return unit;
    }

    public boolean setUnit(String unit) {
        if (ALLOWED_UNITS.contains(unit)) {
            this.unit = unit;
        }
        return false;
    }

    public boolean shouldRunAtStart() {
        return run_at_start;
    }

    public void setRun_at_start(boolean run_at_start) {
        this.run_at_start = run_at_start;
    }

    public int getTickCounter() {
        return tickCounter;
    }

    public void setTickCounter(int tickCounter) {
        this.tickCounter = tickCounter;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String desc) {
        this.description = desc;
    }

}