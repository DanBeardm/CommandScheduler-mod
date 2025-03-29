package net.william.commandscheduler;

import java.util.ArrayList;
import java.util.List;

public class ClockBasedCommand extends BaseScheduledCommand {

    private List<int[]> times = new ArrayList<>();
    private int lastRunHour = -1;
    private int lastRunMinute = -1;

    public ClockBasedCommand(String ID, String command) {
        super(ID, true, command);
    }

    public static List<ClockBasedCommand> defaultList() {
        ClockBasedCommand cmd = new ClockBasedCommand("fallbackClock-basedScheduler",
                "say this is a fallback clock-based scheduler");
        cmd.addTime(0, 0);
        cmd.setDescription(
                "This is a fallback description. A bug has likely happened, as this scheduler should not exist!");
        return List.of(cmd);
    }

    public List<int[]> getTimes() {
        return times;
    }

    public static boolean isValidTimeString(String time) {
        if (time == null || !time.matches("^\\d{2}\\.\\d{2}$")) // Ensure the format is `hh.mm`
            return false;

        String[] parts = time.split("\\."); // Split based on period
        try {
            int hour = Integer.parseInt(parts[0]); // Parse hour
            int minute = Integer.parseInt(parts[1]); // Parse minute
            return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59; // Check if valid time
        } catch (NumberFormatException e) {
            return false; // Handle invalid numbers
        }
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

    public int getLastRunHour() {
        return lastRunHour;
    }

    public int getLastRunMinute() {
        return lastRunMinute;
    }

    public boolean run(int hour, int minute) {
        if (this.lastRunHour == hour && this.lastRunMinute == minute) {
            return false; // Already ran this minute
        }

        this.lastRunHour = hour;
        this.lastRunMinute = minute;
        return true; // New execution
    }

    @Override
    public String toString() {
        return String.format("ClockBasedCommand{id='%s', active=%s, times=%s}", ID, active, times);
    }
}
