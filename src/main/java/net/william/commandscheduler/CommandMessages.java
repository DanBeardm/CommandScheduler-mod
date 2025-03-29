package net.william.commandscheduler;

import java.util.List;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class CommandMessages {

        public static void sendActivationStatus(CommandContext<ServerCommandSource> ctx, String id, boolean activated) {
                MutableText message = Text.literal(" - ")
                                .append(Text.literal(id).styled(s -> s.withColor(Formatting.YELLOW)))
                                .append(Text.literal(" has been ").styled(s -> s.withColor(Formatting.GRAY)))
                                .append(Text.literal(activated ? "activated" : "deactivated")
                                                .styled(s -> s.withColor(
                                                                activated ? Formatting.GREEN : Formatting.RED)));

                ctx.getSource().sendFeedback(() -> message, false);
        }

        public static void sendNoWithID(CommandContext<ServerCommandSource> ctx, String id) {
                ctx.getSource().sendError(
                                Text.literal("✖ No scheduler found with ID ")
                                                .append(Text.literal(id).styled(s -> s.withColor(Formatting.RED))));
        }

        public static void sendNoClockBasedWithID(CommandContext<ServerCommandSource> ctx, String id) {
                ctx.getSource().sendError(
                                Text.literal("✖ No clock-based scheduler found with ID ")
                                                .append(Text.literal(id).styled(s -> s.withColor(Formatting.RED))));
        }

        public static void sendForceReloadSuccess(CommandContext<ServerCommandSource> ctx) {
                ctx.getSource().sendFeedback(
                                () -> Text.literal(" - All configs reloaded.")
                                                .styled(s -> s.withColor(Formatting.GREEN)),
                                false);
        }

        public static void sendSchedulerDetails(CommandContext<ServerCommandSource> ctx, Object cmd,
                        String id) {
                MutableText output = Text.literal("")
                                .append(Text.literal("Details for scheduler : ")
                                                .styled(s -> s.withColor(Formatting.GOLD).withBold(true)))
                                .append(Text.literal(id + "\n")
                                                .styled(s -> s.withColor(Formatting.YELLOW)));

                if (cmd instanceof IntervalCommand ic) {
                        output.append(Text.literal(" - Type: ")
                                        .styled(s -> s.withBold(true).withColor(Formatting.GRAY)))
                                        .append(Text.literal("Interval\n"));

                        output.append(label("Active")).append(Text.literal(ic.isActive() + "\n"));

                        output.append(Text.literal(" - Interval: ")
                                        .styled(s -> s.withBold(true).withColor(Formatting.GRAY)))
                                        .append(Text.literal(ic.getInterval() + " " + ic.getUnit().name().toLowerCase()
                                                        + "\n"));

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

                } else if (cmd instanceof OnceAtBootCommand oc) {
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

        public static MutableText label(String name) {
                return Text.literal(" - " + name + ": ")
                                .styled(s -> s.withBold(true).withColor(Formatting.GRAY));
        }

        public static void sendAddedTimeMessage(CommandContext<ServerCommandSource> ctx, String timeArg, String id) {
                ctx.getSource().sendFeedback(
                                () -> Text.literal(" - Added time ")
                                                .append(Text.literal(timeArg).styled(s -> s.withColor(Formatting.AQUA)))
                                                .append(Text.literal(" to ").styled(s -> s.withColor(Formatting.GRAY)))
                                                .append(Text.literal(id).styled(s -> s.withColor(Formatting.YELLOW))),
                                false);
        }

        public static void sendCreatedMessage(CommandContext<ServerCommandSource> ctx, String type, String id) {
                ctx.getSource().sendFeedback(
                                () -> Text.literal(" - Created " + type + " scheduler with ID: ")
                                                .append(Text.literal(id).styled(s -> s.withColor(Formatting.YELLOW))),
                                false);
        }

        public static void sendRenamedMessage(CommandContext<ServerCommandSource> ctx, String oldId, String newId) {
                ctx.getSource().sendFeedback(
                                () -> Text.literal(" - Renamed ")
                                                .append(Text.literal(oldId).styled(s -> s.withColor(Formatting.RED)))
                                                .append(Text.literal(" → ").styled(s -> s.withColor(Formatting.GRAY)))
                                                .append(Text.literal(newId)
                                                                .styled(s -> s.withColor(Formatting.YELLOW))),
                                false);
        }

        public static void sendRemovedMessage(ServerCommandSource source, String id) {
                source.sendFeedback(
                                () -> Text.literal(" - Removed ")
                                                .append(Text.literal(id).styled(s -> s.withColor(Formatting.RED))),
                                false);
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
                ctx.getSource().sendFeedback(
                                () -> Text.literal(" - Updated description for ")
                                                .append(Text.literal(id).styled(s -> s.withColor(Formatting.YELLOW))),
                                false);
        }

        public static void sendRemovedTimeMessage(CommandContext<ServerCommandSource> ctx, String time, String id) {
                ctx.getSource().sendFeedback(
                                () -> Text.literal(" - Removed time ")
                                                .append(Text.literal(time).styled(s -> s.withColor(Formatting.AQUA)))
                                                .append(Text.literal(" from ")
                                                                .styled(s -> s.withColor(Formatting.GRAY)))
                                                .append(Text.literal(id).styled(s -> s.withColor(Formatting.YELLOW))),
                                false);
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
}
