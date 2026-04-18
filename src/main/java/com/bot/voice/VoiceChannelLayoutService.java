 package com.bot.voice;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

public final class VoiceChannelLayoutService {
    private static final VoiceChannelLayoutService INSTANCE = new VoiceChannelLayoutService();
    private static final String VOICE_CATEGORY_NAME = "voicechats";

    private static final List<VoiceChannelSpec> TARGET_CHANNELS = List.of(
            new VoiceChannelSpec("duo vc", 2),
            new VoiceChannelSpec("trio vc", 3),
            new VoiceChannelSpec("general vc", 0),
            new VoiceChannelSpec("music vc", 0));

    private VoiceChannelLayoutService() {
    }

    public static VoiceChannelLayoutService getInstance() {
        return INSTANCE;
    }

    public void ensureVoiceChannelLayout(Guild guild) {
        Member selfMember = guild.getSelfMember();
        if (!selfMember.hasPermission(Permission.MANAGE_CHANNEL)) {
            return;
        }

        Category voiceCategory = resolveOrCreateVoiceCategory(guild);
        if (voiceCategory == null) {
            return;
        }

        moveCategoryToEnd(guild, voiceCategory);

        if (alreadyMatchesTargetLayout(guild, voiceCategory)) {
            return;
        }

        detachAfkChannelIfNeeded(guild, selfMember);

        for (var stageChannel : guild.getStageChannels()) {
            try {
                stageChannel.delete().reason("Resetting VC layout").complete();
            } catch (Exception ignored) {
            }
        }

        for (var voiceChannel : guild.getVoiceChannels()) {
            try {
                voiceChannel.delete().reason("Resetting VC layout").complete();
            } catch (Exception ignored) {
            }
        }

        for (VoiceChannelSpec spec : TARGET_CHANNELS) {
            try {
                var action = guild.createVoiceChannel(spec.name()).setParent(voiceCategory);
                if (spec.userLimit() > 0) {
                    action = action.setUserlimit(spec.userLimit());
                }
                action.reason("Creating default VC layout").complete();
            } catch (Exception ignored) {
            }
        }
    }

    private boolean alreadyMatchesTargetLayout(Guild guild, Category voiceCategory) {
        if (!guild.getStageChannels().isEmpty()) {
            return false;
        }

        List<VoiceChannel> voiceChannels = guild.getVoiceChannels();
        if (voiceChannels.size() != TARGET_CHANNELS.size()) {
            return false;
        }

        Map<String, Integer> current = voiceChannels.stream()
                .collect(Collectors.toMap(
                        channel -> channel.getName().toLowerCase(),
                        VoiceChannel::getUserLimit,
                        (left, right) -> left));

        for (VoiceChannelSpec spec : TARGET_CHANNELS) {
            Integer limit = current.get(spec.name());
            if (limit == null || limit != spec.userLimit()) {
                return false;
            }
        }

        for (VoiceChannel voiceChannel : voiceChannels) {
            if (voiceChannel.getParentCategoryIdLong() != voiceCategory.getIdLong()) {
                return false;
            }
        }

        int lastIndex = guild.getCategories().size() - 1;
        if (lastIndex >= 0 && voiceCategory.getPositionRaw() != lastIndex) {
            return false;
        }

        return true;
    }

    private Category resolveOrCreateVoiceCategory(Guild guild) {
        List<Category> existing = guild.getCategoriesByName(VOICE_CATEGORY_NAME, true);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        try {
            return guild.createCategory(VOICE_CATEGORY_NAME).reason("Creating voice category").complete();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void moveCategoryToEnd(Guild guild, Category category) {
        int lastIndex = guild.getCategories().size() - 1;
        if (lastIndex < 0 || category.getPositionRaw() == lastIndex) {
            return;
        }

        try {
            category.getManager().setPosition(lastIndex).reason("Move voice category to end").complete();
        } catch (Exception ignored) {
        }
    }

    private void detachAfkChannelIfNeeded(Guild guild, Member selfMember) {
        var afkChannel = guild.getAfkChannel();
        if (afkChannel == null) {
            return;
        }

        if (!selfMember.hasPermission(Permission.MANAGE_SERVER)) {
            return;
        }

        try {
            guild.getManager().setAfkChannel(null).reason("Resetting VC layout").complete();
        } catch (Exception ignored) {
        }
    }

    private record VoiceChannelSpec(String name, int userLimit) {
    }
}
