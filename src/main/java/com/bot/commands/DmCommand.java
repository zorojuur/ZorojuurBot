package com.bot.commands;

import java.util.List;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class DmCommand implements BotCommand {
    @Override
    public boolean matches(String commandName) {
        return "dm".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (args.length < 1) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.usage(
                    "dm",
                    "+dm <user-id|@mention|username> <message or media>",
                    "+dm jesterskitt Please check this image (attach image or paste link)."))
                    .queue();
            return;
        }

        String targetInput = args[0];
        String messageContent = CommandTextUtil.joinArgs(args, 1, "").trim();
        List<Message.Attachment> attachments = event.getMessage().getAttachments();

        String attachmentUrls = attachments.stream()
                .map(Message.Attachment::getUrl)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

        String payload = messageContent;
        if (!attachmentUrls.isEmpty()) {
            payload = payload.isEmpty() ? attachmentUrls : payload + "\n\n" + attachmentUrls;
        }

        if (payload.isEmpty()) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    "DM",
                    "Please provide text, a media link, or attach a file to send."))
                    .queue();
            return;
        }

        if (payload.length() > 2000) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    "DM",
                    "DM content is too long after adding media links. Keep it under 2000 characters."))
                    .queue();
            return;
        }

        final String finalPayload = payload;

        TargetResolver.resolveMemberAsync(
                event.getGuild(),
                targetInput,
                member -> sendDirectMessage(event, member, finalPayload),
                failure -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                        "DM",
                        failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND
                                ? "I couldn't find that member in this server."
                                : "I couldn't look up that member right now."))
                        .queue());
    }

    private void sendDirectMessage(MessageReceivedEvent event, Member member, String content) {
        member.getUser().openPrivateChannel().queue(
                dmChannel -> dmChannel.sendMessage(content).queue(
                        success -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.success(
                                "DM",
                                "Sent a DM to " + member.getUser().getAsTag() + "."))
                                .queue(),
                        failure -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                                "DM",
                                describeDmFailure(failure)))
                                .queue()),
                failure -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                        "DM",
                        describeDmFailure(failure)))
                        .queue());
    }

    private String describeDmFailure(Throwable failure) {
        if (!(failure instanceof ErrorResponseException exception)) {
            return "I couldn't send that DM right now. Please try again.";
        }

        ErrorResponse error = exception.getErrorResponse();
        if (error == ErrorResponse.CANNOT_SEND_TO_USER) {
            return "I couldn't send a DM to that user. Their DMs are likely closed.";
        }
        if (error == ErrorResponse.UNKNOWN_USER || error == ErrorResponse.UNKNOWN_MEMBER) {
            return "I couldn't send a DM because that user is no longer available.";
        }
        if (error == ErrorResponse.MESSAGE_BLOCKED_BY_AUTOMOD) {
            return "I couldn't send that DM because Discord blocked the message content.";
        }

        return "I couldn't send that DM right now (" + error + ").";
    }
}




