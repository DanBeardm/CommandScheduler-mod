package net.william.commandscheduler;

import java.util.ArrayList;
import java.util.List;

public class ClockBasedCommands {
    private String ID;
    private boolean active;
    private String command;
    private List<int[]> times = new ArrayList<>();
    private String description;
    private int lastRunHour = -1;
    private int lastRunMinute = -1;

    public static List<ClockBasedCommands> defaultList() {
        ClockBasedCommands cmd = new ClockBasedCommands();
        cmd.setID("test1");
        cmd.setActive(true);
        cmd.setCommand("say This is a fallback clock-based command");
        cmd.addTime(0, 0);
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

    public List<int[]> getTimes() {
        return times;
    }

    public boolean addTime(int hour, int minute) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59)
            return false;

        for (int[] t : times) {
            if (t[0] == hour && t[1] == minute)
                return false; // already exists
        }

        times.add(new int[] { hour, minute });
        return true;
    }

    public boolean removeTime(int hour, int minute) {
        return times.removeIf(t -> t[0] == hour && t[1] == minute);
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String desc) {
        this.description = desc;
    }

    public int getLastRunHour() {
        return lastRunHour;
    }

    public int getLastRunMinute() {
        return lastRunMinute;
    }

    public void setLastRunTime(int hour, int minute) {
        this.lastRunHour = hour;
        this.lastRunMinute = minute;
    }
}
