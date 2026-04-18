package com.bot.commands;

import java.util.List;

import com.bot.moderation.AccessControlService;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class CommandRegistry {
    private static final String PREFIX = "+";
    private final AccessControlService accessControl = AccessControlService.getInstance();

    private final List<BotCommand> commands = List.of(
            new GreetingCommand(),
            new GameCommand(),
            new HelpCommand(),
            new PostReactionRolesCommand(),
            new DeleteReactionRolesCommand(),
            new PostRoleInfoCommand(),
            new PostRulesCommand(),
            new SetupVerifyPrototypeCommand(),
            new PostVerifyPrototypeCommand(),
            new SetupCommand(),
            new MuteCommand(),
            new UnmuteCommand(),
            new KickCommand(),
            new BanCommand(),
            new UnbanCommand(),
            new DragAllCommand(),
            new PurgeCommand(),
            new CleanCommand(),
            new LockCommand(),
            new UnlockCommand(),
            new SlowmodeCommand(),
            new ClearWarnsCommand(),
            new WarnCommand(),
            new DmCommand(),
            new CaseCommand(),
            new CaseDeleteCommand(),
            new RoleCommand(),
            new TmodCommand(),
            new GiveAllCommand(),
            new WarningInfoCommand(),
            new ModLogsCommand(),
            new ReasonCommand());

    public boolean dispatch(MessageReceivedEvent event, String message) {
        if (!message.startsWith(PREFIX) || message.length() == 1) {
            return false;
        }

        String[] parts = message.substring(1).trim().split("\\s+");
        if (parts.length == 0 || parts[0].isBlank()) {
            return false;
        }

        String commandName = parts[0].toLowerCase();
        if (!accessControl.canUseCommand(event.getMember(), commandName)) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    "Access Denied",
                    "Only staff can use bot commands. Regular members can only use +game."))
                    .queue();
            return true;
        }

        String[] args = new String[Math.max(0, parts.length - 1)];
        if (args.length > 0) {
            System.arraycopy(parts, 1, args, 0, args.length);
        }

        for (BotCommand command : commands) {
            if (command.matches(commandName)) {
                command.execute(event, commandName, args);
                return true;
            }
        }

        return false;
    }

    public boolean dispatchSlash(SlashCommandInteractionEvent event) {
        String commandName = event.getName().toLowerCase();
        if (!accessControl.canUseCommand(event.getMember(), commandName)) {
            event.replyEmbeds(CommandTemplateEmbeds.error(
                    "Access Denied",
                    "Only staff can use bot commands. Regular members can only use /game."))
                    .setEphemeral(true)
                    .queue();
            return true;
        }

        for (BotCommand command : commands) {
            if (command instanceof SlashCommandHandler slashHandler && slashHandler.handlesSlash(commandName)) {
                slashHandler.executeSlash(event);
                return true;
            }
        }

        return false;
    }
}