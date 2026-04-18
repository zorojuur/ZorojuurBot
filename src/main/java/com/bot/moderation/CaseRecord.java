package com.bot.moderation;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

public record CaseRecord(
		long id,
		CaseType type,
		String guildId,
		String channelId,
		String targetId,
		String targetName,
		String moderatorId,
		String moderatorName,
		String reason,
		String extra,
		Instant createdAt) implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;
}

