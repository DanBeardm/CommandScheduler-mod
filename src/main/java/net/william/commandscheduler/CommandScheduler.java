package net.william.commandscheduler;

import net.fabricmc.api.ModInitializer;

import java.lang.reflect.Field;
import java.time.LocalTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class CommandScheduler implements ModInitializer {
	private static final String MOD_ID = "commandscheduler";

	private static final int TICKS_PER_SECOND = 20;
	private static final int TICKS_PER_MINUTE = CommandScheduler.TICKS_PER_SECOND * 60; // 1 200
	private static final int TICKS_PER_HOUR = CommandScheduler.TICKS_PER_MINUTE * 60; // 72 000

	private final int permissionLevel = 2; // 2 is for OPs

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		CommandSchedulerConfig.intervalCommands = CommandSchedulerConfig.loadIntervalCommands();
		CommandSchedulerConfig.clockBasedCommands = CommandSchedulerConfig.loadClockBasedCommands();
		CommandSchedulerConfig.onceCommands = CommandSchedulerConfig.loadOnceCommands();

		LOGGER.info("CommandScheduler initialized.");
		ServerLifecycleEvents.SERVER_STARTING.register(server -> registerCommands(server));

		// Runs commands meant for once at boot
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			for (OnceAtBootCommands oc : CommandSchedulerConfig.onceCommands) {
				if (!oc.isExpired() && oc.isActive()) {
					runCommand(server, oc.getCommand());
					oc.setExpired();
				}
			}
		});

		ServerTickEvents.START_SERVER_TICK.register(server -> {
			// Tick-based interval commands
			for (IntervalCommands ic : CommandSchedulerConfig.intervalCommands) {
				ic.tickCounter++;
				int ticks = switch (ic.getUnit().toLowerCase()) {
					case "seconds" -> ic.getInterval() * TICKS_PER_SECOND;
					case "minutes" -> ic.getInterval() * TICKS_PER_MINUTE;
					case "hours" -> ic.getInterval() * TICKS_PER_HOUR;
					default -> ic.getInterval(); // default to ticks
				};

				if (ic.tickCounter >= ticks && ic.isActive()) {
					runCommand(server, ic.getCommand());
					ic.tickCounter = 0;
				}
			}

			LocalTime now = LocalTime.now();
			int hour = now.getHour();
			int minute = now.getMinute();

			// Time-of-day based commands (real system clock)
			for (ClockBasedCommands cc : CommandSchedulerConfig.clockBasedCommands) {
				for (int[] t : cc.getTimes()) {
					if (t[0] == hour && t[1] == minute && cc.isActive()) {
						runCommand(server, cc.getCommand());
						break; // prevent multiple triggers if duplicate times exist
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
				.executes(ctx -> {
					ServerCommandSource source = ctx.getSource();
					source.sendFeedback(() -> Text.literal("§6[CommandScheduler] Available commands:"), false);
					source.sendFeedback(() -> Text.literal(" - /commandscheduler list"), false);
					source.sendFeedback(() -> Text.literal(" - /commandscheduler list active"), false);
					source.sendFeedback(() -> Text.literal(" - /commandscheduler list inactive"), false);
					source.sendFeedback(() -> Text.literal(" - /commandscheduler show [name]"), false);
					source.sendFeedback(() -> Text.literal(" - /commandscheduler activate [name]"), false);
					source.sendFeedback(() -> Text.literal(" - /commandscheduler deactivate [name]"), false);
					source.sendFeedback(() -> Text.literal(" - /commandscheduler rename [name] [new name]"), false);
					source.sendFeedback(() -> Text.literal(" - /commandscheduler forcereload"), false);
					source.sendFeedback(() -> Text.literal(
							" - /commandscheduler add interval [name] [interval] [unit] [run_at_start] \"<command>\""),
							false);
					source.sendFeedback(() -> Text.literal(" - /commandscheduler add clock [name] \"<command>\""),
							false);
					source.sendFeedback(() -> Text.literal(" - /commandscheduler addtime [name] [HH:mm]"), false);
					source.sendFeedback(() -> Text.literal(" - /commandscheduler add once [name] \"<command>\""),
							false);
					return 1;
				})

				// Command for force reloading the config files
				.then(CommandManager.literal("forcereload")
						.executes(ctx -> {
							CommandSchedulerConfig.reloadConfigs();
							ctx.getSource().sendFeedback(
									() -> Text.literal("[CommandScheduler] Reloaded configs from disk."), false);
							return 1;
						}))

				// Command for listing all scheduled commands
				.then(CommandManager.literal("list")
						.executes(ctx -> {
							ServerCommandSource source = ctx.getSource();

							sendListHeader(source, "Interval Commands");
							printCommandList(source, CommandSchedulerConfig.intervalCommands);

							sendListHeader(source, "Clock-Based Commands");
							printCommandList(source, CommandSchedulerConfig.clockBasedCommands);

							sendListHeader(source, "Run Once Commands");
							printCommandList(source, CommandSchedulerConfig.onceCommands);

							source.sendFeedback(() -> Text.literal(""), false); // This sends a blank line

							return 1;
						}))

				// Command for activating a scheduled command
				.then(CommandManager.literal("activate")
						.then(CommandManager.argument("id", StringArgumentType.word())
								.executes(ctx -> {
									String id = StringArgumentType.getString(ctx, "id");
									boolean success = setCommandActiveState(id, true);
									if (success) {
										ctx.getSource().sendFeedback(
												() -> Text.literal("[CommandScheduler] Activated '" + id + "'."),
												false);
									} else {
										ctx.getSource().sendError(Text
												.literal("[CommandScheduler] No command found with ID '" + id + "'."));
									}
									return 1;
								})))

				// Command for deactivating a scheduled command
				.then(CommandManager.literal("deactivate")
						.then(CommandManager.argument("id", StringArgumentType.word())
								.executes(ctx -> {
									String id = StringArgumentType.getString(ctx, "id");
									boolean success = setCommandActiveState(id, false);
									if (success) {
										ctx.getSource().sendFeedback(
												() -> Text.literal("[CommandScheduler] Deactivated '" + id + "'."),
												false);
									} else {
										ctx.getSource().sendError(Text
												.literal("[CommandScheduler] No command found with ID '" + id + "'."));
									}
									return 1;
								})))

				// Command to get info about a command
				.then(literal("show")
						.then(argument("id", StringArgumentType.word())
								.executes(ctx -> {
									try {
										String id = StringArgumentType.getString(ctx, "id");
										Object cmd = CommandSchedulerConfig.getCommandById(id);

										if (cmd == null) {
											ctx.getSource().sendError(Text.literal(
													"[CommandScheduler] No command found with ID '" + id + "'."));
											return 0;
										}

										MutableText output = Text
												.literal("[CommandScheduler] Info for ID '" + id + "':\n");

										if (cmd instanceof IntervalCommands ic) {
											output.append(Text.literal("- Type: Interval\n"));
											output.append(Text.literal("- Active: " + ic.isActive() + "\n"));
											output.append(Text.literal(
													"- Interval: " + ic.getInterval() + " " + ic.getUnit() + "\n"));
											output.append(
													Text.literal("- Run at start: " + ic.shouldRunAtStart() + "\n"));
											output.append(Text.literal("- Command: " + ic.getCommand()));
											if (ic.getDescription() != null && !ic.getDescription().isEmpty())
												output.append(Text.literal("\n- Description: " + ic.getDescription()));
										} else if (cmd instanceof ClockBasedCommands cc) {
											output.append(Text.literal("- Type: Clock-Based\n"));
											output.append(Text.literal("- Active: " + cc.isActive() + "\n"));
											output.append(Text.literal("- Times:\n"));
											for (int[] time : cc.getTimes()) {
												output.append(Text.literal(
														"  - " + String.format("%02d:%02d", time[0], time[1]) + "\n"));
											}
											output.append(Text.literal("- Command: " + cc.getCommand()));
											if (!cc.getDescription().isEmpty())
												output.append(Text.literal("\n- Description: " + cc.getDescription()));
										} else if (cmd instanceof OnceAtBootCommands oc) {
											output.append(Text.literal("- Type: Run Once at boot\n"));
											output.append(Text.literal("- Active: " + oc.isActive() + "\n"));
											output.append(Text.literal("- Command: " + oc.getCommand()));
											if (!oc.getDescription().isEmpty())
												output.append(Text.literal("\n- Description: " + oc.getDescription()));
										}

										ctx.getSource().sendFeedback(() -> output, false);
										return 1;

									} catch (Exception e) {
										e.printStackTrace(); // print to console
										ctx.getSource().sendError(
												Text.literal("[CommandScheduler] Unexpected error: " + e.getMessage()));
										return 0;
									}
								})))

				// Command to list all active commands
				.then(literal("list")
						.then(literal("active")
								.executes(ctx -> {
									ServerCommandSource source = ctx.getSource();

									sendListHeader(source, "Interval Commands");
									printCommandList(source, CommandSchedulerConfig.intervalCommands, true);

									sendListHeader(source, "Clock-Based Commands");
									printCommandList(source, CommandSchedulerConfig.clockBasedCommands, true);

									sendListHeader(source, "Run Once Commands");
									printCommandList(source, CommandSchedulerConfig.onceCommands, true);

									return 1;
								})))

				// Command to list all inactive commands
				.then(literal("list")
						.then(literal("inactive")
								.executes(ctx -> {
									ServerCommandSource source = ctx.getSource();

									sendListHeader(source, "Interval Commands");
									printCommandList(source, CommandSchedulerConfig.intervalCommands, false);

									sendListHeader(source, "Clock-Based Commands");
									printCommandList(source, CommandSchedulerConfig.clockBasedCommands, false);

									sendListHeader(source, "Run Once Commands");
									printCommandList(source, CommandSchedulerConfig.onceCommands, false);

									return 1;
								})))

				// Rename a command
				.then(literal("rename")
						.then(argument("id", StringArgumentType.word())
								.then(argument("new", StringArgumentType.word())
										.executes(ctx -> {
											String oldId = StringArgumentType.getString(ctx, "id");
											String newId = StringArgumentType.getString(ctx, "new");

											if (CommandSchedulerConfig.getCommandById(newId) != null) {
												ctx.getSource().sendError(Text.literal(
														"[CommandScheduler] A command with that name already exists."));
												return 0;
											}

											Object cmd = CommandSchedulerConfig.getCommandById(oldId);
											if (cmd == null) {
												ctx.getSource().sendError(
														Text.literal("[CommandScheduler] No command found with ID '"
																+ oldId + "'."));
												return 0;
											}

											// Rename
											if (cmd instanceof IntervalCommands ic) {
												ic.setID(newId);
												CommandSchedulerConfig.saveIntervalCommands();
											} else if (cmd instanceof ClockBasedCommands cc) {
												cc.setID(newId);
												CommandSchedulerConfig.saveClockBasedCommands();
											} else if (cmd instanceof OnceAtBootCommands oc) {
												oc.setID(newId);
												CommandSchedulerConfig.saveOnceCommands();
											}

											ctx.getSource().sendFeedback(
													() -> Text.literal("[CommandScheduler] Renamed command '" + oldId
															+ "' to '" + newId + "'."),
													false);
											return 1;
										}))))

		);
	}

	private static void sendListHeader(ServerCommandSource source, String title) {
		source.sendFeedback(() -> Text.literal("\n§6[" + title + "]"), false);
	}

	private static <T> void printCommandList(ServerCommandSource source, List<T> list) {
		int count = 0;
		for (T entry : list) {
			if (count >= 10) {
				source.sendFeedback(() -> Text.literal("§7...and more (use pagination later)"), false);
				break;
			}

			try {
				Field idField = entry.getClass().getDeclaredField("ID");
				Field activeField = entry.getClass().getDeclaredField("active");
				idField.setAccessible(true);
				activeField.setAccessible(true);
				String id = (String) idField.get(entry);
				boolean active = (boolean) activeField.get(entry);
				source.sendFeedback(() -> Text.literal(" - " + id + " (" + (active ? "active" : "inactive") + ")"),
						false);
				count++;
			} catch (Exception e) {
				source.sendError(Text.literal("§cFailed to read command entry."));
			}
		}

		if (list.isEmpty()) {
			source.sendFeedback(() -> Text.literal(" §8(no commands found)"), false);
		}
	}

	private static <T> void printCommandList(ServerCommandSource source, List<T> list, boolean activeOnly) {
		int count = 0;
		for (T entry : list) {
			try {
				Field idField = entry.getClass().getDeclaredField("ID");
				Field activeField = entry.getClass().getDeclaredField("active");
				idField.setAccessible(true);
				activeField.setAccessible(true);
				String id = (String) idField.get(entry);
				boolean isActive = (boolean) activeField.get(entry);

				if (isActive != activeOnly)
					continue;

				source.sendFeedback(() -> Text.literal(" - " + id + " (" + (isActive ? "active" : "inactive") + ")"),
						false);
				count++;
			} catch (Exception e) {
				source.sendError(Text.literal("§cFailed to read command entry."));
			}
		}

		if (count == 0) {
			source.sendFeedback(() -> Text.literal(" §8(no commands found)"), false);
		}
	}

	private static boolean setCommandActiveState(String id, boolean active) {
		for (IntervalCommands cmd : CommandSchedulerConfig.intervalCommands) {
			if (cmd.getID().equals(id)) {
				cmd.setActive(active);
				CommandSchedulerConfig.saveIntervalCommands();
				return true;
			}
		}

		for (ClockBasedCommands cmd : CommandSchedulerConfig.clockBasedCommands) {
			if (cmd.getID().equals(id)) {
				cmd.setActive(active);
				CommandSchedulerConfig.saveClockBasedCommands();
				return true;
			}
		}

		for (OnceAtBootCommands cmd : CommandSchedulerConfig.onceCommands) {
			if (cmd.getID().equals(id)) {
				cmd.setActive(active);
				CommandSchedulerConfig.saveOnceCommands();
				return true;
			}
		}

		return false; // Not found
	}

	public static String getModId() {
		return MOD_ID;
	}

	public static int getTicksPerSecond() {
		return TICKS_PER_SECOND;
	}

	public static int getTicksPerMinute() {
		return TICKS_PER_MINUTE;
	}

	public static int getTicksPerHour() {
		return TICKS_PER_HOUR;
	}

}
