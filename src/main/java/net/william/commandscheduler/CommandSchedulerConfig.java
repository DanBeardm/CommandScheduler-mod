package net.william.commandscheduler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
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
  public static Path onceAtBootPath;

  private static final Path CONFIG_PATH = Paths.get("config", "commandscheduler");

  private static final Gson gson = new GsonBuilder()
      .registerTypeAdapter(TimeUnit.class, (JsonDeserializer<TimeUnit>) (json, typeOfT, context) -> {
        String value = json.getAsString();
        return TimeUnit.fromString(value);
      })
      .setPrettyPrinting()
      .disableHtmlEscaping()
      .create();

  private static final Logger LOGGER = LoggerFactory.getLogger("CommandScheduler");

  private static final Type INTERVAL_TYPE = new TypeToken<List<IntervalCommand>>() {
  }.getType();
  private static final Type CLOCKBASED_TYPE = new TypeToken<List<ClockBasedCommand>>() {
  }.getType();
  private static final Type ONCE_TYPE = new TypeToken<List<OnceAtBootCommand>>() {
  }.getType();

  private static List<IntervalCommand> intervalCommands = new ArrayList<>();
  private static List<ClockBasedCommand> clockBasedCommands = new ArrayList<>();
  private static List<OnceAtBootCommand> onceAtBootCommands = new ArrayList<>();

  public static void loadAllCommands() {
    intervalCommands = loadIntervalCommands();
    clockBasedCommands = loadClockBasedCommands();
    onceAtBootCommands = loadOnceAtBootCommands();
  }

  public static List<IntervalCommand> loadIntervalCommands() {
    intervalPath = CONFIG_PATH.resolve("intervals.json5");
    List<IntervalCommand> list = loadConfig("intervals.json5", INTERVAL_TYPE, IntervalCommand.defaultList());
    if (checkForDuplicateIDs(list, "intervals")) {
      saveIntervalCommands();
    }
    return list;
  }

  public static List<ClockBasedCommand> loadClockBasedCommands() {
    clockPath = CONFIG_PATH.resolve("clock_based.json5");

    List<ClockBasedCommand> list = loadConfig("clock_based.json5", CLOCKBASED_TYPE, ClockBasedCommand.defaultList());

    // Sort the times for each loaded command
    for (ClockBasedCommand cc : list) {
      cc.getTimes().sort((a, b) -> {
        int cmp = Integer.compare(a[0], b[0]); // compare hours
        return cmp != 0 ? cmp : Integer.compare(a[1], b[1]); // if equal, compare minutes
      });
    }

    clockBasedCommands = list;
    saveClockBasedCommands();

    return list;
  }

  public static List<OnceAtBootCommand> loadOnceAtBootCommands() {
    onceAtBootPath = CONFIG_PATH.resolve("once_at_boot.json5");
    List<OnceAtBootCommand> list = loadConfig("once_at_boot.json5", ONCE_TYPE, OnceAtBootCommand.defaultList());
    if (checkForDuplicateIDs(list, "once_at_boot")) {
      saveOnceAtBootCommands();
    }
    return list;
  }

  public static void reloadConfigs() {
    intervalCommands = loadIntervalCommands();
    clockBasedCommands = loadClockBasedCommands();
    onceAtBootCommands = loadOnceAtBootCommands();
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
      if (loaded == null)
        return defaultList;

      // Validate and filter entries
      List<T> validEntries = new ArrayList<>();
      for (T entry : loaded) {
        try {
          if (entry instanceof IntervalCommand ic) {
            if (!IntervalCommand.isValidInterval(ic.getInterval())) {
              LOGGER.error("Skipping invalid interval for ID '{}': {}", ic.getID(), ic.getInterval());
              continue;
            }
          }
          validEntries.add(entry);
        } catch (Exception e) {
          LOGGER.error("Skipping invalid entry in {}: {}", fileName, e.getMessage());
        }
      }
      return validEntries.isEmpty() ? defaultList : validEntries;
    } catch (Exception e) {
      LOGGER.error("Failed to load {}: {}", fileName, e.getMessage());
      return defaultList;
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
            LOGGER.warn("Renamed duplicate ID '{}' to '{}' in {}", originalID, newID, typeName);
            seen.put(originalID, suffix - 1);
            seen.put(newID, 0);
            renamedAny = true;
          } else {
            seen.put(originalID, 0);
          }
        }
      } catch (Exception e) {
        LOGGER.warn("Failed to access ID in {}: {}", typeName, e.getMessage());
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
                "ID": "ExampleIntervalCommand",
                "description": "This is the description for the 'interval' scheduler example. This runs every minute.",
                "active": true,
                "command": "say This command runs every minute! Via the CommandScheduler mod. If you are a server moderator, you should probably update this. Run /commandscheduler",
                "interval": 1,
                // units are ticks, seconds, minutes, hours or days
                "unit": "minutes",
                // if the command should run once as the timer starts or not
                "run_at_start": false
              },
              {
                "ID": "ExampleIntervalCommand2",
                "description": "This is the description for the second 'interval' scheduler example. This runs every 7 game ticks, however is deactivated by default.",
                "active": false,
                "command": "say This runs every 7 game ticks. To deactivate, run /commandscheduler deactivate ExampleIntervalCommand2",
                "interval": 7,
                // units are ticks, seconds, minutes, hours or days
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
                "ID": "ExampleClockBasedCommand",
                "description": "This is the description for the 'clock-based' scheduler example. This runs at 01.00 and at 13.00.",
                "active": false,
                "command": "say The time is either 01.00 or 13.00! (commandscheduler mod)",
                // Use 24h format: HH.mm
                "times": [[1, 0], [13, 0]]
              }
            ]
            """;
      case "once_at_boot.json5" ->
        """
            [
              {
                "ID": "ExampleAtBootCommand",
                "description": "This is the description for the 'at boot' scheduler example. This runs every time the server boots",
                "active": false,
                "command": "say The server has booted! (commandscheduler mod)"
              }
            ]
            """;
      default -> throw new IllegalArgumentException("Unknown config file: " + fileName);
    };

    Files.writeString(path, commentedJson, StandardCharsets.UTF_8);
  }

  public static boolean removeCommandById(String id) {
    boolean removed = false;

    removed |= intervalCommands.removeIf(cmd -> cmd.getID().equals(id));
    removed |= clockBasedCommands.removeIf(cmd -> cmd.getID().equals(id));
    removed |= onceAtBootCommands.removeIf(cmd -> cmd.getID().equals(id));

    if (removed) {
      saveIntervalCommands();
      saveClockBasedCommands();
      saveOnceAtBootCommands();
    }

    return removed;
  }

  public static Object getCommandById(String id) {
    for (IntervalCommand ic : intervalCommands) {
      if (ic.getID().equals(id))
        return ic;
    }
    for (ClockBasedCommand cc : clockBasedCommands) {
      if (cc.getID().equals(id))
        return cc;
    }
    for (OnceAtBootCommand oc : onceAtBootCommands) {
      if (oc.getID().equals(id))
        return oc;
    }
    return null;
  }

  public static boolean updateSchedulerId(String oldId, String newId) {
    Object cmd = getCommandById(oldId);
    if (cmd == null || !BaseScheduledCommand.isValidID(newId))
      return false;

    boolean success = false;

    if (cmd instanceof IntervalCommand ic) {
      success = ic.setID(newId);
      if (success)
        saveIntervalCommands();
    } else if (cmd instanceof ClockBasedCommand cc) {
      success = cc.setID(newId);
      if (success)
        saveClockBasedCommands();
    } else if (cmd instanceof OnceAtBootCommand oc) {
      success = oc.setID(newId);
      if (success)
        saveOnceAtBootCommands();
    }

    return success;
  }

  public static void saveIntervalCommands() {
    saveConfig(intervalPath, intervalCommands);
  }

  public static void saveClockBasedCommands() {
    // Sort the times in each ClockBasedCommand before saving
    for (ClockBasedCommand cc : clockBasedCommands) {
      cc.getTimes().sort((time1, time2) -> {
        int hourComparison = Integer.compare(time1[0], time2[0]);
        if (hourComparison != 0) {
          return hourComparison; // Sort by hour first
        }
        return Integer.compare(time1[1], time2[1]); // Then by minute
      });
    }

    saveConfig(clockPath, clockBasedCommands);
  }

  public static void saveOnceAtBootCommands() {
    saveConfig(onceAtBootPath, onceAtBootCommands);
  }

  public static List<ClockBasedCommand> getClockBasedCommands() {
    return Collections.unmodifiableList(clockBasedCommands);
  }

  public static List<IntervalCommand> getIntervalCommands() {
    return Collections.unmodifiableList(intervalCommands);
  }

  public static List<OnceAtBootCommand> getOnceAtBootCommands() {
    return Collections.unmodifiableList(onceAtBootCommands);
  }

  public static void addClockBasedCommand(ClockBasedCommand command) {
    clockBasedCommands.add(command);
  }

  public static void addIntervalCommand(IntervalCommand command) {
    intervalCommands.add(command);
  }

  public static void addOnceAtBootCommand(OnceAtBootCommand command) {
    onceAtBootCommands.add(command);
  }

  public static List<String> getAllSchedulerIDs() {
    List<String> ids = new ArrayList<>();
    for (ScheduledCommandInfo cmd : getIntervalCommands()) {
      ids.add(cmd.getID());
    }
    for (ScheduledCommandInfo cmd : getClockBasedCommands()) {
      ids.add(cmd.getID());
    }
    for (ScheduledCommandInfo cmd : getOnceAtBootCommands()) {
      ids.add(cmd.getID());
    }
    return ids;
  }

  public static List<String> getClockBasedSchedulerIDs() {
    List<String> ids = new ArrayList<>();
    for (ScheduledCommandInfo cmd : getClockBasedCommands()) {
      ids.add(cmd.getID());
    }
    return ids;
  }

}