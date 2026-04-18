package com.bot.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class DragAllCommand implements BotCommand, SlashCommandHandler {
    @Override
    public boolean matches(String commandName) {
        return "dragall".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        Member invoker = event.getMember();
        if (invoker == null) {
            event.getChannel().sendMessage("This command can only be used in a server.").queue();
            return;
        }

        var voiceState = invoker.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            event.getChannel().sendMessage("Join a voice channel first, then run +dragall.").queue();
            return;
        }

        var targetChannel = voiceState.getChannel();
        if (targetChannel == null) {
            event.getChannel().sendMessage("Join a voice channel first, then run +dragall.").queue();
            return;
        }

        executeDragAll(
                event.getGuild().getSelfMember(),
                invoker,
                targetChannel.getId(),
                event.getGuild().getMembers(),
                (moved, failed) -> event.getChannel().sendMessage(
                        "Drag complete. Moved " + moved + " member(s). Failed: " + failed + ".").queue(),
                failure -> event.getChannel().sendMessage(failure).queue());
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "dragall".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        Member invoker = event.getMember();
        if (invoker == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        var voiceState = invoker.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            event.reply("Join a voice channel first, then run /dragall.").setEphemeral(true).queue();
            return;
        }

        var targetChannel = voiceState.getChannel();
        if (targetChannel == null) {
            event.reply("Join a voice channel first, then run /dragall.").setEphemeral(true).queue();
            return;
        }

        executeDragAll(
                event.getGuild().getSelfMember(),
                invoker,
                targetChannel.getId(),
                event.getGuild().getMembers(),
                (moved, failed) -> event.reply("Drag complete. Moved " + moved + " member(s). Failed: " + failed + ".")
                        .setEphemeral(true)
                        .queue(),
                failure -> event.reply(failure).setEphemeral(true).queue());
    }

    private void executeDragAll(
            Member selfMember,
            Member invoker,
            String targetChannelId,
            List<Member> guildMembers,
            DragResultCallback onComplete,
            java.util.function.Consumer<String> onFailure) {
        if (!selfMember.hasPermission(Permission.VOICE_MOVE_OTHERS)) {
            onFailure.accept("I need the Move Members permission to do that.");
            return;
        }

        List<Member> candidates = new ArrayList<>();
        for (Member member : guildMembers) {
            if (member.getId().equals(invoker.getId())) {
                continue;
            }

            var state = member.getVoiceState();
            if (state == null || !state.inAudioChannel()) {
                continue;
            }

            var channel = state.getChannel();
            if (channel == null || channel.getId().equals(targetChannelId)) {
                continue;
            }

            candidates.add(member);
        }

        if (candidates.isEmpty()) {
            onFailure.accept("No members are in other voice channels right now.");
            return;
        }

        AtomicInteger remaining = new AtomicInteger(candidates.size());
        AtomicInteger moved = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        for (Member candidate : candidates) {
            if (!selfMember.canInteract(candidate)) {
                failed.incrementAndGet();
                if (remaining.decrementAndGet() == 0) {
                    onComplete.onResult(moved.get(), failed.get());
                }
                continue;
            }

            invoker.getGuild().moveVoiceMember(candidate, invoker.getVoiceState().getChannel())
                    .queue(
                            success -> {
                                moved.incrementAndGet();
                                if (remaining.decrementAndGet() == 0) {
                                    onComplete.onResult(moved.get(), failed.get());
                                }
                            },
                            failure -> {
                                failed.incrementAndGet();
                                if (remaining.decrementAndGet() == 0) {
                                    onComplete.onResult(moved.get(), failed.get());
                                }
                            });
        }
    }

    @FunctionalInterface
    private interface DragResultCallback {
        void onResult(int moved, int failed);
    }
}
