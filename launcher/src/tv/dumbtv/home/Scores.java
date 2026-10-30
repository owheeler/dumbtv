package tv.dumbtv.home;

/** One best score per arcade game, kept in home.conf so it survives launcher updates. */
final class Scores {
    static final String[] GAMES = {"snake", "bricks", "swarm"};

    private Scores() {}

    static long best(Config cfg, String game) {
        return cfg.getLong("arcade." + game + ".best");
    }

    /** Saves {@code score} if it beats the best; true when it did. */
    static boolean submit(Config cfg, String game, long score) {
        if (score <= best(cfg, game)) return false;
        cfg.set("arcade." + game + ".best", String.valueOf(score));
        return true;
    }

    static void reset(Config cfg) {
        cfg.removePrefix("arcade.");
    }
}
