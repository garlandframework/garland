package dev.garlandframework.mongodb;

public final class MongoTestClientLogTemplates {

    private static final String MG    = "\033[38;5;45m";
    private static final String DATA  = "\033[38;5;159m";
    private static final String RESET = "\033[0m";

    public static final String FIND_BY_ID =
            MG + "▶ findById: " + RESET + DATA + "{}" + RESET;

    public static final String FIND_BY_FIELDS =
            MG + "▶ findByFields: " + RESET + DATA + "{}" + RESET;

    public static final String COUNT_BY_FIELDS =
            MG + "▶ countByFields: " + RESET + DATA + "{}" + RESET;

    public static final String PERSIST =
            MG + "▶ persist: " + RESET + DATA + "{}" + RESET;

    public static final String EXISTS =
            MG + "▶ exists: " + RESET + DATA + "{}" + RESET;

    public static final String NOT_EXISTS =
            MG + "▶ notExists: " + RESET + DATA + "{}" + RESET;

    public static final String DELETE =
            MG + "▶ delete: " + RESET + DATA + "{}" + RESET;

    public static final String VERIFIED =
            MG + "✓ data verified" + RESET;

    public static final String ABSENT =
            MG + "✓ document absent" + RESET;

    private MongoTestClientLogTemplates() {}
}
