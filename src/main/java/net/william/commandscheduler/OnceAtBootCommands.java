package net.william.commandscheduler;

import java.util.List;

public class OnceAtBootCommands {
    private String ID;
    private boolean active;
    private String command;
    private String description;
    private boolean expired = false;

    public static List<OnceAtBootCommands> defaultList() {
        OnceAtBootCommands cmd = new OnceAtBootCommands();
        cmd.ID = "test2";
        cmd.active = true;
        cmd.command = "say This is a fallback once command";
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

    public boolean isExpired() {
        return expired;
    }

    public void setExpired() {
        this.expired = true;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String desc) {
        this.description = desc;
    }

}
