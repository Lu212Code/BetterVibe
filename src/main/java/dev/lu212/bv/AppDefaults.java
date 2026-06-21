package dev.lu212.bv;

public final class AppDefaults {
    public static final String DB_DIR = System.getProperty("user.home") + "/.bettervibe";
    public static final String DB_PATH = DB_DIR + "/bettervibe.db";
    public static final String CONFIG_FILE = DB_DIR + "/config.json";

    public static final String DEFAULT_PROJECT_PATH = System.getProperty("user.dir");

    public static final String WATCH_IGNORE = ".git,target,node_modules,.idea,*.class,*.jar,build,dist,.mvn,.gradle";

    public static final String VERSION = "0.0.1";

    private AppDefaults() {}
}
