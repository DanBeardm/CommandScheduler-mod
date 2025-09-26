package net.william.commandscheduler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigHandler {

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

  private static final Type INTERVAL_JSON_TYPE = new TypeToken<List<com.google.gson.JsonObject>>() {
  }.getType();
  private static final Type CLOCKBASED_TYPE = new TypeToken<List<ClockBased>>() {
  }.getType();
  private static final Type ONCE_TYPE = new TypeToken<List<AtBoot>>() {
  }.getType();

  private static List<Interval> intervalCommands = new ArrayList<>();
  private static List<ClockBased> clockBasedCommands = new ArrayList<>();
  private static List<AtBoot> onceAtBootCommands = new ArrayList<>();

  public static void loadAllCommands() {
    intervalCommands = loadIntervalCommands();
    clockBasedCommands = loadClockBasedCommands();
    onceAtBootCommands = loadOnceAtBootCommands();
  }

    public static List<Interval> loadIntervalCommands() {
        intervalPath = CONFIG_PATH.resolve("intervals.json5");
        List<com.google.gson.JsonObject> rawList = loadConfig("intervals.json5", INTERVAL_JSON_TYPE);

        List<Interval> list = new ArrayList<>();
        for (com.google.gson.JsonObject obj : rawList) {
            try {
                String id = obj.get("ID").getAsString();
                int interval = obj.get("interval").getAsInt();
                String unit = obj.get("unit").getAsString();
                boolean runInstantly = obj.has("runInstantly") && obj.get("runInstantly").getAsBoolean();
                boolean random = obj.has("random") && obj.get("random").getAsBoolean();

                List<List<String>> commandGroups = new ArrayList<>();

                if (obj.has("commands")) {
                    // Could be flat OR nested
                    var arr = obj.getAsJsonArray("commands");
                    if (arr.size() > 0 && arr.get(0).isJsonArray()) {
                        // ✅ Nested array: [["say A","say B"],["say X","say Y"]]
                        for (var el : arr) {
                            List<String> group = new ArrayList<>();
                            for (var cmd : el.getAsJsonArray()) {
                                group.add(cmd.getAsString());
                            }
                            commandGroups.add(group);
                        }
                    } else {
                        // ✅ Flat array: ["say A","say B"]
                        List<String> group = new ArrayList<>();
                        for (var el : arr) {
                            group.add(el.getAsString());
                        }
                        commandGroups.add(group);
                    }
                } else if (obj.has("command")) {
                    // ✅ Legacy single command
                    commandGroups.add(List.of(obj.get("command").getAsString()));
                }

                Interval ic = new Interval(id, commandGroups, interval, unit, runInstantly, random);
                list.add(ic);

            } catch (Exception e) {
                LOGGER.error("Skipping invalid interval entry: {}", e.getMessage());
            }
        }

        if (checkForDuplicateIDs(list)) {
            saveIntervalCommands();
        }

        return list;
    }

    public static List<ClockBased> loadClockBasedCommands() {
        clockPath = CONFIG_PATH.resolve("clock_based.json5");
        List<com.google.gson.JsonObject> rawList = loadConfig("clock_based.json5", INTERVAL_JSON_TYPE); // reuse JsonObject list

        List<ClockBased> list = new ArrayList<>();
        for (com.google.gson.JsonObject obj : rawList) {
            try {
                String id = obj.get("ID").getAsString();
                boolean active = obj.has("active") && obj.get("active").getAsBoolean();

                // Times
                List<int[]> times = new ArrayList<>();
                if (obj.has("times")) {
                    for (var t : obj.getAsJsonArray("times")) {
                        if (t.isJsonArray() && t.getAsJsonArray().size() == 2) {
                            int hour = t.getAsJsonArray().get(0).getAsInt();
                            int minute = t.getAsJsonArray().get(1).getAsInt();
                            times.add(new int[]{hour, minute});
                        }
                    }
                }

                // Commands
                List<String> commands = new ArrayList<>();
                List<List<String>> commandGroups = new ArrayList<>();
                boolean random = obj.has("random") && obj.get("random").getAsBoolean();

                if (obj.has("commands")) {
                    for (var el : obj.getAsJsonArray("commands")) {
                        if (el.isJsonArray()) {
                            // Group of commands
                            List<String> group = new ArrayList<>();
                            for (var sub : el.getAsJsonArray()) {
                                group.add(sub.getAsString());
                            }
                            commandGroups.add(group);
                        } else {
                            // Single command
                            commands.add(el.getAsString());
                        }
                    }
                } else if (obj.has("command")) {
                    commands.add(obj.get("command").getAsString()); // legacy single command
                }

                ClockBased cc = new ClockBased(id, commands, commandGroups, random);
                cc.setActive(active);
                for (int[] t : times) {
                    cc.addTime(t[0], t[1]);
                }

                list.add(cc);

            } catch (Exception e) {
                LOGGER.error("Skipping invalid clock-based entry: {}", e.getMessage());
            }
        }

        if (checkForDuplicateIDs(list)) {
            saveClockBasedCommands();
        }

        return list;
    }


    public static List<AtBoot> loadOnceAtBootCommands() {
    onceAtBootPath = CONFIG_PATH.resolve("once_at_boot.json5");
    List<AtBoot> list = loadConfig("once_at_boot.json5", ONCE_TYPE);

    if (checkForDuplicateIDs(list)) {
      saveOnceAtBootCommands();
    }

    return list;
  }

  public static void reloadConfigs() {
    intervalCommands = loadIntervalCommands();
    clockBasedCommands = loadClockBasedCommands();
    onceAtBootCommands = loadOnceAtBootCommands();
  }

  private static <T> List<T> loadConfig(String fileName, Type type) {
    try {
      Path path = CONFIG_PATH.resolve(fileName);

      if (!Files.exists(path)) {
        Files.createDirectories(CONFIG_PATH);
        writeDefaultConfigWithComments(fileName);
      }

      String json = Files.readString(path, StandardCharsets.UTF_8);
      List<T> loaded = gson.fromJson(json, type);
      if (loaded == null)
        return new ArrayList<>();

      // Validate and filter entries
      List<T> validEntries = new ArrayList<>();
      for (T entry : loaded) {
        try {
          if (entry instanceof Interval ic) {
            if (!Interval.isValidInterval(ic.getInterval())) {
              LOGGER.error("Skipping invalid interval for ID '{}': {}", ic.getID(), ic.getInterval());
              continue;
            }
          }
          validEntries.add(entry);
        } catch (Exception e) {
          LOGGER.error("Skipping invalid entry in {}: {}", fileName, e.getMessage());
        }
      }

      return validEntries;
    } catch (Exception e) {
      LOGGER.error("Failed to load {}: {}", fileName, e.getMessage());
      return new ArrayList<>();
    }
  }

  private static <T> void saveConfig(Path path, List<T> list, Type type) {
    try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
      gson.toJson(list, type, writer); // <-- USE THIS instead of list.getClass()
    } catch (IOException e) {
      LOGGER.error("Failed to save config to {}: {}", path, e.getMessage());
    }
  }

  public static <T> boolean checkForDuplicateIDs(List<T> list) {
    Map<String, Integer> idMap = new HashMap<>();
    boolean duplicatesFound = false;

    for (T item : list) {
      if (!(item instanceof Scheduler cmd)) {
        continue;
      }

      String originalID = cmd.getID();
      int duplicateCount = idMap.getOrDefault(originalID, 0);

      if (duplicateCount > 0) {
        // Generate new unique ID
        String newID;
        do {
          newID = originalID + "." + duplicateCount;
          duplicateCount++;
        } while (idMap.containsKey(newID));

        // Update the command's ID
        cmd.setID(newID);
        duplicatesFound = true;
        LOGGER.warn("Renamed duplicate ID '{}' to '{}'", originalID, newID);

        // Track both old and new IDs
        idMap.put(originalID, duplicateCount);
        idMap.put(newID, 0);
      } else {
        idMap.put(originalID, 1);
      }
    }

    return duplicatesFound;
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
                "runInstantly": false
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
                "runInstantly": false
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
    for (Interval ic : intervalCommands) {
      if (ic.getID().equals(id))
        return ic;
    }
    for (ClockBased cc : clockBasedCommands) {
      if (cc.getID().equals(id))
        return cc;
    }
    for (AtBoot oc : onceAtBootCommands) {
      if (oc.getID().equals(id))
        return oc;
    }
    return null;
  }

  public static boolean updateSchedulerId(String oldId, String newId) {
    Object cmd = getCommandById(oldId);
    if (cmd == null || !Scheduler.isValidID(newId))
      return false;

    boolean success = false;

    if (cmd instanceof Interval ic) {
      success = ic.setID(newId);
      if (success)
        saveIntervalCommands();
    } else if (cmd instanceof ClockBased cc) {
      success = cc.setID(newId);
      if (success)
        saveClockBasedCommands();
    } else if (cmd instanceof AtBoot oc) {
      success = oc.setID(newId);
      if (success)
        saveOnceAtBootCommands();
    }

    return success;
  }

    public static void saveIntervalCommands() {
        try (BufferedWriter writer = Files.newBufferedWriter(intervalPath, StandardCharsets.UTF_8)) {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (Interval ic : intervalCommands) {
                com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
                obj.addProperty("ID", ic.getID());
                obj.addProperty("active", ic.isActive());
                obj.addProperty("interval", ic.getInterval());
                obj.addProperty("unit", ic.getUnit().toString().toLowerCase());
                obj.addProperty("runInstantly", ic.shouldRunInstantly());

                if (ic.getCommandGroup() != null && !ic.getCommandGroup().isEmpty()) {
                    com.google.gson.JsonArray cmdArr = new com.google.gson.JsonArray();
                    for (String c : ic.getCommandGroup()) {
                        cmdArr.add(c);
                    }
                    obj.add("commands", cmdArr);
                    obj.addProperty("random", ic.isRandom());
                } else {
                    obj.addProperty("command", ic.getCommand()); // legacy single command
                }

                arr.add(obj);
            }
            gson.toJson(arr, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save interval commands: {}", e.getMessage());
        }
    }


    public static void saveClockBasedCommands() {
        try (BufferedWriter writer = Files.newBufferedWriter(clockPath, StandardCharsets.UTF_8)) {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (ClockBased cc : clockBasedCommands) {
                com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
                obj.addProperty("ID", cc.getID());
                obj.addProperty("active", cc.isActive());

                // Save times
                com.google.gson.JsonArray timeArr = new com.google.gson.JsonArray();
                for (int[] t : cc.getTimes()) {
                    com.google.gson.JsonArray pair = new com.google.gson.JsonArray();
                    pair.add(t[0]);
                    pair.add(t[1]);
                    timeArr.add(pair);
                }
                obj.add("times", timeArr);

                // Save commands
                if (cc.getCommandGroups() != null && !cc.getCommandGroups().isEmpty()) {
                    com.google.gson.JsonArray cmdArr = new com.google.gson.JsonArray();
                    for (List<String> group : cc.getCommandGroups()) {
                        com.google.gson.JsonArray subArr = new com.google.gson.JsonArray();
                        for (String cmd : group) {
                            subArr.add(cmd);
                        }
                        cmdArr.add(subArr);
                    }
                    obj.add("commands", cmdArr);
                } else if (cc.getCommands() != null && !cc.getCommands().isEmpty()) {
                    com.google.gson.JsonArray cmdArr = new com.google.gson.JsonArray();
                    for (String cmd : cc.getCommands()) {
                        cmdArr.add(cmd);
                    }
                    obj.add("commands", cmdArr);
                } else {
                    obj.addProperty("command", cc.getCommand()); // legacy
                }

                obj.addProperty("random", cc.isRandom());
                arr.add(obj);
            }
            gson.toJson(arr, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save clock-based commands: {}", e.getMessage());
        }
    }

  public static void saveOnceAtBootCommands() {
    saveConfig(onceAtBootPath, onceAtBootCommands, ONCE_TYPE);
  }

  public static List<ClockBased> getClockBasedCommands() {
    return Collections.unmodifiableList(clockBasedCommands);
  }

  public static List<Interval> getIntervalCommands() {
    return Collections.unmodifiableList(intervalCommands);
  }

  public static List<AtBoot> getOnceAtBootCommands() {
    return Collections.unmodifiableList(onceAtBootCommands);
  }

  public static void addClockBasedCommand(ClockBased command) {
    clockBasedCommands.add(command);
  }

  public static void addIntervalCommand(Interval command) {
    intervalCommands.add(command);
  }

  public static void addOnceAtBootCommand(AtBoot command) {
    onceAtBootCommands.add(command);
  }

  public static List<String> getAllSchedulerIDs() {
    List<String> ids = new ArrayList<>();
    for (Scheduler cmd : getIntervalCommands()) {
      ids.add(cmd.getID());
    }
    for (Scheduler cmd : getClockBasedCommands()) {
      ids.add(cmd.getID());
    }
    for (Scheduler cmd : getOnceAtBootCommands()) {
      ids.add(cmd.getID());
    }
    return ids;
  }

  public static List<String> getClockBasedSchedulerIDs() {
    List<String> ids = new ArrayList<>();
    for (Scheduler cmd : getClockBasedCommands()) {
      ids.add(cmd.getID());
    }
    return ids;
  }

}