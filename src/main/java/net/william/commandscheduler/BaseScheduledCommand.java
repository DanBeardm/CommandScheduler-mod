package net.william.commandscheduler;

public abstract class BaseScheduledCommand implements ScheduledCommandInfo {

    protected String ID;
    protected boolean active = true;
    protected String command;
    protected String description = "";

    public BaseScheduledCommand(String ID, boolean active, String command) {
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

    public static boolean isValidCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return false;
        }

        String trimmed = command.trim();
        return !trimmed.equalsIgnoreCase("stop") && !trimmed.equalsIgnoreCase("/stop");
    }
}