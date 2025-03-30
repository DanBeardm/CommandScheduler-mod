package net.william.commandscheduler;

import java.util.ArrayList;
import java.util.List;

public abstract class Scheduler {

    protected String ID;
    protected boolean active = true;
    protected String command;
    protected String description = "";

    public Scheduler(String ID, boolean active, String command) {
        if (!setID(ID)) {
            throw new IllegalArgumentException("Invalid ID: " + ID);
        }
        this.setActive(active);
        this.setCommand(command);
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

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public boolean isActive() {
        return active;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = (description != null) ? description : "";
    }

    public static boolean isValidID(String id) {
        return id != null && id.matches("^[a-zA-Z0-9._-]+$");
    }

    private static final List<String> INVALID_COMMANDS = new ArrayList<>();

    static {
        INVALID_COMMANDS.add("stop");
        // Add more as needed
    }

    public static boolean isValidCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return false;
        }

        String trimmed = command.trim();

        if (INVALID_COMMANDS.contains(trimmed) || INVALID_COMMANDS.contains("/" + trimmed)) {
            return false;
        }
        return true;
    }

}