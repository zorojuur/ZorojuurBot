package com.bot.moderation;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class ModerationDmService {
    private static final ModerationDmService INSTANCE = new ModerationDmService();
    private static final String GUILD_NAME = "Zorojuur Family";

    public enum DmDeliveryStatus {
        DELIVERED,
        UNDELIVERABLE,
        UNKNOWN
    }

    private ModerationDmService() {
    }

    public static ModerationDmService getInstance() {
        return INSTANCE;
    }

    public DmDeliveryStatus sendActionDm(User user, String actionPastTense, String reason) {
        if (user == null) {
            return DmDeliveryStatus.UNKNOWN;
        }

        String safeReason = (reason == null || reason.isBlank()) ? "No reason provided." : reason;
        String message = "You were " + actionPastTense + " in " + GUILD_NAME + ". | " + safeReason;

        AtomicReference<DmDeliveryStatus> result = new AtomicReference<>(DmDeliveryStatus.UNKNOWN);
        CountDownLatch latch = new CountDownLatch(1);

        user.openPrivateChannel().queue(
                channel -> channel.sendMessage(message).queue(
                        success -> {
                            result.set(DmDeliveryStatus.DELIVERED);
                            latch.countDown();
                        },
                        failure -> {
                            result.set(mapFailure(failure));
                            latch.countDown();
                        }),
                failure -> {
                    result.set(mapFailure(failure));
                    latch.countDown();
                });

        try {
            if (!latch.await(8, TimeUnit.SECONDS)) {
                return DmDeliveryStatus.UNKNOWN;
            }
            return result.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return DmDeliveryStatus.UNKNOWN;
        }
    }

    private DmDeliveryStatus mapFailure(Throwable failure) {
        if (failure instanceof ErrorResponseException exception) {
            if (exception.getErrorResponse() == ErrorResponse.CANNOT_SEND_TO_USER) {
                return DmDeliveryStatus.UNDELIVERABLE;
            }
        }
        return DmDeliveryStatus.UNKNOWN;
    }

    public String deliverySuffix(DmDeliveryStatus status) {
        if (status == DmDeliveryStatus.UNDELIVERABLE) {
            return " (DM could not be delivered).";
        }

        return "";
    }
}


