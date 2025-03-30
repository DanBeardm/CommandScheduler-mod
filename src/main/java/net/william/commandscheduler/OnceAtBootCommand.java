package net.william.commandscheduler;

public class OnceAtBootCommand extends BaseScheduledCommand {
    private transient boolean expired = false;

    public OnceAtBootCommand(String ID, String command) {
        super(ID, true, command);

    }

    public boolean isExpired() {
        return expired;
    }

    public void setExpired() {
        this.expired = true;
    }

    @Override
    public String toString() {
        return String.format("OnceAtBootCommand{id='%s', active=%s, command='%s', expired=%s}", getID(), isActive(),
                getCommand(), expired);

    }

}
