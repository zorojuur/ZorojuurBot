package com.bot.commands;

import java.awt.Color;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class PostRulesCommand implements BotCommand {
    private static final String TARGET_RULES_CHANNEL_ID = "1475416785493688350";
    private static final Color TEMPLATE_COLOR = new Color(102, 2, 60);

    @Override
    public boolean matches(String commandName) {
        return "postrules".equalsIgnoreCase(commandName)
                || "rulespanel".equalsIgnoreCase(commandName)
                || "rulespost".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        TextChannel rulesChannel = event.getGuild().getTextChannelById(TARGET_RULES_CHANNEL_ID);
        if (rulesChannel == null) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    "Rules",
                    "I couldn't find the rules channel (`" + TARGET_RULES_CHANNEL_ID + "`)."))
                    .queue();
            return;
        }

        EmbedBuilder intro = new EmbedBuilder()
                .setTitle("SERVER RULES")
                .setColor(TEMPLATE_COLOR)
                .setDescription("Welcome! These rules exist to keep this community safe, enjoyable, and fair for everyone.\n"
                        + "Ignorance of the rules is not an excuse - by being here, you agree to follow them.");

        EmbedBuilder sectionOne = buildSectionEmbed(
                "1. RESPECT & CONDUCT",
                "**1.1** **Treat Members With Respect**\n"
                        + "- Treat every member with basic human decency. Rudeness, belittling, or condescension is not welcome here.\n\n"
                        + "**1.2** **No Hate Speech Or Targeted Harassment**\n"
                        + "- Hate speech, slurs, discrimination, racism, homophobia, or targeted harassment in any form will not be tolerated.\n\n"
                        + "**1.3** **No Threats, Bullying, Or Intimidation**\n"
                        + "- Bullying, threats, death threats, blackmail, or intimidation - even as a joke - will result in punishment.\n\n"
                        + "**1.4** **No Baiting Or Provocation**\n"
                        + "- Do not provoke, bait, or manipulate others into arguments or rule-breaking.");

        EmbedBuilder sectionTwo = buildSectionEmbed(
                "2. CONTENT & LANGUAGE",
                "**2.1** **English Only**\n"
                        + "- This server is English only so staff can properly moderate all conversations.\n\n"
                        + "**2.2** **No NSFW Or Explicit Content**\n"
                        + "- No NSFW, explicit, or adult content of any kind, including images, links, text, jokes, or profile content.\n\n"
                        + "**2.3** **No Gore Or Extremist Content**\n"
                        + "- No gore, Nazi imagery, or references to massacres. Violations result in an immediate ban.\n\n"
                        + "**2.4** **Avoid Heavy Trigger Topics**\n"
                        + "- Avoid heavy or triggering topics such as suicide, self-harm, politics, or religion.");

        EmbedBuilder sectionThree = buildSectionEmbed(
                "3. SAFETY & PRIVACY",
                "**3.1** **No Doxxing**\n"
                        + "- Sharing private information without explicit consent results in an immediate permanent ban.\n\n"
                        + "**3.2** **No Scams Or Malicious Activity**\n"
                        + "- Scamming, fraud, phishing links, malicious files, or black market activity is strictly forbidden.\n\n"
                        + "**3.3** **Protect Sensitive Information**\n"
                        + "- Do not share account credentials or sensitive information. Report security concerns to staff immediately.\n\n"
                        + "**3.4** **Zero Tolerance For Minor Exploitation**\n"
                        + "- Grooming, pedophilic behavior, or exploitation of minors is illegal and will result in a permanent ban and report to authorities.");

        EmbedBuilder sectionFour = buildSectionEmbed(
                "4. CHAT & CHANNEL BEHAVIOR",
                "**4.1** **Use Channels Correctly**\n"
                        + "- Use each channel for its intended purpose. Check channel descriptions if you're unsure.\n\n"
                        + "**4.2** **No Spam Or Flooding**\n"
                        + "- No spamming, flooding, excessive caps, repeated emojis, or meaningless messages.\n\n"
                        + "**4.3** **Use Bot Channels For Commands**\n"
                        + "- Bot commands are only allowed in their designated bot channels.\n\n"
                        + "**4.4** **No Intentional Misinformation**\n"
                        + "- Do not spread misinformation, fake news, or rumors intentionally.\n\n"
                        + "**4.5** **No Impersonation**\n"
                        + "- No impersonating staff, members, or public figures in any way.");

        EmbedBuilder sectionFive = buildSectionEmbed(
                "5. PROMOTION & ADVERTISING",
                "**5.1** **No Advertising Without Approval**\n"
                        + "- Advertising servers, services, products, or social media is not allowed without prior staff approval.\n\n"
                        + "**5.2** **No DM Advertising**\n"
                        + "- DM advertising to members is prohibited and will result in a ban.\n\n"
                        + "**5.3** **Limited Self-Promotion**\n"
                        + "- Self-promotion is only permitted in designated channels and with staff permission.\n\n"
                        + "**5.4** **No Crosstrading**\n"
                        + "- Crosstrading or any trade involving items outside the game is completely banned.");

        EmbedBuilder sectionSix = buildSectionEmbed(
                "6. STAFF & MODERATION",
                "**6.1** **Follow Staff Instructions**\n"
                        + "- Staff instructions are to be followed without public argument. Disrespecting staff will not be tolerated.\n\n"
                        + "**6.2** **Appeal Privately**\n"
                        + "- If you disagree with a moderation decision, open a ticket or DM a higher rank. Do not argue in public channels.\n\n"
                        + "**6.3** **No Mini-Modding**\n"
                        + "- Do not mini-mod or speak on behalf of staff. If you see an issue, report it and let staff handle it.\n\n"
                        + "**6.4** **No Loophole Abuse**\n"
                        + "- Do not look for loopholes or exploit gaps in these rules. Use common sense.");

        EmbedBuilder sectionSeven = buildSectionEmbed(
                "7. EXTERNAL TERMS & GUIDELINES",
                "**7.1** **Follow Discord Terms & Guidelines**\n"
                        + "- All members must follow Discord's Terms of Service and Community Guidelines at all times.\n\n"
                        + "**7.2** **No Illegal Content Or Activity**\n"
                        + "- Sharing pirated content, hacking tools, or participating in illegal activity is strictly forbidden.\n\n"
                        + "**7.3** **No Raids Or Cross-Server Disruption**\n"
                        + "- Raiding, trolling, or disrupting other servers, including affiliates, will result in a ban.\n\n"
                        + "**7.4** **No Exploit Promotion**\n"
                        + "- Exploits or cheats must not be shared or promoted in any channel.");

        EmbedBuilder sectionEight = buildSectionEmbed(
                "8. CONSEQUENCES",
                "Rule violations are handled based on severity and history:\n\n"
                        + "**8.1** **Warning**\n"
                        + "- Minor or first-time offenses.\n\n"
                        + "**8.2** **Mute**\n"
                        + "- Repeated or disruptive behavior.\n\n"
                        + "**8.3** **Kick**\n"
                        + "- Serious violations or failure to comply.\n\n"
                        + "**8.4** **Ban**\n"
                        + "- Severe violations or repeat offenses.\n\n"
                        + "Certain offenses such as doxxing, NSFW content, grooming, scamming, or hate speech result in an immediate permanent ban with no appeal.\n\n"
                        + "By remaining in this server you acknowledge and agree to all of the above rules.\n"
                        + "If you have any questions, open a ticket or reach out to a staff member.");

        rulesChannel.sendMessageEmbeds(intro.build()).queue();
        rulesChannel.sendMessageEmbeds(sectionOne.build()).queue();
        rulesChannel.sendMessageEmbeds(sectionTwo.build()).queue();
        rulesChannel.sendMessageEmbeds(sectionThree.build()).queue();
        rulesChannel.sendMessageEmbeds(sectionFour.build()).queue();
        rulesChannel.sendMessageEmbeds(sectionFive.build()).queue();
        rulesChannel.sendMessageEmbeds(sectionSix.build()).queue();
        rulesChannel.sendMessageEmbeds(sectionSeven.build()).queue();
        rulesChannel.sendMessageEmbeds(sectionEight.build()).queue(
                success -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.success(
                        "Rules",
                        "Rules template sent in <#" + TARGET_RULES_CHANNEL_ID + ">."))
                        .queue(),
                failure -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                        "Rules",
                        "I couldn't post rules in the target channel."))
                        .queue());
    }

    private EmbedBuilder buildSectionEmbed(String title, String content) {
        return new EmbedBuilder()
                .setColor(TEMPLATE_COLOR)
                .addField("**" + title + "**", content, false);
    }
}






