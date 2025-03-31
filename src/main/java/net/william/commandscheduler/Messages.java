package net.william.commandscheduler;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class Messages {

	public static MutableText label(String name) {
		return Text.literal(" - " + name + ": ")
				.styled(s -> s.withBold(true).withColor(Formatting.GRAY));
	}

	public static MutableText styledCommand(String commandBase) {
		return Text.literal(" - /commandscheduler ")
				.styled(s -> s.withColor(Formatting.GRAY))
				.append(Text.literal(commandBase).styled(s -> s.withColor(Formatting.WHITE)));
	}

	public static MutableText arg(String name) {
		return arg(name, Formatting.ITALIC);
	}

	public static MutableText arg(String name, Formatting format) {
		return Text.literal(name)
				.styled(s -> s.withItalic(true).withColor(format));
	}

	public static void sendActivationStatus(CommandContext<ServerCommandSource> ctx, String id, boolean activated) {
		String senderName = ctx.getSource().getName();

		MutableText msg = Text.literal("[CommandScheduler] ")
				.append(Text.literal(senderName + " ").styled(s -> s.withColor(Formatting.AQUA)))
				.append(Text.literal("has ").styled(s -> s.withColor(Formatting.GRAY)))
				.append(Text.literal(activated ? "activated " : "deactivated ").styled(
						s -> s.withColor(activated ? Formatting.GREEN : Formatting.RED)))
				.append(Text.literal(id).styled(s -> s.withColor(Formatting.YELLOW)));

		// Send to server console
		ctx.getSource().getServer().sendMessage(msg);

		// Send to all OPs
		ctx.getSource().getServer().getPlayerManager().getPlayerList().forEach(player -> {
			if (ctx.getSource().getServer().getPlayerManager().isOperator(player.getGameProfile())) {
				player.sendMessage(msg.copy().styled(s -> s.withColor(Formatting.GOLD)), false);
			}
		});
	}

	public static void sendInvalidID(CommandContext<ServerCommandSource> ctx) {
		ctx.getSource().sendError(
				Text.literal("✖ Invalid ID or failed to update scheduler.")
						.styled(s -> s.withColor(Formatting.RED)));
	}

	public static void sendInvalidCommand(CommandContext<ServerCommandSource> ctx) {
		ctx.getSource().sendError(
				Text.literal("✖ Command not allowed!")
						.styled(s -> s.withColor(Formatting.RED)));
	}

	public static void sendIDAlreadyExists(CommandContext<ServerCommandSource> ctx) {
		ctx.getSource().sendError(
				Text.literal("✖ A scheduler with that ID already exists.")
						.styled(s -> s.withColor(Formatting.RED)));
	}

	public static void sendIdNotFound(CommandContext<ServerCommandSource> ctx, String id) {
		ctx.getSource().sendError(
				Text.literal("✖ No scheduler found with ID ")
						.append(Text.literal(id).styled(s -> s.withColor(Formatting.RED))));
	}

	public static void sendClockBasedIdNotFound(CommandContext<ServerCommandSource> ctx, String id) {
		ctx.getSource().sendError(
				Text.literal("✖ No clock-based scheduler found with ID ")
						.append(Text.literal(id).styled(s -> s.withColor(Formatting.RED))));
	}

	public static void sendReloadSuccess(CommandContext<ServerCommandSource> ctx) {
		String senderName = ctx.getSource().getName();

		MutableText msg = Text.literal("[CommandScheduler] ")
				.append(Text.literal(senderName + " ").styled(s -> s.withColor(Formatting.AQUA)))
				.append(Text.literal("reloaded all configs.").styled(s -> s.withColor(Formatting.GRAY)));

		// Send to server console
		ctx.getSource().getServer().sendMessage(msg);

		// Send to all OPs
		ctx.getSource().getServer().getPlayerManager().getPlayerList().forEach(player -> {
			if (ctx.getSource().getServer().getPlayerManager().isOperator(player.getGameProfile())) {
				player.sendMessage(msg.copy().styled(s -> s.withColor(Formatting.GOLD)), false);
			}
		});
	}

	public static void sendSchedulerDetails(CommandContext<ServerCommandSource> ctx, Object cmd,
			String id) {
		MutableText output = Text.literal("")
				.append(Text.literal("\nDetails for scheduler: ")
						.styled(s -> s.withColor(Formatting.GOLD).withBold(true)))
				.append(Text.literal(id).styled(s -> s.withColor(Formatting.YELLOW)))
				.append("\n");

		if (cmd instanceof Interval ic) {
			output.append(Text.literal(" - Type: ")
					.styled(s -> s.withBold(true).withColor(Formatting.GRAY)))
					.append(Text.literal("Interval\n"));

			output.append(label("Active")).append(Text.literal(ic.isActive() + "\n"));

			output.append(Text.literal(" - Interval: ")
					.styled(s -> s.withBold(true).withColor(Formatting.GRAY)))
					.append(Text.literal(ic.getInterval() + " " + ic.getUnit().name().toLowerCase()
							+ "\n"));

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

		} else if (cmd instanceof ClockBased cc) {
			output.append(Text.literal(" - Type: ")
					.styled(s -> s.withBold(true).withColor(Formatting.GRAY)))
					.append(Text.literal("Clock-Based\n"));

			output.append(label("Active")).append(Text.literal(cc.isActive() + "\n"));

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

		} else if (cmd instanceof AtBoot oc) {
			output.append(Text.literal(" - Type: ")
					.styled(s -> s.withBold(true).withColor(Formatting.GRAY)))
					.append(Text.literal("Run Once at Boot\n"));

			output.append(label("Active")).append(Text.literal(oc.isActive() + "\n"));

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
	}

	public static void sendAddedTimeMessage(CommandContext<ServerCommandSource> ctx, String timeArg, String id) {
		String senderName = ctx.getSource().getName();

		MutableText msg = Text.literal("[CommandScheduler] ")
				.append(Text.literal(senderName + " ").styled(s -> s.withColor(Formatting.AQUA)))
				.append(Text.literal("added time ").styled(s -> s.withColor(Formatting.GRAY)))
				.append(Text.literal(timeArg).styled(s -> s.withColor(Formatting.AQUA)))
				.append(Text.literal(" to ").styled(s -> s.withColor(Formatting.GRAY)))
				.append(Text.literal(id).styled(s -> s.withColor(Formatting.YELLOW)));

		// Send to server console
		ctx.getSource().getServer().sendMessage(msg);

		// Send to all OPs
		ctx.getSource().getServer().getPlayerManager().getPlayerList().forEach(player -> {
			if (ctx.getSource().getServer().getPlayerManager().isOperator(player.getGameProfile())) {
				player.sendMessage(msg.copy().styled(s -> s.withColor(Formatting.GOLD)), false);
			}
		});
	}

	public static void sendCreatedMessage(CommandContext<ServerCommandSource> ctx, String type, String id) {
		String senderName = ctx.getSource().getName();

		MutableText msg = Text.literal("[CommandScheduler] ")
				.append(Text.literal(senderName + " ").styled(s -> s.withColor(Formatting.AQUA)))
				.append(Text.literal("created " + type + " scheduler with ID: ")
						.styled(s -> s.withColor(Formatting.GRAY)))
				.append(Text.literal(id).styled(s -> s.withColor(Formatting.YELLOW)));

		// Send to server console
		ctx.getSource().getServer().sendMessage(msg);

		// Send to all OPs
		ctx.getSource().getServer().getPlayerManager().getPlayerList().forEach(player -> {
			if (ctx.getSource().getServer().getPlayerManager().isOperator(player.getGameProfile())) {
				player.sendMessage(msg.copy().styled(s -> s.withColor(Formatting.GOLD)), false);
			}
		});
	}

	public static void sendRenamedMessage(CommandContext<ServerCommandSource> ctx, String oldId, String newId) {
		String senderName = ctx.getSource().getName();

		MutableText msg = Text.literal("[CommandScheduler] ")
				.append(Text.literal(senderName + " ").styled(s -> s.withColor(Formatting.AQUA)))
				.append(Text.literal("renamed ").styled(s -> s.withColor(Formatting.GRAY)))
				.append(Text.literal(oldId).styled(s -> s.withColor(Formatting.YELLOW)))
				.append(Text.literal(" to ").styled(s -> s.withColor(Formatting.GRAY)))
				.append(Text.literal(newId).styled(s -> s.withColor(Formatting.YELLOW)));

		// Send to server console
		ctx.getSource().getServer().sendMessage(msg);

		// Send to all OPs (players with permission level >= 2)
		ctx.getSource().getServer().getPlayerManager().getPlayerList().forEach(player -> {
			if (ctx.getSource().getServer().getPlayerManager().isOperator(player.getGameProfile())) {
				player.sendMessage(msg.copy().styled(s -> s.withColor(Formatting.GOLD)), false);
			}
		});
	}

	public static void sendRemovedMessage(ServerCommandSource source, String id) {
		String senderName = source.getName();

		MutableText msg = Text.literal("[CommandScheduler] ")
				.append(Text.literal(senderName + " ").styled(s -> s.withColor(Formatting.AQUA)))
				.append(Text.literal("removed ").styled(s -> s.withColor(Formatting.GRAY)))
				.append(Text.literal(id).styled(s -> s.withColor(Formatting.RED)));

		// Send to server console
		source.getServer().sendMessage(msg);

		// Send to all OPs
		source.getServer().getPlayerManager().getPlayerList().forEach(player -> {
			if (source.getServer().getPlayerManager().isOperator(player.getGameProfile())) {
				player.sendMessage(msg.copy().styled(s -> s.withColor(Formatting.GOLD)), false);
			}
		});
	}

	public static void sendRemoveConfirmation(ServerCommandSource source, String id) {
		source.sendFeedback(
				() -> Text.literal(" - Are you sure you want to remove ")
						.append(Text.literal(id).styled(s -> s.withColor(Formatting.RED)))
						.append(Text.literal(
								"? Run the same command again within 30 seconds to confirm.")
								.styled(s -> s.withColor(Formatting.GRAY))),
				false);
	}

	public static void sendUpdatedDescription(CommandContext<ServerCommandSource> ctx, String id) {
		String senderName = ctx.getSource().getName();

		MutableText msg = Text.literal("[CommandScheduler] ")
				.append(Text.literal(senderName + " ").styled(s -> s.withColor(Formatting.AQUA)))
				.append(Text.literal("updated description for ").styled(s -> s.withColor(Formatting.GRAY)))
				.append(Text.literal(id).styled(s -> s.withColor(Formatting.YELLOW)));

		// Send to server console
		ctx.getSource().getServer().sendMessage(msg);

		// Send to all OPs
		ctx.getSource().getServer().getPlayerManager().getPlayerList().forEach(player -> {
			if (ctx.getSource().getServer().getPlayerManager().isOperator(player.getGameProfile())) {
				player.sendMessage(msg.copy().styled(s -> s.withColor(Formatting.GOLD)), false);
			}
		});
	}

	public static void sendRemovedTimeMessage(CommandContext<ServerCommandSource> ctx, String time, String id) {
		String senderName = ctx.getSource().getName();

		MutableText msg = Text.literal("[CommandScheduler] ")
				.append(Text.literal(senderName + " ").styled(s -> s.withColor(Formatting.AQUA)))
				.append(Text.literal("removed time ").styled(s -> s.withColor(Formatting.GRAY)))
				.append(Text.literal(time).styled(s -> s.withColor(Formatting.AQUA)))
				.append(Text.literal(" from ").styled(s -> s.withColor(Formatting.GRAY)))
				.append(Text.literal(id).styled(s -> s.withColor(Formatting.YELLOW)));

		// Send to server console
		ctx.getSource().getServer().sendMessage(msg);

		// Send to all OPs
		ctx.getSource().getServer().getPlayerManager().getPlayerList().forEach(player -> {
			if (ctx.getSource().getServer().getPlayerManager().isOperator(player.getGameProfile())) {
				player.sendMessage(msg.copy().styled(s -> s.withColor(Formatting.GOLD)), false);
			}
		});
	}

	public static void sendInvalidTimeFormat(CommandContext<ServerCommandSource> ctx) {
		ctx.getSource().sendError(
				Text.literal("✖ Invalid time format. Use HH.MM (24h).")
						.styled(s -> s.withColor(Formatting.RED)));
	}

	public static void sendAlreadyActiveMessage(CommandContext<ServerCommandSource> ctx, String id) {
		ctx.getSource().sendFeedback(() -> Text.literal(" - ")
				.append(Text.literal(id).styled(s -> s.withColor(Formatting.YELLOW)))
				.append(Text.literal(" is already active.").styled(s -> s.withColor(Formatting.GRAY))),
				false);
	}

	public static void sendAlreadyInactiveMessage(CommandContext<ServerCommandSource> ctx, String id) {
		ctx.getSource().sendFeedback(() -> Text.literal(" - ")
				.append(Text.literal(id).styled(s -> s.withColor(Formatting.YELLOW)))
				.append(Text.literal(" is already inactive.")
						.styled(s -> s.withColor(Formatting.GRAY))),
				false);
	}

	public static int sendHelpMenu(CommandContext<ServerCommandSource> ctx, int page) {
		ServerCommandSource source = ctx.getSource();
		source.sendFeedback(() -> Text.literal("\n"), false);

		switch (page) {
			case 1 -> sendHelpPage1(source);
			case 2 -> sendHelpPage2(source);
			case 3 -> sendHelpPage3(source);
			case 4 -> sendHelpPage4(source);
			default -> {
				source.sendFeedback(() -> Text.literal("§6[CommandScheduler Help Page " + page + "/4]"), false);
				source.sendFeedback(() -> Text.literal("This page doesn't exist."), false);
			}
		}
		return 1;
	}

	private static void sendHelpPage1(ServerCommandSource source) {
		source.sendFeedback(() -> Text.literal("[CommandScheduler Help Page 1/4]")
				.styled(s -> s.withColor(Formatting.GOLD).withBold(true)),
				false);

		source.sendFeedback(() -> Messages.styledCommand(""), false);

		source.sendFeedback(() -> Messages.styledCommand("help ")
				.append(Messages.arg("[page]")), false);

		source.sendFeedback(() -> Messages.styledCommand("about"),
				false);

		source.sendFeedback(() -> Messages.styledCommand("reload"),
				false);

		source.sendFeedback(() -> Text.literal("For commands on creating new schedulers, go to page 2")
				.styled(s -> s.withColor(Formatting.DARK_GRAY)),
				false);
	}

	private static void sendHelpPage2(ServerCommandSource source) {
		source.sendFeedback(() -> Text.literal("[CommandScheduler Help Page 2/4]")
				.styled(s -> s.withColor(Formatting.GOLD).withBold(true)),
				false);

		source.sendFeedback(() -> Messages.styledCommand("add interval ")
				.append(Messages.arg("<id>")).append(" ")
				.append(Messages.arg("<unit>")).append(" ")
				.append(Messages.arg("<interval>")).append(" ")
				.append(Messages.arg("<command>")),
				false);

		source.sendFeedback(() -> Messages.styledCommand("add clockbased ")
				.append(Messages.arg("<id>")).append(" ")
				.append(Messages.arg("<command>")),
				false);

		source.sendFeedback(() -> Messages.styledCommand("add atboot ")
				.append(Messages.arg("<id>")).append(" ")
				.append(Messages.arg("<command>")),
				false);

		source.sendFeedback(() -> Text.literal("For commands on listing details for schedulers, go to page 3")
				.styled(s -> s.withColor(Formatting.DARK_GRAY)),
				false);
	}

	private static void sendHelpPage3(ServerCommandSource source) {
		source.sendFeedback(() -> Text.literal("[CommandScheduler Help Page 3/4]")
				.styled(s -> s.withColor(Formatting.GOLD).withBold(true)),
				false);

		source.sendFeedback(() -> Messages.styledCommand("list active"), false);

		source.sendFeedback(() -> Messages.styledCommand("list inactive"), false);

		source.sendFeedback(() -> Messages.styledCommand("list atboot ")
				.append(Messages.arg("[page]", Formatting.GRAY)), false);

		source.sendFeedback(() -> Messages.styledCommand("list interval ")
				.append(Messages.arg("[page]", Formatting.GRAY)), false);

		source.sendFeedback(() -> Messages.styledCommand("list clockbased ")
				.append(Messages.arg("[page]", Formatting.GRAY)), false);

		source.sendFeedback(() -> Messages.styledCommand("details ")
				.append(Messages.arg("<id>", Formatting.GRAY)), false);

		source.sendFeedback(() -> Text.literal("For commands on modifying schedulers, go to page 4")
				.styled(s -> s.withColor(Formatting.DARK_GRAY)),
				false);
	}

	private static void sendHelpPage4(ServerCommandSource source) {
		source.sendFeedback(() -> Text.literal("[CommandScheduler Help Page 4/4]")
				.styled(s -> s.withColor(Formatting.GOLD).withBold(true)),
				false);

		source.sendFeedback(() -> Messages.styledCommand("activate ")
				.append(Messages.arg("<id>")), false);

		source.sendFeedback(() -> Messages.styledCommand("deactivate ")
				.append(Messages.arg("<id>")), false);

		source.sendFeedback(() -> Messages.styledCommand("rename ")
				.append(Messages.arg("<id>")).append(" ")
				.append(Messages.arg("<new id>")), false);

		source.sendFeedback(() -> Messages.styledCommand("description ")
				.append(Messages.arg("<id>")).append(" ")
				.append(Messages.arg("<description>")),
				false);

		source.sendFeedback(() -> Messages.styledCommand("addtime ")
				.append(Messages.arg("<id>")).append(" ")
				.append(Messages.arg("<time>")),
				false);

		source.sendFeedback(() -> Messages.styledCommand("removetime ")
				.append(Messages.arg("<id>")).append(" ")
				.append(Messages.arg("<time>")),
				false);

		source.sendFeedback(() -> Messages.styledCommand("remove ")
				.append(Messages.arg("<id>")),
				false);

		source.sendFeedback(() -> Text.literal("For other questions, check modrinth or the github repository")
				.styled(s -> s.withColor(Formatting.DARK_GRAY)),
				false);

	}

	public static <T extends Scheduler> void sendList(ServerCommandSource source,
			List<T> list, Boolean activeOnly) {

		List<T> filtered = new ArrayList<>();
		for (T cmd : list) {
			if (activeOnly == null || cmd.isActive() == activeOnly) {
				filtered.add(cmd);
			}
		}

		if (filtered.isEmpty()) {
			String msg = activeOnly == null ? "§8(no schedulers found)"
					: activeOnly ? "§8(no active schedulers found)"
							: "§8(no inactive schedulers found)";
			source.sendFeedback(() -> Text.literal(msg), false);
			return;
		}

		for (int i = 0; i < Math.min(4, filtered.size()); i++) {
			T cmd = filtered.get(i);
			String id = cmd.getID();
			boolean isActive = cmd.isActive();

			source.sendFeedback(() -> Text.literal(" - ")
					.append(Text.literal(id).styled(s -> s.withColor(Formatting.YELLOW)))
					.append(Text.literal(" (" + (isActive ? "active" : "inactive") + ")")
							.styled(s -> s.withColor(Formatting.GRAY))),
					false);
		}

		if (filtered.size() > 4) {
			int more = filtered.size() - 4;
			source.sendFeedback(() -> Text.literal("and " + more + " more...")
					.styled(s -> s.withColor(Formatting.DARK_GRAY).withItalic(true)), false);
		}
	}

	public static <T extends Scheduler> void sendListOfType(ServerCommandSource source, List<T> fullList, int page,
			String title, int perPage) {
		int total = fullList.size();

		// If the list is empty, show "no schedulers found" message and return
		if (total == 0) {
			source.sendFeedback(() -> Text.literal("\n§6[" + title + "]"), false);
			source.sendFeedback(() -> Text.literal("§8(no schedulers found)"), false);
			return;
		}

		int maxPages = (int) Math.ceil((double) total / perPage);
		if (page < 1 || page > maxPages) {
			source.sendFeedback(() -> Text.literal("§6[" + title + " Page " + page + "/" + maxPages + "]"), false);
			source.sendFeedback(() -> Text.literal("This page doesn't exist."), false);
			return;
		}

		source.sendFeedback(() -> Text.literal("\n§6[" + title + " Page " + page + "/" + maxPages + "]"), false);

		int start = (page - 1) * perPage;
		int end = Math.min(start + perPage, total);

		for (int i = start; i < end; i++) {
			T cmd = fullList.get(i);
			String id = cmd.getID();
			boolean isActive = cmd.isActive();

			source.sendFeedback(() -> Text.literal(" - ")
					.append(Text.literal(id).styled(s -> s.withColor(Formatting.YELLOW)))
					.append(Text.literal(" (" + (isActive ? "active" : "inactive") + ")")
							.styled(s -> s.withColor(Formatting.GRAY))),
					false);
		}
	}

	public static void sendListHeader(ServerCommandSource source, String title) {
		source.sendFeedback(() -> Text.literal("\n§6[" + title + "]"), false);
	}

}
