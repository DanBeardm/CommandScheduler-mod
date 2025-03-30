package net.william.commandscheduler;

public enum Types {

    ATBOOT("atboot"),
    INTERVAL("interval"),
    CLOCKBASED("clock");

    public final String name;

    private Types(String name) {
        this.name = name;
    }
}
