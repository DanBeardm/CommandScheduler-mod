package net.william.commandscheduler;

import java.util.List;

public class OnceAtBootCommand implements ScheduledCommandInfo {
    private String ID;
    private boolean active;
    private String command;
    private String description;
    private transient boolean expired = false;

    public static List<OnceAtBootCommand> defaultList() {
        OnceAtBootCommand cmd = new OnceAtBootCommand();
        cmd.setID("fallback2");
        cmd.setCommand("say This is a fallback once command");
        cmd.setDescription("this is a fallback description");
        cmd.setActive(true);
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

    @Override
    public String getID() {
        return this.ID;
    }

    @Override
    public boolean isActive() {
        return this.active;
    }

}
