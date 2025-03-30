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

  // Boot commands runs 15 seconds after boot
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
      for (IntervalCommand ic : commands) {
        if (!ic.isActive())
          continue;
        ic.tick();
        int ticks = TimeUnit.getTickCountForUnits(ic.getUnit(), ic.getInterval());

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
      }

      LocalTime now = LocalTime.now();
      int hour = now.getHour();
      int minute = now.getMinute();

      for (ClockBasedCommand cc : CommandSchedulerConfig.getClockBasedCommands()) {
        if (!cc.isActive())
          continue;

        for (int[] t : cc.getTimes()) {
          if (t[0] == hour && t[1] == minute) {
            if (cc.run(hour, minute)) {
              runCommand(server, cc.getCommand());
              CommandSchedulerConfig.saveClockBasedCommands();
            }
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
        .executes(ctx -> CommandMessages.sendHelpMenu(ctx, 1))

        // Command for help menus
        .then(CommandManager.literal("help")
            .then(CommandManager.literal("1")
                .executes(ctx -> CommandMessages.sendHelpMenu(ctx, 1)))
            .then(CommandManager.literal("2")
                .executes(ctx -> CommandMessages.sendHelpMenu(ctx, 2)))
            .then(CommandManager.literal("3")
                .executes(ctx -> CommandMessages.sendHelpMenu(ctx, 3)))
            .then(CommandManager.literal("4")
                .executes(ctx -> CommandMessages.sendHelpMenu(ctx, 4)))
            .executes(ctx -> CommandMessages.sendHelpMenu(ctx, 1)) // fallback if no number
        )

        // Command to show small about page for the mod and myself
        .then(CommandManager.literal("about")
            .executes(ctx -> {
              ctx.getSource().sendFeedback(() -> Text.literal(" - CommandScheduler v1.0 ")
                  .styled(s -> s.withColor(Formatting.GOLD).withBold(true)), false);

              ctx.getSource().sendFeedback(() -> Text.literal(" - Fabric mod made by Poizon.")
                  .styled(s -> s.withColor(Formatting.GRAY)), false);

              ctx.getSource().sendFeedback(
                  () -> Text
                      .literal(" - Adds scheduled command execution by interval, time of day, or for server boot.")
                      .styled(s -> s.withColor(Formatting.GRAY)),
                  false);

              ctx.getSource().sendFeedback(() -> Text.literal(" - Type ")
                  .append(Text.literal("/commandscheduler").styled(s -> s.withColor(Formatting.YELLOW)))
                  .append(" for usage.")
                  .styled(s -> s.withColor(Formatting.GRAY)), false);

              // Optional GitHub link
              ctx.getSource().sendFeedback(() -> Text.literal(" - The github repository:\n")
                  .append(Text.literal("https://github.com/wPoizon/command-scheduler-1.20.2")
                      .styled(s -> s.withColor(Formatting.BLUE).withUnderline(true))),
                  false);

              return 1;
            }))

        // Command for force reloading config files. Needed if they are manually changed
        .then(literal("forcereload")
            .executes(ctx -> {
              CommandSchedulerConfig.reloadConfigs();
              CommandMessages.sendForceReloadSuccess(ctx);
              return 1;
            }))

        // Command to list all schedulers
        .then(CommandManager.literal("list")
            .executes(ctx -> {
              ServerCommandSource source = ctx.getSource();

              CommandMessages.sendListHeader(source, "Interval Schedulers");
              CommandMessages.sendSchedulerList(source, CommandSchedulerConfig.getIntervalCommands(), null);

              CommandMessages.sendListHeader(source, "Clock-Based Schedulers");
              CommandMessages.sendSchedulerList(source, CommandSchedulerConfig.getClockBasedCommands(), null);

              CommandMessages.sendListHeader(source, "At-Boot Schedulers");
              CommandMessages.sendSchedulerList(source, CommandSchedulerConfig.getOnceAtBootCommands(), null);

              source.sendFeedback(() -> Text.literal(""), false); // This sends a blank line

              return 1;
            }))

        // Command to list all active schedulers
        .then(literal("list")
            .then(literal("active")
                .executes(ctx -> {
                  ServerCommandSource source = ctx.getSource();

                  CommandMessages.sendListHeader(source, "Active Interval Commands");
                  CommandMessages.sendSchedulerList(source, CommandSchedulerConfig.getIntervalCommands(), true);

                  CommandMessages.sendListHeader(source, "Active Clock-Based Commands");
                  CommandMessages.sendSchedulerList(source, CommandSchedulerConfig.getClockBasedCommands(), true);

                  CommandMessages.sendListHeader(source, "Active Run Once Commands");
                  CommandMessages.sendSchedulerList(source, CommandSchedulerConfig.getOnceAtBootCommands(), true);

                  return 1;
                })))

        // Command to list all inactive schedulers
        .then(literal("list")
            .then(literal("inactive")
                .executes(ctx -> {
                  ServerCommandSource source = ctx.getSource();

                  CommandMessages.sendListHeader(source, "Inactive Interval Commands");
                  CommandMessages.sendSchedulerList(source, CommandSchedulerConfig.getIntervalCommands(), false);

                  CommandMessages.sendListHeader(source, "Inactive Clock-Based Commands");
                  CommandMessages.sendSchedulerList(source, CommandSchedulerConfig.getClockBasedCommands(), false);

                  CommandMessages.sendListHeader(source, "Inactive Run Once Commands");
                  CommandMessages.sendSchedulerList(source, CommandSchedulerConfig.getOnceAtBootCommands(), false);

                  return 1;
                })))

        // Command for activating a scheduler
        .then(CommandManager.literal("activate")
            .then(CommandManager.argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                  for (String id : CommandSchedulerConfig.getAllSchedulerIDs()) {
                    Object cmd = CommandSchedulerConfig.getCommandById(id);
                    if (cmd instanceof ScheduledCommandInfo info && !info.isActive()) {
                      builder.suggest(id);
                    }
                  }
                  return builder.buildFuture();
                })
                .executes(ctx -> {
                  String id = StringArgumentType.getString(ctx, "id");
                  Boolean result = setCommandActiveState(id, true);
                  if (result == null) {
                    CommandMessages.sendAlreadyActiveMessage(ctx, id);
                  } else if (result) {
                    CommandMessages.sendActivationStatus(ctx, id, true);
                  } else {
                    CommandMessages.sendIdNotFound(ctx, id);
                  }
                  return 1;
                })))

        // Command for deactivating a scheduler
        .then(CommandManager.literal("deactivate")
            .then(CommandManager.argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                  for (String id : CommandSchedulerConfig.getAllSchedulerIDs()) {
                    Object cmd = CommandSchedulerConfig.getCommandById(id);
                    if (cmd instanceof ScheduledCommandInfo info && info.isActive()) {
                      builder.suggest(id);
                    }
                  }
                  return builder.buildFuture();
                })
                .executes(ctx -> {
                  String id = StringArgumentType.getString(ctx, "id");
                  Boolean result = setCommandActiveState(id, false);
                  if (result == null) {
                    CommandMessages.sendAlreadyInactiveMessage(ctx, id);
                  } else if (result) {
                    CommandMessages.sendActivationStatus(ctx, id, false);
                  } else {
                    CommandMessages.sendIdNotFound(ctx, id);
                  }
                  return 1;
                })))

        // Command to get details about a schedulers
        .then(literal("details")
            .then(CommandManager.argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                  for (String id : CommandSchedulerConfig.getAllSchedulerIDs()) {
                    builder.suggest(id);
                  }
                  return builder.buildFuture();
                })
                .executes(ctx -> {
                  String id = StringArgumentType.getString(ctx, "id");
                  Object cmd = CommandSchedulerConfig.getCommandById(id);

                  if (cmd == null) {
                    CommandMessages.sendIdNotFound(ctx, id); // Error message for no scheduler with this ID
                    return 0;
                  }

                  CommandMessages.sendSchedulerDetails(ctx, cmd, id);

                  return 1;
                })))

        // Rename a scheduler
        .then(literal("rename")
            .then(argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                  for (String id : CommandSchedulerConfig.getAllSchedulerIDs()) {
                    builder.suggest(id);
                  }
                  return builder.buildFuture();
                })
                .then(argument("new", StringArgumentType.word())
                    .executes(ctx -> {
                      String oldId = StringArgumentType.getString(ctx, "id");
                      String newId = StringArgumentType.getString(ctx, "new");

                      if (CommandSchedulerConfig.getCommandById(newId) != null) {
                        CommandMessages.sendIDAlreadyExists(ctx);
                        return 0;
                      }

                      Object cmd = CommandSchedulerConfig.getCommandById(oldId);
                      if (cmd == null) {
                        CommandMessages.sendIdNotFound(ctx, oldId); // Error message for no scheduler with this ID
                        return 0;
                      }

                      // Rename
                      boolean success = CommandSchedulerConfig.updateSchedulerId(oldId, newId);
                      if (success) {
                        CommandMessages.sendRenamedMessage(ctx, oldId, newId);
                        return 1;
                      } else {
                        CommandMessages.sendInvalidID(ctx);
                        return 0;
                      }
                    }))))

        // Set a description for a scheduler
        .then(literal("description")
            .then(argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                  for (String id : CommandSchedulerConfig.getAllSchedulerIDs()) {
                    builder.suggest(id);
                  }
                  return builder.buildFuture();
                })
                .then(argument("description", StringArgumentType.greedyString())
                    .executes(ctx -> {
                      String id = StringArgumentType.getString(ctx, "id");
                      String desc = StringArgumentType.getString(ctx, "description");

                      Object cmd = CommandSchedulerConfig.getCommandById(id);

                      if (cmd == null) {
                        CommandMessages.sendIdNotFound(ctx, id); // Error message for no scheduler with this ID
                        return 0;
                      }

                      if (cmd instanceof IntervalCommand ic) {
                        ic.setDescription(desc);
                      } else if (cmd instanceof ClockBasedCommand cc) {
                        cc.setDescription(desc);
                      } else if (cmd instanceof OnceAtBootCommand oc) {
                        oc.setDescription(desc);
                      } else {
                        ctx.getSource().sendError(
                            Text.literal("✖ Schedule type not recognized.")
                                .styled(s -> s.withColor(Formatting.RED)));
                        return 0;
                      }

                      if (cmd instanceof IntervalCommand) {
                        CommandSchedulerConfig.saveIntervalCommands();
                      } else if (cmd instanceof ClockBasedCommand) {
                        CommandSchedulerConfig.saveClockBasedCommands();
                      } else if (cmd instanceof OnceAtBootCommand) {
                        CommandSchedulerConfig.saveOnceAtBootCommands();
                      }
                      CommandMessages.sendUpdatedDescription(ctx, id);
                      return 1;
                    }))))

        // Command for removing a scheduler
        .then(CommandManager.literal("remove")
            .then(argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                  for (String id : CommandSchedulerConfig.getAllSchedulerIDs()) {
                    builder.suggest(id);
                  }
                  return builder.buildFuture();
                })
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
                      CommandMessages.sendRemovedMessage(source, id);
                    } else {
                      CommandMessages.sendIdNotFound(ctx, id); // Error message for no scheduler with this ID
                    }
                    pendingRemovals.remove(playerUUID); // Clear confirmation
                  } else {
                    // Start confirmation
                    pendingRemovals.put(playerUUID, new PendingRemoval(id));
                    CommandMessages.sendRemoveConfirmation(source, id);
                  }

                  return 1;
                })))

        // Command for creating a new interval scheduler
        .then(literal("interval")
            .then(argument("id", StringArgumentType.word())
                .then(argument("unit", StringArgumentType.word()).suggests((ctx, builder) -> {
                  for (String unitName : TimeUnit.getAllNames()) {
                    builder.suggest(unitName);
                  }
                  return builder.buildFuture();
                })
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
                              if (!BaseScheduledCommand.isValidID(id)) {
                                CommandMessages.sendInvalidID(ctx);
                                return 0;
                              }
                              if (!TimeUnit.isValid(unit)) {
                                ctx.getSource().sendError(
                                    Text.literal("✖ Invalid unit. Valid units: " + Arrays.stream(TimeUnit.values())
                                        .map(Enum::name).collect(Collectors.joining(", ")))
                                        .styled(s -> s.withColor(Formatting.RED)));
                                return 0;
                              }
                              if (!IntervalCommand.isValidInterval(interval)) {
                                ctx.getSource().sendError(
                                    Text.literal("✖ Interval must be greater than 0.")
                                        .styled(s -> s.withColor(Formatting.RED)));
                                return 0;
                              }
                              if (!IntervalCommand.isValidCommand(command)) {
                                ctx.getSource().sendError(
                                    Text.literal("✖ Command cannot be empty.")
                                        .styled(s -> s.withColor(Formatting.RED)));
                                return 0;
                              }

                              // Check for existing ID
                              if (CommandSchedulerConfig.getCommandById(id) != null) {
                                CommandMessages.sendIDAlreadyExists(ctx);
                                return 0;
                              }

                              // Add the command
                              try {
                                IntervalCommand newCmd = new IntervalCommand(id, command, interval, unit, true);
                                CommandSchedulerConfig.addIntervalCommand(newCmd);
                                CommandSchedulerConfig.saveIntervalCommands();
                              } catch (IllegalArgumentException e) {
                                ctx.getSource().sendError(
                                    Text.literal("✖ Error: " + e.getMessage())
                                        .styled(s -> s.withColor(Formatting.RED)));
                                return 0;
                              }

                              CommandMessages.sendCreatedMessage(ctx, "interval", id);

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
                        CommandMessages.sendIDAlreadyExists(ctx);
                        return 0;
                      }

                      if (!BaseScheduledCommand.isValidID(id)) {
                        CommandMessages.sendInvalidID(ctx);
                        return 0;
                      }

                      if (!IntervalCommand.isValidCommand(command)) {
                        ctx.getSource().sendError(
                            Text.literal("✖ Command cannot be empty.")
                                .styled(s -> s.withColor(Formatting.RED)));
                        return 0;
                      }

                      ClockBasedCommand newCmd = new ClockBasedCommand(id, command);
                      CommandSchedulerConfig.addClockBasedCommand(newCmd);
                      CommandSchedulerConfig.saveClockBasedCommands();

                      CommandMessages.sendCreatedMessage(ctx, "clock-based", id);
                      return 1;
                    }))))

        // Command to add a new time point for a clock-based scheduler
        .then(literal("addtime")
            .then(argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                  for (String id : CommandSchedulerConfig.getClockBasedSchedulerIDs()) {
                    builder.suggest(id);
                  }
                  return builder.buildFuture();
                })
                .then(argument("time", StringArgumentType.word())
                    .suggests((context, builder) -> {
                      builder.suggest("00.00");
                      builder.suggest("12.34");
                      builder.suggest("22.45");
                      return builder.buildFuture();
                    })
                    .executes(ctx -> {
                      String id = StringArgumentType.getString(ctx, "id");
                      String timeArg = StringArgumentType.getString(ctx, "time");

                      Object cmd = CommandSchedulerConfig.getCommandById(id);
                      if (!(cmd instanceof ClockBasedCommand cc)) {
                        CommandMessages.sendClockBasedIdNotFound(ctx, id); // Error message for no scheduler with this
                                                                           // ID
                        return 0;
                      }

                      // Validate time format
                      if (!ClockBasedCommand.isValidTimeString(timeArg)) {
                        CommandMessages.sendInvalidTimeFormat(ctx);
                        return 0;
                      }

                      // Split time using period (.)
                      String[] parts = timeArg.split("\\."); // Changed from ":" to "\\." to split by period
                      if (parts.length != 2) {
                        CommandMessages.sendInvalidTimeFormat(ctx); // In case there's an issue splitting the time
                        return 0;
                      }

                      int hour = Integer.parseInt(parts[0]);
                      int minute = Integer.parseInt(parts[1]);

                      // Add time to the clock-based command
                      boolean added = cc.addTime(hour, minute);
                      if (!added) {
                        ctx.getSource().sendError(
                            Text.literal("✖ Time " + timeArg + " already exists or is invalid.")
                                .styled(s -> s.withColor(Formatting.RED)));
                        return 0;
                      }

                      CommandSchedulerConfig.saveClockBasedCommands();
                      CommandMessages.sendAddedTimeMessage(ctx, timeArg, id);

                      return 1;
                    }))))

        // Command to remove a time point for a clock-based scheduler
        .then(literal("removetime")
            .then(argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                  for (String id : CommandSchedulerConfig.getClockBasedSchedulerIDs()) {
                    builder.suggest(id);
                  }
                  return builder.buildFuture();
                })
                .then(argument("time", StringArgumentType.word())
                    .suggests((context, builder) -> {
                      if (context.getNodes().size() > 1) {
                        String id = StringArgumentType.getString(context, "id");
                        Object cmd = CommandSchedulerConfig.getCommandById(id);

                        if (cmd instanceof ClockBasedCommand cc) {
                          for (int[] time : cc.getTimes()) {
                            String formatted = String.format("%02d.%02d", time[0], time[1]);
                            builder.suggest(formatted);
                          }
                        }
                      }
                      return builder.buildFuture();
                    })
                    .executes(ctx -> {
                      String id = StringArgumentType.getString(ctx, "id");
                      String timeStr = StringArgumentType.getString(ctx, "time");

                      Object cmd = CommandSchedulerConfig.getCommandById(id);
                      if (!(cmd instanceof ClockBasedCommand cc)) {
                        CommandMessages.sendClockBasedIdNotFound(ctx, id); // Error message for no scheduler with this
                                                                           // ID
                        return 0;
                      }

                      // Validate time format using the updated method
                      if (!ClockBasedCommand.isValidTimeString(timeStr)) {
                        CommandMessages.sendInvalidTimeFormat(ctx);
                        return 0;
                      }

                      // Split time using period (.)
                      String[] parts = timeStr.split("\\."); // Changed from ":" to "\\." to split by period
                      if (parts.length != 2) {
                        CommandMessages.sendInvalidTimeFormat(ctx); // In case there's an issue splitting the time
                        return 0;
                      }

                      int hour = -1;
                      int minute = -1;
                      try {
                        hour = Integer.parseInt(parts[0]);
                        minute = Integer.parseInt(parts[1]);

                        // Check for valid time range (00-23 for hour, 00-59 for minute)
                        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                          CommandMessages.sendInvalidTimeFormat(ctx); // Send error if time is out of range
                          return 0;
                        }
                      } catch (NumberFormatException e) {
                        CommandMessages.sendInvalidTimeFormat(ctx); // Send error if parsing fails
                        return 0;
                      }

                      // Try to remove the time from the clock-based scheduler
                      boolean success = cc.removeTime(hour, minute);
                      if (!success) {
                        ctx.getSource().sendError(
                            Text.literal("✖ Time " + timeStr + " not found in this scheduler.")
                                .styled(s -> s.withColor(Formatting.RED)));
                        return 0;
                      }

                      CommandSchedulerConfig.saveClockBasedCommands();
                      CommandMessages.sendRemovedTimeMessage(ctx, timeStr, id);

                      return 1;
                    }))))

        // Command to add atboot schedulers.
        .then(CommandManager.literal("atboot")
            .then(CommandManager.argument("id", StringArgumentType.word())
                .then(CommandManager.argument("command", StringArgumentType.greedyString())
                    .executes(ctx -> {
                      String id = StringArgumentType.getString(ctx, "id");
                      String command = StringArgumentType.getString(ctx, "command");

                      if (!BaseScheduledCommand.isValidID(id)) {
                        CommandMessages.sendInvalidID(ctx);
                        return 0;
                      }

                      if (CommandSchedulerConfig.getCommandById(id) != null) {
                        CommandMessages.sendIDAlreadyExists(ctx);
                        return 0;
                      }

                      OnceAtBootCommand cmd = new OnceAtBootCommand(id, command);
                      CommandSchedulerConfig.addOnceAtBootCommand(cmd);
                      CommandSchedulerConfig.saveOnceAtBootCommands();

                      CommandMessages.sendCreatedMessage(ctx, "at-boot", id);
                      return 1;
                    }))))

    );
  }

  private static Boolean setCommandActiveState(String id, boolean active) {
    for (IntervalCommand cmd : CommandSchedulerConfig.getIntervalCommands()) {
      if (cmd.getID().equals(id)) {
        if (cmd.isActive() == active)
          return null; // Already in desired state
        cmd.setActive(active);
        CommandSchedulerConfig.saveIntervalCommands();
        return true;
      }
    }

    for (ClockBasedCommand cmd : CommandSchedulerConfig.getClockBasedCommands()) {
      if (cmd.getID().equals(id)) {
        if (cmd.isActive() == active)
          return null;
        cmd.setActive(active);
        CommandSchedulerConfig.saveClockBasedCommands();
        return true;
      }
    }

    for (OnceAtBootCommand cmd : CommandSchedulerConfig.getOnceAtBootCommands()) {
      if (cmd.getID().equals(id)) {
        if (cmd.isActive() == active)
          return null;
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

}
