package net.william.commandscheduler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Writer;

public class CommandSchedulerConfig {

  public static Path intervalPath;
  public static Path clockPath;
  public static Path oncePath;

  private static final Path CONFIG_PATH = Paths.get("config", "commandscheduler");
  private static final Gson gson = new GsonBuilder()
      .setPrettyPrinting()
      .disableHtmlEscaping()
      .create();

  private static final Logger LOGGER = LoggerFactory.getLogger("CommandScheduler");

  private static final Type INTERVAL_TYPE = new TypeToken<List<IntervalCommands>>() {
  }.getType();
  private static final Type CLOCKBASED_TYPE = new TypeToken<List<ClockBasedCommands>>() {
  }.getType();
  private static final Type ONCE_TYPE = new TypeToken<List<OnceAtBootCommands>>() {
  }.getType();

  public static List<IntervalCommands> intervalCommands = new ArrayList<>();
  public static List<ClockBasedCommands> clockBasedCommands = new ArrayList<>();
  public static List<OnceAtBootCommands> onceCommands = new ArrayList<>();

  public static List<IntervalCommands> loadIntervalCommands() {
    intervalPath = CONFIG_PATH.resolve("intervals.json5");
    List<IntervalCommands> list = loadConfig("intervals.json5", INTERVAL_TYPE, IntervalCommands.defaultList());
    if (checkForDuplicateIDs(list, "intervals")) {
      saveIntervalCommands();
    }
    return list;
  }

  public static List<ClockBasedCommands> loadClockBasedCommands() {
    clockPath = CONFIG_PATH.resolve("clock_based.json5");
    List<ClockBasedCommands> list = loadConfig("clock_based.json5", CLOCKBASED_TYPE, ClockBasedCommands.defaultList());
    if (checkForDuplicateIDs(list, "clock_based")) {
      saveClockBasedCommands();
    }
    return list;
  }

  public static List<OnceAtBootCommands> loadOnceCommands() {
    oncePath = CONFIG_PATH.resolve("once_at_boot.json5");
    List<OnceAtBootCommands> list = loadConfig("once_at_boot.json5", ONCE_TYPE, OnceAtBootCommands.defaultList());
    if (checkForDuplicateIDs(list, "once_at_boot")) {
      saveOnceCommands();
    }
    return list;
  }

  public static void reloadConfigs() {
    intervalCommands = loadIntervalCommands();
    clockBasedCommands = loadClockBasedCommands();
    onceCommands = loadOnceCommands();
  }

  public static class ScheduledCommand {
    public String command;
    public int interval_ticks;
    public transient int tickCounter = 0; // Not saved
  }

  private static <T> List<T> loadConfig(String fileName, Type type, List<T> defaultList) {
    try {
      Path path = CONFIG_PATH.resolve(fileName);

      if (!Files.exists(path)) {
        Files.createDirectories(CONFIG_PATH);
        writeDefaultConfigWithComments(fileName);
        String json = Files.readString(path, StandardCharsets.UTF_8);
        List<T> loaded = gson.fromJson(json, type);
        return loaded != null ? loaded : defaultList;
      }

      String json = Files.readString(path, StandardCharsets.UTF_8);
      List<T> loaded = gson.fromJson(json, type);
      return loaded != null ? loaded : defaultList;

    } catch (Exception e) {
      e.printStackTrace();
      return Collections.emptyList();
    }
  }

  public static <T> void saveConfig(Path path, List<T> list) {
    try (Writer writer = Files.newBufferedWriter(path)) {
      gson.toJson(list, list.getClass(), writer);
    } catch (IOException e) {
      LOGGER.error("Failed to save config to " + path, e);
    }
  }

  private static <T> boolean checkForDuplicateIDs(List<T> list, String typeName) {
    var seen = new java.util.HashMap<String, Integer>();
    boolean renamedAny = false;

    for (T item : list) {
      try {
        var idField = item.getClass().getDeclaredField("ID");
        idField.setAccessible(true);
        Object idObj = idField.get(item);

        if (idObj instanceof String originalID) {
          String newID = originalID;
          if (seen.containsKey(originalID)) {
            int suffix = seen.get(originalID) + 1;
            do {
              newID = originalID + "." + suffix;
              suffix++;
            } while (seen.containsKey(newID));
            idField.set(item, newID);
            System.err.printf("[CommandScheduler] Renamed duplicate ID '%s' to '%s' in %s%n", originalID, newID,
                typeName);
            seen.put(originalID, suffix - 1);
            seen.put(newID, 0);
            renamedAny = true;
          } else {
            seen.put(originalID, 0);
          }
        }
      } catch (Exception e) {
        System.err.println("[CommandScheduler] Failed to access ID in " + typeName + ": " + e.getMessage());
      }
    }

    return renamedAny;
  }

  private static void writeDefaultConfigWithComments(String fileName) throws Exception {
    Path path = CONFIG_PATH.resolve(fileName);

    String commentedJson = switch (fileName) {
      case "intervals.json5" ->
        """
            [
              {
                "ID": "test",
                "description": "This is a description of an interval command",
                "active": false,
                "command": "say This is ran every 10 seconds.",
                "interval": 10,
                // units are ticks, seconds, minutes or hours
                "unit": "seconds",
                // if the command should run once as the timer starts or not
                "run_at_start": true
              },
              {
                "ID": "test1",
                "description": "This is a description of an interval command",
                "active": false,
                "command": "say This is ran every 2 hours.",
                "interval": 2,
                // units are ticks, seconds, minutes or hours
                "unit": "hours",
                // if the command should run once as the timer starts or not
                "run_at_start": false
              },
              {
                "ID": "test2",
                "description": "This is a description of an interval command",
                "active": false,
                "command": "say This is ran every 5 game ticks.",
                "interval": 5,
                // units are ticks, seconds, minutes or hours
                "unit": "ticks",
                // if the command should run once as the timer starts or not
                "run_at_start": false
              }
            ]
            """;
      case "clock_based.json5" ->
        """
            [
              {
                "ID": "test3",
                "description": "This is a description of a clock based command",
                "active": false,
                "command": "say It's 01:00 or 13:00!",
                // Use 24h format: HH:mm
                "times": [[1, 0], [13, 0]]
              }
            ]
            """;
      case "once_at_boot.json5" ->
        """
            [
              {
                "ID": "test4",
                "description": "This is a description of a command ran at boot",
                "active": true,
                "command": "say This command runs once at server start! Via the CommandScheduler mod. You should probably update this. run /commandscheduler"
              }
            ]
            """;
      default -> throw new IllegalArgumentException("Unknown config file: " + fileName);
    };

    Files.writeString(path, commentedJson, StandardCharsets.UTF_8);
  }

  public static Object getCommandById(String id) {
    for (IntervalCommands ic : intervalCommands) {
      if (ic.getID().equals(id))
        return ic;
    }
    for (ClockBasedCommands cc : clockBasedCommands) {
      if (cc.getID().equals(id))
        return cc;
    }
    for (OnceAtBootCommands oc : onceCommands) {
      if (oc.getID().equals(id))
        return oc;
    }
    return null;
  }

  public static void saveIntervalCommands() {
    saveConfig(intervalPath, intervalCommands);
  }

  public static void saveClockBasedCommands() {
    saveConfig(clockPath, clockBasedCommands);
  }

  public static void saveOnceCommands() {
    saveConfig(oncePath, onceCommands);
  }

}