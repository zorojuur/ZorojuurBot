package com.bot.commands;

import com.bot.moderation.DeletedMessageLogService;
import com.bot.moderation.EditedMessageLogService;
import com.bot.moderation.ModLogService;
import com.bot.moderation.MutedRoleService;
import com.bot.moderation.SetupStateService;
import com.bot.voice.VoiceChannelLayoutService;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class SetupCommand implements BotCommand, SlashCommandHandler {
    private final SetupStateService setupStateService = SetupStateService.getInstance();
    private final ModLogService modLogService = ModLogService.getInstance();
    private final DeletedMessageLogService deletedMessageLogService = DeletedMessageLogService.getInstance();
    private final EditedMessageLogService editedMessageLogService = EditedMessageLogService.getInstance();
    private final MutedRoleService mutedRoleService = MutedRoleService.getInstance();
    private final VoiceChannelLayoutService voiceChannelLayoutService = VoiceChannelLayoutService.getInstance();

    @Override
    public boolean matches(String commandName) {
        return "setup".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        Guild guild = event.getGuild();
        if (setupStateService.isSetupComplete(guild.getId())) {
            event.getChannel().sendMessage("Setup is already completed for this server.").queue();
            return;
        }

        performSetup(guild);
        setupStateService.markSetupComplete(guild.getId());
        event.getChannel().sendMessage("Setup complete. Channels and core services are ready.").queue();
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "setup".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("This command can only be used in servers.").setEphemeral(true).queue();
            return;
        }

        if (setupStateService.isSetupComplete(guild.getId())) {
            event.reply("Setup is already completed for this server.").setEphemeral(true).queue();
            return;
        }

        performSetup(guild);
        setupStateService.markSetupComplete(guild.getId());
        event.reply("Setup complete. Channels and core services are ready.").setEphemeral(true).queue();
    }

    private void performSetup(Guild guild) {
        modLogService.ensureChannel(guild);
        deletedMessageLogService.ensureChannel(guild);
        editedMessageLogService.ensureChannel(guild);
        mutedRoleService.ensureMutedRoleChannelPermissions(guild);
        voiceChannelLayoutService.ensureVoiceChannelLayout(guild);
    }
}
