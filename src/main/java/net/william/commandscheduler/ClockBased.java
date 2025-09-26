package net.william.commandscheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ClockBased extends Scheduler {

    private List<int[]> times = new ArrayList<>();

    private List<String> commands;                // flat list
    private List<List<String>> commandGroups;     // grouped commands
    private boolean random;                       // random selection

    private transient int lastRunHour = -1;
    private transient int lastRunMinute = -1;

    // --- Constructors ---
    public ClockBased(String ID, String command) {
        super(ID, true, command);
        this.commands = new ArrayList<>();
        this.commandGroups = new ArrayList<>();
        this.random = false;
    }

    public ClockBased(String ID, List<String> commands, List<List<String>> commandGroups,
                      boolean random) {
        super(ID, true, (commands != null && !commands.isEmpty()) ? commands.get(0) :
                (commandGroups != null && !commandGroups.isEmpty() && !commandGroups.get(0).isEmpty() ? commandGroups.get(0).get(0) : "")
        );
        this.commands = (commands != null) ? new ArrayList<>(commands) : new ArrayList<>();
        this.commandGroups = (commandGroups != null) ? new ArrayList<>(commandGroups) : new ArrayList<>();
        this.random = random;
    }

    // --- Times ---
    public List<int[]> getTimes() {
        return times;
    }

    public static boolean isValidTimeString(String time) {
        if (time == null || !time.matches("^\\d{2}\\.\\d{2}$")) // Ensure the format is `hh.mm`
            return false;

        String[] parts = time.split("\\.");
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public boolean addTime(int hour, int minute) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59)
            return false;

        for (int[] t : times) {
            if (t[0] == hour && t[1] == minute)
                return false; // already exists
        }

        times.add(new int[]{hour, minute});
        return true;
    }

    public boolean removeTime(int hour, int minute) {
        return times.removeIf(t -> t[0] == hour && t[1] == minute);
    }

    // --- Random + command handling ---
    public String getCommand() {
        if (commands != null && !commands.isEmpty()) {
            if (random) {
                return commands.get(new Random().nextInt(commands.size()));
            }
            return commands.get(0);
        }

        if (commandGroups != null && !commandGroups.isEmpty()) {
            List<String> group = random
                    ? commandGroups.get(new Random().nextInt(commandGroups.size()))
                    : commandGroups.get(0);
            return group.isEmpty() ? super.getCommand() : group.get(0);
        }

        return super.getCommand(); // ultimate fallback
    }

    public List<String> getCommands() {
        return commands;
    }

    public List<String> getCommandGroup() {
        if (commandGroups != null && !commandGroups.isEmpty()) {
            if (random) {
                return commandGroups.get(new Random().nextInt(commandGroups.size()));
            }
            return commandGroups.get(0);
        }

        // fallback: single-command mode
        List<String> single = new ArrayList<>();
        single.add(getCommand());
        return single;
    }

    public List<List<String>> getCommandGroups() {
        return commandGroups;
    }

    public boolean isRandom() {
        return random;
    }

    // --- Execution tracking ---
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
        return String.format(
                "ClockBasedCommand{id='%s', active=%s, times=%s, random=%s, commands=%s, groups=%s}",
                ID, active, times, random, commands, commandGroups
        );
    }
}
