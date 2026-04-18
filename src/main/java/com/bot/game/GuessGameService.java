package com.bot.game;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class GuessGameService {
    private static final GuessGameService INSTANCE = new GuessGameService();

    private final Map<String, GameState> gamesByGuild = new ConcurrentHashMap<>();

    private GuessGameService() {
    }

    public static GuessGameService getInstance() {
        return INSTANCE;
    }

    public String startGame(String guildId) {
        int target = ThreadLocalRandom.current().nextInt(1, 51);
        gamesByGuild.put(guildId, new GameState(target, 0, true));
        return "Started a new guessing game. Pick a number from 1 to 50 with `+game guess <number>`!";
    }

    public String guess(String guildId, int guessed) {
        GameState state = gamesByGuild.get(guildId);
        if (state == null || !state.active()) {
            return "No active game. Start one with `+game start`.";
        }

        if (guessed < 1 || guessed > 50) {
            return "Guess must be between 1 and 50.";
        }

        int attempts = state.attempts() + 1;
        if (guessed == state.target()) {
            gamesByGuild.remove(guildId);
            return "Correct! The number was **" + guessed + "**. You won in **" + attempts
                    + "** attempts. Start again with `+game start`.";
        }

        String hint = guessed < state.target() ? "Too low." : "Too high.";
        gamesByGuild.put(guildId, new GameState(state.target(), attempts, true));
        return hint + " Attempts: " + attempts;
    }

    public String status(String guildId) {
        GameState state = gamesByGuild.get(guildId);
        if (state == null || !state.active()) {
            return "No active game right now.";
        }

        return "A game is active. Attempts so far: **" + state.attempts() + "**.";
    }

    public String stop(String guildId) {
        GameState removed = gamesByGuild.remove(guildId);
        if (removed == null) {
            return "No active game to stop.";
        }

        return "Game stopped. The hidden number was **" + removed.target() + "**.";
    }

    private record GameState(int target, int attempts, boolean active) {
    }
}

