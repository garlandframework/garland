package dev.garlandframework.postgres;

public final class PostgresTestClientLogTemplates {

    private static final String DB    = "\033[38;5;141m";
    private static final String DATA  = "\033[38;5;183m";
    private static final String RESET = "\033[0m";

    public static final String FIND_BY_ID =
            DB + "▶ findById: " + RESET + DATA + "{}" + RESET;

    public static final String FIND_BY_FIELDS =
            DB + "▶ findByFields: " + RESET + DATA + "{}" + RESET;

    public static final String COUNT_BY_FIELDS =
            DB + "▶ countByFields: " + RESET + DATA + "{}" + RESET;

    public static final String PERSIST =
            DB + "▶ persist: " + RESET + DATA + "{}" + RESET;

    public static final String EXISTS =
            DB + "▶ exists: " + RESET + DATA + "{}" + RESET;

    public static final String NOT_EXISTS =
            DB + "▶ notExists: " + RESET + DATA + "{}" + RESET;

    public static final String DELETE =
            DB + "▶ delete: " + RESET + DATA + "{}" + RESET;

    public static final String VERIFIED =
            DB + "✓ data verified" + RESET;

    public static final String ABSENT =
            DB + "✓ entity absent" + RESET;

    private PostgresTestClientLogTemplates() {}
}
