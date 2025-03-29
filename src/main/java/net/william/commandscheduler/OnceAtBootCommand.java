package net.william.commandscheduler;

import java.util.List;

public class OnceAtBootCommand extends BaseScheduledCommand {
    private transient boolean expired = false;

    public OnceAtBootCommand(String ID, String command) {
        super(ID, true, command);

    }

    public static List<OnceAtBootCommand> defaultList() {
        OnceAtBootCommand cmd = new OnceAtBootCommand("FallbackOnceAtBootScheduler",
                "say this is a fallback onceAtBoot scheduler");
        cmd.setDescription("this is a fallback description");
        return List.of(cmd);
    }

    public boolean isExpired() {
        return expired;
    }

    public void setExpired() {
        this.expired = true;
    }

    @Override
    public String toString() {
        return String.format(
                "OnceAtBootCommand{id='%s', active=%s, command='%s'}",
                ID, active, command, expired);
    }

}
