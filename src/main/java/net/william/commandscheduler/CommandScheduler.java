package net.william.commandscheduler;

import net.fabricmc.api.ModInitializer;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class CommandScheduler implements ModInitializer {
  private static final String MOD_ID = "commandscheduler";

  private static int bootDelayTicks = 0;
  private static boolean ranBootCommands = false;

  // Boot commands ran 15 seconds after boot
  private static final int REQUIRED_BOOT_DELAY_TICKS = TimeUnit.TICKS_PER_SECOND * 15;

  private static final int permissionLevel = 2; // 2 is for OPs

  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  private static final Map<UUID, PendingRemoval> pendingRemovals = new HashMap<>();

  @Override
  public void onInitialize() {
    CommandSchedulerConfig.loadAllCommands();

    LOGGER.info("CommandScheduler initialized.");
    ServerLifecycleEvents.SERVER_STARTING.register(server -> registerCommands(server));

    ServerTickEvents.START_SERVER_TICK.register(server -> {
      // Tick-based interval commands (safe iteration via defensive copy)
      List<IntervalCommand> commands = new ArrayList<>(CommandSchedulerConfig.getIntervalCommands());
      for (IntervalCommand ic : commands) { // ✅ Now safe against concurrent modification
        ic.tick();
        int ticks = switch (ic.getUnit()) {
          case TICKS -> ic.getInterval();
          case SECONDS -> ic.getInterval() * TimeUnit.TICKS_PER_SECOND;
          case MINUTES -> ic.getInterval() * TimeUnit.TICKS_PER_MINUTE;
          case HOURS -> ic.getInterval() * TimeUnit.TICKS_PER_HOUR;
          case DAYS -> ic.getInterval() * TimeUnit.TICKS_PER_DAY;
        };

        if (!ic.hasRan() && ic.shouldRunInstantly()) {
          ic.fastForwardUntilNextRun();
          ic.setRunInstantly(false);
          CommandSchedulerConfig.saveIntervalCommands();
        }

        if (ic.getTickCounter() >= ticks && ic.isActive()) {
          runCommand(server, ic.getCommand());
          ic.run();
        }
      }

      bootDelayTicks++;

      if (!ranBootCommands && bootDelayTicks >= REQUIRED_BOOT_DELAY_TICKS) {
        ranBootCommands = true;

        for (OnceAtBootCommand oc : CommandSchedulerConfig.getOnceAtBootCommands()) {
          if (!oc.isExpired() && oc.isActive()) {
            runCommand(server, oc.getCommand());
            oc.setExpired();
          }
        }

        CommandSchedulerConfig.saveOnceAtBootCommands();
      }

      LocalTime now = LocalTime.now();
      int hour = now.getHour();
      int minute = now.getMinute();

      for (ClockBasedCommand cc : CommandSchedulerConfig.getClockBasedCommands()) {
        if (!cc.isActive())
          continue;

        if (cc.getLastRunHour() == hour && cc.getLastRunMinute() == minute)
          continue;

        for (int[] t : cc.getTimes()) {
          if (t[0] == hour && t[1] == minute) {
            runCommand(server, cc.getCommand());
            cc.run(hour, minute);
            break;
          }
        }
      }
    });
  }

  private void runCommand(MinecraftServer server, String command) {
    try {
      var dispatcher = server != null ? server.getCommandManager().getDispatcher() : null;
      if (dispatcher != null) {
        var parseResults = dispatcher.parse(command, server.getCommandSource());
        dispatcher.execute(parseResults);
      }
      LOGGER.info("Scheduled command ran: {}", command);
    } catch (CommandSyntaxException e) {
      LOGGER.warn("Failed to run command '{}': {}", command, e.getMessage());
    }
  }

  private void registerCommands(MinecraftServer server) {
    CommandDispatcher<ServerCommandSource> dispatcher = server.getCommandManager().getDispatcher();

    dispatcher.register(CommandManager.literal("commandscheduler")
        .requires(source -> source.hasPermissionLevel(permissionLevel))
        .executes(ctx -> showHelp(ctx.getSource(), 1))

        .then(CommandManager.literal("help")
            .then(CommandManager.literal("1")
                .executes(ctx -> showHelp(ctx.getSource(), 1)))
            .then(CommandManager.literal("2")
                .executes(ctx -> showHelp(ctx.getSource(), 2)))
            .executes(ctx -> showHelp(ctx.getSource(), 1)) // fallback if no number
        )

        // Command for listing all schedulers
        .then(CommandManager.literal("list")
            .executes(ctx -> {
              ServerCommandSource source = ctx.getSource();

              sendListHeader(source, "Interval Schedulers");
              printCommandList(source, CommandSchedulerConfig.getIntervalCommands());

              sendListHeader(source, "Clock-Based Schedulers");
              printCommandList(source, CommandSchedulerConfig.getClockBasedCommands());

              sendListHeader(source, "At-Boot Schedulers");
              printCommandList(source, CommandSchedulerConfig.getOnceAtBootCommands());

              source.sendFeedback(() -> Text.literal(""), false); // This sends a blank line

              return 1;
            }))

        // Command for activating a scheduler
        .then(CommandManager.literal("activate")
            .then(CommandManager.argument("id", StringArgumentType.word())
                .executes(ctx -> {
                  String id = StringArgumentType.getString(ctx, "id");
                  boolean success = setCommandActiveState(id, true);
                  if (success) {
                    ctx.getSource().sendFeedback(
                        () -> Text.literal("[CommandScheduler] Activated: ").append(
                            Text.literal(id).styled(s -> s.withColor(Formatting.YELLOW))
                                .append(Text.literal("\n"))),
                        false);
                  } else {
                    ctx.getSource().sendError(Text
                        .literal("[CommandScheduler] No command found with ID '" + id + "'."));
                  }
                  return 1;
                })))

        // Command for deactivating a scheduler
        .then(CommandManager.literal("deactivate")
            .then(CommandManager.argument("id", StringArgumentType.word())
                .executes(ctx -> {
                  String id = StringArgumentType.getString(ctx, "id");
                  boolean success = setCommandActiveState(id, false);
                  if (success) {
                    ctx.getSource().sendFeedback(
                        () -> Text.literal("[CommandScheduler] Deactivated: ").append(
                            Text.literal(id).styled(s -> s.withColor(Formatting.YELLOW))),
                        false);
                  } else {
                    ctx.getSource().sendError(Text
                        .literal("[CommandScheduler] No command found with ID '" + id + "'."));
                  }
                  return 1;
                })))

        // Command to get info about a schedulers
        .then(literal("details")
            .then(argument("id", StringArgumentType.word())
                .executes(ctx -> {
                  String id = StringArgumentType.getString(ctx, "id");
                  Object cmd = CommandSchedulerConfig.getCommandById(id);

                  if (cmd == null) {
                    ctx.getSource().sendError(Text
                        .literal("[CommandScheduler] No scheduler found with ID: ")
                        .append(Text.literal(id).styled(s -> s.withColor(Formatting.RED))));
                    return 0;
                  }

                  MutableText output = Text.literal("")
                      .append(Text.literal("[CommandScheduler] Info for ID: ")
                          .styled(s -> s.withColor(Formatting.GOLD).withBold(true)))
                      .append(Text.literal(id + "\n")
                          .styled(s -> s.withColor(Formatting.YELLOW)));

                  if (cmd instanceof IntervalCommand ic) {
                    output.append(Text.literal(" - Type: ")
                        .styled(s -> s.withBold(true).withColor(Formatting.GRAY)))
                        .append(Text.literal("Interval\n"));

                    output.append(Text.literal(" - Active: ")
                        .styled(s -> s.withBold(true).withColor(Formatting.GRAY)))
                        .append(Text.literal(ic.isActive() + "\n"));

                    output.append(Text.literal(" - Interval: ")
                        .styled(s -> s.withBold(true).withColor(Formatting.GRAY)))
                        .append(Text.literal(ic.getInterval() + " "
                            + ic.getUnit().name().toLowerCase() + "\n"));

                    output.append(Text.literal(" - Run at start: ")
                        .styled(s -> s.withBold(true).withColor(Formatting.GRAY)))
                        .append(Text.literal(ic.shouldRunInstantly() + "\n"));

                    output.append(Text.literal(" - Command: ")
                        .styled(s -> s.withBold(true).withColor(Formatting.GRAY)))
                        .append(Text.literal(ic.getCommand()).styled(s -> s.withItalic(true)))
                        .append(Text.literal("\n"));

                    if (ic.getDescription() != null && !ic.getDescription().isEmpty()) {
                      output.append(Text.literal(" - Description: ")
                          .styled(s -> s.withBold(true).withColor(Formatting.GRAY)))
                          .append(Text.literal(ic.getDescription())
                              .styled(s -> s.withItalic(true)))
                          .append(Text.literal("\n"));
                    }

                  } else if (cmd instanceof ClockBasedCommand cc) {
                    output.append(Text.literal(" - Type: ")
                        .styled(s -> s.withBold(true).withColor(Formatting.GRAY)))
                        .append(Text.literal("Clock-Based\n"));

                    output.append(Text.literal(" - Active: ")
                        .styled(s -> s.withBold(true).withColor(Formatting.GRAY)))
                        .append(Text.literal(cc.isActive() + "\n"));

                    output.append(Text.literal(" - Times: ")
                        .styled(s -> s.withBold(true).withColor(Formatting.GRAY)));
                    List<int[]> times = cc.getTimes();
                    for (int i = 0; i < times.size(); i++) {
                      int[] time = times.get(i);
                      String formatted = String.format("%02d:%02d", time[0], time[1]);
                      output.append(Text.literal(formatted));
                      if (i < times.size() - 1)
                        output.append(Text.literal(", "));
                    }
                    output.append(Text.literal("\n"));

                    output.append(Text.literal(" - Command: ")
                        .styled(s -> s.withBold(true).withColor(Formatting.GRAY)))
                        .append(Text.literal(cc.getCommand()).styled(s -> s.withItalic(true)))
                        .append(Text.literal("\n"));

                    if (!cc.getDescription().isEmpty()) {
                      output.append(Text.literal(" - Description: ")
                          .styled(s -> s.withBold(true).withColor(Formatting.GRAY)))
                          .append(Text.literal(cc.getDescription())
                              .styled(s -> s.withItalic(true)))
                          .append(Text.literal("\n"));
                    }

                  } else if (cmd instanceof OnceAtBootCommand oc) {
                    output.append(Text.literal(" - Type: ")
                        .styled(s -> s.withBold(true).withColor(Formatting.GRAY)))
                        .append(Text.literal("Run Once at Boot\n"));

                    output.append(Text.literal(" - Active: ")
                        .styled(s -> s.withBold(true).withColor(Formatting.GRAY)))
                        .append(Text.literal(oc.isActive() + "\n"));

                    output.append(Text.literal(" - Command: ")
                        .styled(s -> s.withBold(true).withColor(Formatting.GRAY)))
                        .append(Text.literal(oc.getCommand()).styled(s -> s.withItalic(true)))
                        .append(Text.literal("\n"));

                    if (!oc.getDescription().isEmpty()) {
                      output.append(Text.literal(" - Description: ")
                          .styled(s -> s.withBold(true).withColor(Formatting.GRAY)))
                          .append(Text.literal(oc.getDescription())
                              .styled(s -> s.withItalic(true)))
                          .append(Text.literal("\n"));
                    }
                  }

                  ctx.getSource().sendFeedback(() -> output, false);
                  return 1;
                })))

        // Command to list all active schedulers
        .then(literal("list")
            .then(literal("active")
                .executes(ctx -> {
                  ServerCommandSource source = ctx.getSource();

                  sendListHeader(source, "Active Interval Commands");
                  printCommandList(source, CommandSchedulerConfig.getIntervalCommands(), true);

                  sendListHeader(source, "Active Clock-Based Commands");
                  printCommandList(source, CommandSchedulerConfig.getClockBasedCommands(), true);

                  sendListHeader(source, "Active Run Once Commands");
                  printCommandList(source, CommandSchedulerConfig.getOnceAtBootCommands(), true);

                  return 1;
                })))

        // Command to list all inactive schedulers
        .then(literal("list")
            .then(literal("inactive")
                .executes(ctx -> {
                  ServerCommandSource source = ctx.getSource();

                  sendListHeader(source, "Inactive Interval Commands");
                  printCommandList(source, CommandSchedulerConfig.getIntervalCommands(), false);

                  sendListHeader(source, "Inactive Clock-Based Commands");
                  printCommandList(source, CommandSchedulerConfig.getClockBasedCommands(), false);

                  sendListHeader(source, "Inactive Run Once Commands");
                  printCommandList(source, CommandSchedulerConfig.getOnceAtBootCommands(), false);

                  return 1;
                })))

        // Rename a scheduler
        .then(literal("rename")
            .then(argument("id", StringArgumentType.word())
                .then(argument("new", StringArgumentType.word())
                    .executes(ctx -> {
                      String oldId = StringArgumentType.getString(ctx, "id");
                      String newId = StringArgumentType.getString(ctx, "new");

                      if (CommandSchedulerConfig.getCommandById(newId) != null) {
                        ctx.getSource().sendError(Text.literal(
                            "[CommandScheduler] A scheduler with that id already exists."));
                        return 0;
                      }

                      Object cmd = CommandSchedulerConfig.getCommandById(oldId);
                      if (cmd == null) {
                        ctx.getSource().sendError(
                            Text.literal("[CommandScheduler] No scheduler found with ID '"
                                + oldId + "'."));
                        return 0;
                      }

                      // Rename
                      if (cmd instanceof IntervalCommand ic) {
                        ic.setID(newId);
                        CommandSchedulerConfig.saveIntervalCommands();
                      } else if (cmd instanceof ClockBasedCommand cc) {
                        cc.setID(newId);
                        CommandSchedulerConfig.saveClockBasedCommands();
                      } else if (cmd instanceof OnceAtBootCommand oc) {
                        oc.setID(newId);
                        CommandSchedulerConfig.saveOnceAtBootCommands();
                      }

                      ctx.getSource().sendFeedback(
                          () -> Text.literal(
                              "[CommandScheduler] Updated ID of scheduler: '" + oldId
                                  + "' -> '" + newId + "'."),
                          false);
                      return 1;
                    }))))

        // Set a description for a scheduler
        .then(literal("description")
            .then(argument("id", StringArgumentType.word())
                .then(argument("description", StringArgumentType.greedyString())
                    .executes(ctx -> {
                      String id = StringArgumentType.getString(ctx, "id");
                      String desc = StringArgumentType.getString(ctx, "description");

                      Object cmd = CommandSchedulerConfig.getCommandById(id);

                      if (cmd == null) {
                        ctx.getSource().sendError(Text.literal(
                            "[CommandScheduler] No scheduler found with ID '" + id + "'."));
                        return 0;
                      }

                      if (cmd instanceof IntervalCommand ic) {
                        ic.setDescription(desc);
                      } else if (cmd instanceof ClockBasedCommand cc) {
                        cc.setDescription(desc);
                      } else if (cmd instanceof OnceAtBootCommand oc) {
                        oc.setDescription(desc);
                      } else {
                        ctx.getSource().sendError(Text
                            .literal("[CommandScheduler] Schedule type not recognized."));
                        return 0;
                      }

                      if (cmd instanceof IntervalCommand) {
                        CommandSchedulerConfig.saveIntervalCommands();
                      } else if (cmd instanceof ClockBasedCommand) {
                        CommandSchedulerConfig.saveClockBasedCommands();
                      } else if (cmd instanceof OnceAtBootCommand) {
                        CommandSchedulerConfig.saveOnceAtBootCommands();
                      }
                      ctx.getSource()
                          .sendFeedback(() -> Text
                              .literal("[CommandScheduler] Description updated for: ")
                              .append(Text.literal(id)
                                  .styled(s -> s.withColor(Formatting.YELLOW))),
                              false);
                      return 1;
                    }))))

        // Command for removing a scheduler
        .then(CommandManager.literal("remove")
            .then(argument("id", StringArgumentType.word())
                .executes(ctx -> {
                  ServerCommandSource source = ctx.getSource();
                  ServerPlayerEntity player = source.getPlayer();
                  String id = StringArgumentType.getString(ctx, "id");
                  UUID playerUUID = player.getUuid();

                  // Check if this player has a pending removal for the same ID
                  PendingRemoval pending = pendingRemovals.get(playerUUID);
                  long now = System.currentTimeMillis();

                  if (pending != null && pending.id.equals(id) && now - pending.timestamp < 30_000) {
                    // Proceed with removal
                    boolean success = CommandSchedulerConfig.removeCommandById(id);
                    if (success) {
                      source.sendFeedback(
                          () -> Text.literal(
                              "[CommandScheduler] Successfully removed '" + id + "'."),
                          false);
                    } else {
                      source.sendError(Text.literal(
                          "[CommandScheduler] Could not find scheduler with ID '" + id
                              + "'."));
                    }
                    pendingRemovals.remove(playerUUID); // Clear confirmation
                  } else {
                    // Start confirmation
                    pendingRemovals.put(playerUUID, new PendingRemoval(id));
                    source.sendFeedback(
                        () -> Text
                            .literal("[CommandScheduler] Are you sure you want to remove: ")
                            .append(Text.literal(id)
                                .styled(s -> s.withColor(Formatting.RED)))
                            .append(Text.literal(
                                "? Run the same command again within 30 seconds to confirm.")),
                        false);
                  }

                  return 1;
                })))

        // Command for creating a new interval scheduler
        .then(literal("interval")
            .then(argument("id", StringArgumentType.word())
                .then(argument("unit", StringArgumentType.word())
                    .then(argument("interval", IntegerArgumentType.integer(1)) // Minimum 1
                        .then(argument("command", StringArgumentType.greedyString())
                            .executes(ctx -> {
                              String id = StringArgumentType.getString(ctx, "id");
                              String unit = StringArgumentType.getString(ctx, "unit");
                              int interval = IntegerArgumentType.getInteger(ctx,
                                  "interval");
                              String command = StringArgumentType.getString(ctx,
                                  "command");

                              // Validate all inputs
                              if (!IntervalCommand.isValidID(id)) {
                                ctx.getSource().sendError(
                                    Text.literal("[CommandScheduler] Invalid ID."));
                                return 0;
                              }
                              if (!TimeUnit.isValid(unit)) {
                                ctx.getSource().sendError(Text.literal(
                                    "[CommandScheduler] Invalid unit. Valid units: "
                                        + Arrays.stream(TimeUnit.values())
                                            .map(Enum::name)
                                            .collect(Collectors
                                                .joining(", "))));
                                return 0;
                              }
                              if (!IntervalCommand.isValidInterval(interval)) {
                                ctx.getSource().sendError(Text.literal(
                                    "[CommandScheduler] Interval must be greater than 0."));
                                return 0;
                              }
                              if (!IntervalCommand.isValidCommand(command)) {
                                ctx.getSource().sendError(Text.literal(
                                    "[CommandScheduler] Command cannot be empty."));
                                return 0;
                              }

                              // Check for existing ID
                              if (CommandSchedulerConfig.getCommandById(id) != null) {
                                ctx.getSource().sendError(Text.literal(
                                    "[CommandScheduler] ID already exists."));
                                return 0;
                              }

                              // Add the command
                              try {
                                IntervalCommand newCmd = new IntervalCommand(id, command, interval, unit, true);
                                CommandSchedulerConfig.addIntervalCommand(newCmd);
                                CommandSchedulerConfig.saveIntervalCommands();
                              } catch (IllegalArgumentException e) {
                                ctx.getSource().sendError(Text.literal("[CommandScheduler] Error: " + e.getMessage()));
                                return 0;
                              }

                              ctx.getSource().sendFeedback(() -> Text.literal(
                                  "[CommandScheduler] Interval scheduler created with ID: ")
                                  .append(Text.literal(id).styled(
                                      s -> s.withColor(Formatting.YELLOW))),
                                  false);

                              return 1;
                            }))))))

        // Command for creating a new clock-based scheduler
        .then(literal("clockbased")
            .then(argument("id", StringArgumentType.word())
                .then(argument("command", StringArgumentType.greedyString())
                    .executes(ctx -> {
                      String id = StringArgumentType.getString(ctx, "id");
                      String command = StringArgumentType.getString(ctx, "command");

                      if (CommandSchedulerConfig.getCommandById(id) != null) {
                        ctx.getSource().sendError(Text.literal(
                            "[CommandScheduler] A scheduler with this ID already exists."));
                        return 0;
                      }

                      if (!IntervalCommand.isValidID(id)) {
                        ctx.getSource()
                            .sendError(Text.literal("[CommandScheduler] Invalid ID."));
                        return 0;
                      }

                      if (!IntervalCommand.isValidCommand(command)) {
                        ctx.getSource().sendError(
                            Text.literal("[CommandScheduler] Command cannot be empty."));
                        return 0;
                      }

                      ClockBasedCommand newCmd = new ClockBasedCommand(id, command);
                      CommandSchedulerConfig.addClockBasedCommand(newCmd);
                      CommandSchedulerConfig.saveClockBasedCommands();

                      ctx.getSource().sendFeedback(() -> Text.literal(
                          "[CommandScheduler] Clock-based scheduler created with ID: ")
                          .append(Text.literal(id)
                              .styled(s -> s.withColor(Formatting.YELLOW))),
                          false);
                      return 1;
                    }))))

    );
  }

  private static void sendListHeader(ServerCommandSource source, String title) {
    source.sendFeedback(() -> Text.literal("\n§6[" + title + "]"), false);
  }

  private static <T extends ScheduledCommandInfo> void printCommandList(ServerCommandSource source, List<T> list) {
    int count = 0;
    for (T entry : list) {
      if (count >= 10) {
        Text.literal("...and more (use pagination later)")
            .styled(s -> s.withColor(Formatting.DARK_GRAY).withItalic(true));
        break;
      }

      String id = entry.getID();
      boolean active = entry.isActive();
      source.sendFeedback(() -> Text.literal(" - ")
          .append(Text.literal(id).styled(s -> s.withColor(Formatting.YELLOW)))
          .append(Text.literal(" (" + (active ? "active" : "inactive") + ")")
              .styled(s -> s.withColor(Formatting.GRAY))),
          false);
      count++;
    }

    if (list.isEmpty()) {
      source.sendFeedback(() -> Text.literal(" §8(no schedulers found)"), false);
    }
  }

  private static <T> void printCommandList(ServerCommandSource source, List<T> list, boolean activeOnly) {
    int count = 0;
    for (T entry : list) {
      // Cast to the ScheduledCommandInfo interface
      ScheduledCommandInfo cmd = (ScheduledCommandInfo) entry;

      String id = cmd.getID();
      boolean isActive = cmd.isActive();

      if (isActive != activeOnly)
        continue;

      source.sendFeedback(() -> Text.literal(" - " + id + " (" + (isActive ? "active" : "inactive") + ")"),
          false);
      count++;
    }

    if (count == 0) {
      if (activeOnly) {
        source.sendFeedback(() -> Text.literal(" §8(no active schedulers found)"), false);
      } else {
        source.sendFeedback(() -> Text.literal(" §8(no inactive schedulers found)"), false);
      }
    }
  }

  private static boolean setCommandActiveState(String id, boolean active) {
    for (IntervalCommand cmd : CommandSchedulerConfig.getIntervalCommands()) {
      if (cmd.getID().equals(id)) {
        cmd.setActive(active);
        CommandSchedulerConfig.saveIntervalCommands();
        return true;
      }
    }

    for (ClockBasedCommand cmd : CommandSchedulerConfig.getClockBasedCommands()) {
      if (cmd.getID().equals(id)) {
        cmd.setActive(active);
        CommandSchedulerConfig.saveClockBasedCommands();
        return true;
      }
    }

    for (OnceAtBootCommand cmd : CommandSchedulerConfig.getOnceAtBootCommands()) {
      if (cmd.getID().equals(id)) {
        cmd.setActive(active);
        CommandSchedulerConfig.saveOnceAtBootCommands();
        return true;
      }
    }

    return false; // Not found
  }

  private static class PendingRemoval {
    public final String id;
    public final long timestamp;

    public PendingRemoval(String id) {
      this.id = id;
      this.timestamp = System.currentTimeMillis();
    }
  }

  public static String getModId() {
    return MOD_ID;
  }

  private static int showHelp(ServerCommandSource source, int page) {
    // First page
    if (page == 1) {
      source.sendFeedback(() -> Text.literal("")
          .append(Text.literal("[CommandScheduler Help Page 1/2]")
              .styled(s -> s.withColor(Formatting.GOLD).withBold(true)))
          .append(Text.literal("\nAvailable commands:").styled(s -> s.withColor(Formatting.YELLOW))),
          false);

      source.sendFeedback(() -> styledCommand("list"), false);
      source.sendFeedback(() -> styledCommand("list ").append(arg("active")), false);
      source.sendFeedback(() -> styledCommand("list ").append(arg("inactive")), false);
      source.sendFeedback(() -> styledCommand("details ").append(arg("[id]")), false);
      source.sendFeedback(() -> styledCommand("activate ").append(arg("[id]")), false);
      source.sendFeedback(() -> styledCommand("deactivate ").append(arg("[id]")), false);
      source.sendFeedback(() -> styledCommand("rename ")
          .append(arg("id")).append(" ").append(arg("[new id]")), false);
      source.sendFeedback(() -> styledCommand("remove ").append(arg("[id]")), false);
      source.sendFeedback(() -> styledCommand("forcereload"), false);

      source.sendFeedback(() -> styledCommand("interval ")
          .append(arg("[id]")).append(" ")
          .append(arg("[unit]")).append(" ")
          .append(arg("[interval]")).append(" ")
          .append(arg("<command>", Formatting.DARK_GRAY)), false);

      source.sendFeedback(() -> styledCommand("clockbased ")
          .append(arg("[id]")).append(" ")
          .append(arg("<command>", Formatting.DARK_GRAY)), false);

      source.sendFeedback(() -> styledCommand("addtime ")
          .append(arg("[id]")).append(" ").append(arg("[hh:mm]")), false);

      source.sendFeedback(() -> styledCommand("atboot ")
          .append(arg("[id]")).append(" ").append(arg("<command>", Formatting.DARK_GRAY)), false);

      source.sendFeedback(() -> Text.literal("For an explanation of the arguments, run ")
          .append(Text.literal("/commandscheduler help 2").styled(s -> s.withColor(Formatting.YELLOW))),
          false);

      // Second page
    } else if (page == 2)

    {
      source.sendFeedback(() -> Text.literal("\n§6[CommandScheduler Help Page 2/2]"), false);
      source.sendFeedback(() -> Text.literal("Explanations for the command arguments:"), false);

      source.sendFeedback(() -> Text.literal(" - ")
          .append(Text.literal("id").styled(style -> style.withItalic(true)))
          .append(
              " is the name you wish to have for the command scheduler. Can not include spaces or some special characters."),
          false);

      source.sendFeedback(() -> Text.literal(" - ")
          .append(Text.literal("unit").styled(style -> style.withItalic(true)))
          .append(" is the time unit used for the interval. Use 'ticks', 'seconds', 'minutes', 'hours', or 'days'."),
          false);

      source.sendFeedback(() -> Text.literal(" - ")
          .append(Text.literal("interval").styled(style -> style.withItalic(true)))
          .append(" is the time between each execution, using the time unit specified earlier. Must be an integer."),
          false);

      source.sendFeedback(() -> Text.literal(" - ")
          .append(Text.literal("command").styled(style -> style.withItalic(true)))
          .append(" is the command you want the scheduler to run."), false);

      // Fall back (wrong page)
    } else {
      source.sendFeedback(() -> Text.literal("§6[CommandScheduler Help Page ?/2]"), false);
      source.sendFeedback(() -> Text.literal("This page doesn't exist."), false);

    }

    return 1;
  }

  private static MutableText styledCommand(String commandBase) {
    return Text.literal(" - /commandscheduler ")
        .styled(s -> s.withColor(Formatting.GRAY))
        .append(Text.literal(commandBase).styled(s -> s.withColor(Formatting.WHITE)));
  }

  private static MutableText arg(String name) {
    return arg(name, Formatting.ITALIC);
  }

  private static MutableText arg(String name, Formatting format) {
    return Text.literal(name)
        .styled(s -> s.withItalic(true).withColor(format));
  }

}
