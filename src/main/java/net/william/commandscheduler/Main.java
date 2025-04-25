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
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Main implements ModInitializer {

  private static final String MOD_ID = "commandscheduler";

  // Boot commands runs 15 seconds after boot
  private static final int bootDelaySeconds = 15;

  // Removal confirmation needed within 30 seconds
  private static final int removalTimeSeconds = 30;

  // 2 is for OPs
  private static final int permissionLevel = 2;

  // How many schedulers should be listed when running list command
  private static final int listingsPerPage = 10;

  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
  private static final Map<UUID, PendingRemoval> pendingRemovals = new HashMap<>();
  private static int bootDelayTicks = 0;
  private static boolean ranBootCommands = false;

  @Override
  public void onInitialize() {

    // Load config files
    ConfigHandler.loadAllCommands();

    LOGGER.info("CommandScheduler initialized.");
    ServerLifecycleEvents.SERVER_STARTING.register(server -> registerUserCommands(server));

    ServerTickEvents.START_SERVER_TICK.register(server -> {

      // At boot commands runs here!
      bootDelayTicks++;
      if (!ranBootCommands && bootDelayTicks >= (bootDelaySeconds * TimeUnit.TICKS_PER_SECOND)) {
        ranBootCommands = true;

        for (AtBoot oc : ConfigHandler.getOnceAtBootCommands()) {
          if (!oc.isExpired() && oc.isActive()) {
            runScheduledCommand(server, oc.getCommand());
            oc.setExpired();
          }
        }
      }

      // Interval commands runs here!
      List<Interval> commands = new ArrayList<>(ConfigHandler.getIntervalCommands());
      for (Interval ic : commands) {
        if (!ic.isActive())
          continue;
        ic.tick();
        int ticks = TimeUnit.getTickCountForUnits(ic.getUnit(), ic.getInterval());

        if (!ic.hasRan() && ic.shouldRunInstantly()) {
          ic.fastForwardUntilNextRun();
          ic.setRunInstantly(false);
          ConfigHandler.saveIntervalCommands();
        }

        if (ic.getTickCounter() >= ticks && ic.isActive()) {
          runScheduledCommand(server, ic.getCommand());
          ic.run();
        }
      }

      // Clock based commands runs here!
      LocalTime now = LocalTime.now();
      int hour = now.getHour();
      int minute = now.getMinute();
      for (ClockBased cc : ConfigHandler.getClockBasedCommands()) {
        if (!cc.isActive())
          continue;
        for (int[] t : cc.getTimes()) {
          if (t[0] == hour && t[1] == minute) {
            if (cc.run(hour, minute)) {
              runScheduledCommand(server, cc.getCommand());
            }
            break;
          }
        }
      }
    });
  }

  private void runScheduledCommand(MinecraftServer server, String command) {
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

  private void registerUserCommands(MinecraftServer server) {
    CommandDispatcher<ServerCommandSource> dispatcher = server.getCommandManager().getDispatcher();

    dispatcher.register(literal("commandscheduler")
        .requires(source -> source.hasPermissionLevel(permissionLevel))
        .executes(ctx -> Messages.sendHelpMenu(ctx, 1))

        // Command for help menus
        .then(literal("help")
            .then(literal("1")
                .executes(ctx -> Messages.sendHelpMenu(ctx, 1)))
            .then(literal("2")
                .executes(ctx -> Messages.sendHelpMenu(ctx, 2)))
            .then(literal("3")
                .executes(ctx -> Messages.sendHelpMenu(ctx, 3)))
            .then(literal("4")
                .executes(ctx -> Messages.sendHelpMenu(ctx, 4)))

            .executes(ctx -> Messages.sendHelpMenu(ctx, 1)) // Send page 1 if no page supplied
        )

        // Command to show small about page for the mod, links to github repo
        .then(literal("about")
            .executes(ctx -> {
              ctx.getSource().sendFeedback(() -> Text.literal("\n[CommandScheduler v1.0]")
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

              ctx.getSource().sendFeedback(() -> Text.literal(" - The github repository:")
                  .styled(s -> s.withColor(Formatting.GRAY)), false);

              ctx.getSource().sendFeedback(() -> Text.literal(" https://github.com/wPoizon/CommandScheduler-mod")
                  .styled(s -> s
                      .withColor(Formatting.BLUE)
                      .withUnderline(true)
                      .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,
                          "https://github.com/wPoizon/CommandScheduler-mod"))),
                  false);

              return 1;
            }))

        // Command for force reloading config files. Needed if they are manually changed
        .then(literal("reload")
            .executes(ctx -> {
              ConfigHandler.reloadConfigs();
              Messages.sendReloadSuccess(ctx);
              return 1;
            }))

        // Command to list all schedulers
        .then(literal("list")

            // Command to list all active schedulers
            .then(literal("active")
                .executes(ctx -> {
                  ServerCommandSource source = ctx.getSource();

                  Messages.sendListHeader(source, "Active Interval Commands");
                  Messages.sendList(source, ConfigHandler.getIntervalCommands(), true);

                  Messages.sendListHeader(source, "Active Clock-Based Commands");
                  Messages.sendList(source, ConfigHandler.getClockBasedCommands(), true);

                  Messages.sendListHeader(source, "Active Run Once Commands");
                  Messages.sendList(source, ConfigHandler.getOnceAtBootCommands(), true);

                  return 1;
                }))

            // Command to list all inactive schedulers
            .then(literal("inactive")
                .executes(ctx -> {
                  ServerCommandSource source = ctx.getSource();

                  Messages.sendListHeader(source, "Inactive Interval Commands");
                  Messages.sendList(source, ConfigHandler.getIntervalCommands(), false);

                  Messages.sendListHeader(source, "Inactive Clock-Based Commands");
                  Messages.sendList(source, ConfigHandler.getClockBasedCommands(), false);

                  Messages.sendListHeader(source, "Inactive Run Once Commands");
                  Messages.sendList(source, ConfigHandler.getOnceAtBootCommands(), false);

                  return 1;
                }))

            // Command to list all interval schedulers
            .then(literal(Types.INTERVAL.name)
                .executes(ctx -> {
                  Messages.sendListOfType(ctx.getSource(), ConfigHandler.getIntervalCommands(), 1,
                      "Interval Schedulers",
                      listingsPerPage);
                  return 1;
                })
                .then(argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> {
                      int page = IntegerArgumentType.getInteger(ctx, "page");
                      Messages.sendListOfType(ctx.getSource(), ConfigHandler.getIntervalCommands(), page,
                          "Interval Schedulers", listingsPerPage);
                      return 1;
                    })))

            // Command to list all clockbased schedulers
            .then(literal(Types.CLOCKBASED.name)
                .executes(ctx -> {
                  Messages.sendListOfType(ctx.getSource(), ConfigHandler.getClockBasedCommands(), 1,
                      "Clock-Based Schedulers", listingsPerPage);
                  return 1;
                })
                .then(argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> {
                      int page = IntegerArgumentType.getInteger(ctx, "page");
                      Messages.sendListOfType(ctx.getSource(), ConfigHandler.getClockBasedCommands(), page,
                          "Clock-Based Schedulers", listingsPerPage);
                      return 1;
                    })))

            // Command to list all atboot schedulers
            .then(literal(Types.ATBOOT.name)
                .executes(ctx -> {
                  Messages.sendListOfType(ctx.getSource(), ConfigHandler.getOnceAtBootCommands(), 1,
                      "Run Once At Boot Schedulers", listingsPerPage);
                  return 1;
                })
                .then(argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> {
                      int page = IntegerArgumentType.getInteger(ctx, "page");
                      Messages.sendListOfType(ctx.getSource(), ConfigHandler.getOnceAtBootCommands(), page,
                          "Run Once At Boot Schedulers", listingsPerPage);
                      return 1;
                    }))))

        // Command for activating a scheduler
        .then(literal("activate")
            .then(argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                  for (String id : ConfigHandler.getAllSchedulerIDs()) {
                    Object cmd = ConfigHandler.getCommandById(id);
                    if (cmd instanceof Scheduler info && !info.isActive()) {
                      builder.suggest(id);
                    }
                  }
                  return builder.buildFuture();
                })
                .executes(ctx -> {
                  String id = StringArgumentType.getString(ctx, "id");
                  Boolean result = setCommandActiveState(id, true);
                  if (result == null) {
                    Messages.sendAlreadyActiveMessage(ctx, id);
                  } else if (result) {
                    Messages.sendActivationStatus(ctx, id, true);
                  } else {
                    Messages.sendIdNotFound(ctx, id);
                  }
                  return 1;
                })))

        // Command for deactivating a scheduler
        .then(literal("deactivate")
            .then(argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                  for (String id : ConfigHandler.getAllSchedulerIDs()) {
                    Object cmd = ConfigHandler.getCommandById(id);
                    if (cmd instanceof Scheduler info && info.isActive()) {
                      builder.suggest(id);
                    }
                  }
                  return builder.buildFuture();
                })
                .executes(ctx -> {
                  String id = StringArgumentType.getString(ctx, "id");
                  Boolean result = setCommandActiveState(id, false);
                  if (result == null) {
                    Messages.sendAlreadyInactiveMessage(ctx, id);
                  } else if (result) {
                    Messages.sendActivationStatus(ctx, id, false);
                  } else {
                    Messages.sendIdNotFound(ctx, id);
                  }
                  return 1;
                })))

        // Command to get details about a schedulers
        .then(literal("details")
            .then(argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                  for (String id : ConfigHandler.getAllSchedulerIDs()) {
                    builder.suggest(id);
                  }
                  return builder.buildFuture();
                })
                .executes(ctx -> {
                  String id = StringArgumentType.getString(ctx, "id");
                  Object cmd = ConfigHandler.getCommandById(id);

                  if (cmd == null) {
                    Messages.sendIdNotFound(ctx, id);
                    return 0;
                  }

                  Messages.sendSchedulerDetails(ctx, cmd, id);

                  return 1;
                })))

        // Rename a scheduler
        .then(literal("rename")
            .then(argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                  for (String id : ConfigHandler.getAllSchedulerIDs()) {
                    builder.suggest(id);
                  }
                  return builder.buildFuture();
                })
                .then(argument("new", StringArgumentType.word())
                    .executes(ctx -> {
                      String oldId = StringArgumentType.getString(ctx, "id");
                      String newId = StringArgumentType.getString(ctx, "new");

                      if (ConfigHandler.getCommandById(newId) != null) {
                        Messages.sendIDAlreadyExists(ctx);
                        return 0;
                      }

                      Object cmd = ConfigHandler.getCommandById(oldId);
                      if (cmd == null) {
                        Messages.sendIdNotFound(ctx, oldId);
                        return 0;
                      }

                      // Rename
                      boolean success = ConfigHandler.updateSchedulerId(oldId, newId);
                      if (success) {
                        Messages.sendRenamedMessage(ctx, oldId, newId);
                        return 1;
                      } else {
                        Messages.sendInvalidID(ctx);
                        return 0;
                      }
                    }))))

        // Set a description for a scheduler
        .then(literal("description")
            .then(argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                  for (String id : ConfigHandler.getAllSchedulerIDs()) {
                    builder.suggest(id);
                  }
                  return builder.buildFuture();
                })
                .then(argument("description", StringArgumentType.greedyString())
                    .executes(ctx -> {
                      String id = StringArgumentType.getString(ctx, "id");
                      String desc = StringArgumentType.getString(ctx, "description");

                      Object cmd = ConfigHandler.getCommandById(id);

                      if (cmd == null) {
                        Messages.sendIdNotFound(ctx, id);
                        return 0;
                      }

                      if (cmd instanceof Interval ic) {
                        ic.setDescription(desc);
                      } else if (cmd instanceof ClockBased cc) {
                        cc.setDescription(desc);
                      } else if (cmd instanceof AtBoot oc) {
                        oc.setDescription(desc);
                      } else {
                        ctx.getSource().sendError(
                            Text.literal("✖ Schedule type not recognized.")
                                .styled(s -> s.withColor(Formatting.RED)));
                        return 0;
                      }

                      if (cmd instanceof Interval) {
                        ConfigHandler.saveIntervalCommands();
                      } else if (cmd instanceof ClockBased) {
                        ConfigHandler.saveClockBasedCommands();
                      } else if (cmd instanceof AtBoot) {
                        ConfigHandler.saveOnceAtBootCommands();
                      }
                      Messages.sendUpdatedDescription(ctx, id);
                      return 1;
                    }))))

        // Command for removing a scheduler
        .then(literal("remove")
            .then(argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                  for (String id : ConfigHandler.getAllSchedulerIDs()) {
                    builder.suggest(id);
                  }
                  return builder.buildFuture();
                })
                .executes(ctx -> {
                  ServerCommandSource source = ctx.getSource();
                  String id = StringArgumentType.getString(ctx, "id");

                  // If run from console (no player), just delete immediately
                  if (source.getEntity() == null) {
                    boolean success = ConfigHandler.removeCommandById(id);
                    if (success) {
                      Messages.sendRemovedMessage(source, id);
                    } else {
                      Messages.sendIdNotFound(ctx, id);
                    }
                    return 1;
                  }

                  // Player context: use confirmation system
                  ServerPlayerEntity player = source.getPlayer();
                  UUID playerUUID = player.getUuid();
                  PendingRemoval pending = pendingRemovals.get(playerUUID);
                  long now = System.currentTimeMillis();

                  // If confirmation already exists and is still fresh
                  if (pending != null && pending.id.equals(id)
                      && now - pending.timestamp < (removalTimeSeconds * 1000)) {

                    boolean success = ConfigHandler.removeCommandById(id);
                    if (success) {
                      Messages.sendRemovedMessage(source, id);
                    } else {
                      Messages.sendIdNotFound(ctx, id);
                    }
                    pendingRemovals.remove(playerUUID);
                  } else {
                    // Check if the ID actually exists before starting confirmation
                    if (ConfigHandler.getCommandById(id) == null) {
                      Messages.sendIdNotFound(ctx, id);
                      return 0;
                    }

                    // Start confirmation
                    pendingRemovals.put(playerUUID, new PendingRemoval(id));
                    Messages.sendRemoveConfirmation(source, id);
                  }
                  return 1;
                })))

        // Command for creating new schedulers
        .then(literal("new")

            // Command to add atboot schedulers.
            .then(literal(Types.ATBOOT.name)
                .then(argument("id", StringArgumentType.word())
                    .then(argument("command", StringArgumentType.greedyString())
                        .executes(ctx -> {
                          String id = StringArgumentType.getString(ctx, "id");
                          String command = StringArgumentType.getString(ctx, "command");

                          if (!Scheduler.isValidID(id)) {
                            Messages.sendInvalidID(ctx);
                            return 0;
                          }

                          if (ConfigHandler.getCommandById(id) != null) {
                            Messages.sendIDAlreadyExists(ctx);
                            return 0;
                          }

                          if (!Scheduler.isValidCommand(command)) {
                            Messages.sendInvalidCommand(ctx);
                          }

                          AtBoot cmd = new AtBoot(id, command);
                          ConfigHandler.addOnceAtBootCommand(cmd);
                          ConfigHandler.saveOnceAtBootCommands();

                          Messages.sendCreatedMessage(ctx, "at-boot", id);
                          return 1;
                        }))))

            // Command for creating a new interval scheduler
            .then(literal(Types.INTERVAL.name)
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
                                  if (!Scheduler.isValidID(id)) {
                                    Messages.sendInvalidID(ctx);
                                    return 0;
                                  }
                                  if (!TimeUnit.isValid(unit)) {
                                    ctx.getSource().sendError(
                                        Text.literal("✖ Invalid unit. Valid units: " + Arrays.stream(TimeUnit.values())
                                            .map(Enum::name).collect(Collectors.joining(", ")))
                                            .styled(s -> s.withColor(Formatting.RED)));
                                    return 0;
                                  }
                                  if (!Interval.isValidInterval(interval)) {
                                    ctx.getSource().sendError(
                                        Text.literal("✖ Interval must be greater than 0.")
                                            .styled(s -> s.withColor(Formatting.RED)));
                                    return 0;
                                  }

                                  if (!Scheduler.isValidCommand(command)) {
                                    Messages.sendInvalidCommand(ctx);
                                  }

                                  // Check for existing ID
                                  if (ConfigHandler.getCommandById(id) != null) {
                                    Messages.sendIDAlreadyExists(ctx);
                                    return 0;
                                  }

                                  // Add the command
                                  try {
                                    Interval newCmd = new Interval(id, command, interval, unit, true);
                                    ConfigHandler.addIntervalCommand(newCmd);
                                    ConfigHandler.saveIntervalCommands();
                                  } catch (IllegalArgumentException e) {
                                    ctx.getSource().sendError(
                                        Text.literal("✖ Error: " + e.getMessage())
                                            .styled(s -> s.withColor(Formatting.RED)));
                                    return 0;
                                  }

                                  Messages.sendCreatedMessage(ctx, "interval", id);

                                  return 1;
                                }))))))

            // Command for creating a new clock-based scheduler
            .then(literal(Types.CLOCKBASED.name)
                .then(argument("id", StringArgumentType.word())
                    .then(argument("command", StringArgumentType.greedyString())
                        .executes(ctx -> {
                          String id = StringArgumentType.getString(ctx, "id");
                          String command = StringArgumentType.getString(ctx, "command");

                          if (ConfigHandler.getCommandById(id) != null) {
                            Messages.sendIDAlreadyExists(ctx);
                            return 0;
                          }

                          if (!Scheduler.isValidID(id)) {
                            Messages.sendInvalidID(ctx);
                            return 0;
                          }

                          if (!Scheduler.isValidCommand(command)) {
                            Messages.sendInvalidCommand(ctx);
                          }

                          ClockBased newCmd = new ClockBased(id, command);
                          ConfigHandler.addClockBasedCommand(newCmd);
                          ConfigHandler.saveClockBasedCommands();

                          Messages.sendCreatedMessage(ctx, "clock-based", id);
                          return 1;
                        })))))

        // Command to add a new time point for a clock-based scheduler
        .then(literal("addtime")
            .then(argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                  for (String id : ConfigHandler.getClockBasedSchedulerIDs()) {
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

                      Object cmd = ConfigHandler.getCommandById(id);
                      if (!(cmd instanceof ClockBased cc)) {
                        Messages.sendClockBasedIdNotFound(ctx, id);
                        return 0;
                      }

                      // Validate time format
                      if (!ClockBased.isValidTimeString(timeArg)) {
                        Messages.sendInvalidTimeFormat(ctx);
                        return 0;
                      }

                      // Split time using period (.)
                      String[] parts = timeArg.split("\\.");
                      if (parts.length != 2) {
                        Messages.sendInvalidTimeFormat(ctx);
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

                      ConfigHandler.saveClockBasedCommands();
                      Messages.sendAddedTimeMessage(ctx, timeArg, id);

                      return 1;
                    }))))

        // Command to remove a time point for a clock-based scheduler
        .then(literal("removetime")
            .then(argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                  for (String id : ConfigHandler.getClockBasedSchedulerIDs()) {
                    builder.suggest(id);
                  }
                  return builder.buildFuture();
                })
                .then(argument("time", StringArgumentType.word())
                    .suggests((context, builder) -> {
                      if (context.getNodes().size() > 1) {
                        String id = StringArgumentType.getString(context, "id");
                        Object cmd = ConfigHandler.getCommandById(id);

                        if (cmd instanceof ClockBased cc) {
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

                      Object cmd = ConfigHandler.getCommandById(id);
                      if (!(cmd instanceof ClockBased cc)) {
                        Messages.sendClockBasedIdNotFound(ctx, id);
                        return 0;
                      }

                      // Validate time format using the updated method
                      if (!ClockBased.isValidTimeString(timeStr)) {
                        Messages.sendInvalidTimeFormat(ctx);
                        return 0;
                      }

                      // Split time using period (.)
                      String[] parts = timeStr.split("\\.");
                      if (parts.length != 2) {
                        Messages.sendInvalidTimeFormat(ctx);
                        return 0;
                      }

                      int hour = -1;
                      int minute = -1;
                      try {
                        hour = Integer.parseInt(parts[0]);
                        minute = Integer.parseInt(parts[1]);

                        // Check for valid time range (00-23 for hour, 00-59 for minute)
                        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                          Messages.sendInvalidTimeFormat(ctx);
                          return 0;
                        }
                      } catch (NumberFormatException e) {
                        Messages.sendInvalidTimeFormat(ctx);
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

                      ConfigHandler.saveClockBasedCommands();
                      Messages.sendRemovedTimeMessage(ctx, timeStr, id);

                      return 1;
                    }))))

    );
  }

  private static Boolean setCommandActiveState(String id, boolean active) {
    for (Interval cmd : ConfigHandler.getIntervalCommands()) {
      if (cmd.getID().equals(id)) {
        if (cmd.isActive() == active)
          return null; // Already in desired state
        cmd.setActive(active);
        ConfigHandler.saveIntervalCommands();
        return true;
      }
    }

    for (ClockBased cmd : ConfigHandler.getClockBasedCommands()) {
      if (cmd.getID().equals(id)) {
        if (cmd.isActive() == active)
          return null;
        cmd.setActive(active);
        ConfigHandler.saveClockBasedCommands();
        return true;
      }
    }

    for (AtBoot cmd : ConfigHandler.getOnceAtBootCommands()) {
      if (cmd.getID().equals(id)) {
        if (cmd.isActive() == active)
          return null;
        cmd.setActive(active);
        ConfigHandler.saveOnceAtBootCommands();
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
