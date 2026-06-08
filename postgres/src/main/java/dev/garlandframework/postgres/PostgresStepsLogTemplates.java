package dev.garlandframework.postgres;

public final class PostgresStepsLogTemplates {

    // medium purple — static labels
    private static final String DB    = "\033[38;5;141m";
    // light purple — dynamic values
    private static final String DATA  = "\033[38;5;183m";
    private static final String RESET = "\033[0m";

    public static final String FIND_BY_ID =
            DB + "Finding entity by id: type=" + RESET + DATA + "{}" + RESET +
            DB + ", id=" + RESET + DATA + "{}" + RESET;

    public static final String FOUND =
            DB + "Entity found: type=" + RESET + DATA + "{}" + RESET +
            DB + ", id=" + RESET + DATA + "{}" + RESET;

    public static final String NOT_FOUND =
            DB + "Entity not found: type=" + RESET + DATA + "{}" + RESET +
            DB + ", id=" + RESET + DATA + "{}" + RESET;

    public static final String EXISTS_CHECK =
            DB + "Checking entity existence: type=" + RESET + DATA + "{}" + RESET +
            DB + ", id=" + RESET + DATA + "{}" + RESET;

    public static final String EXISTS_RESULT =
            DB + "Entity exists=" + RESET + DATA + "{}" + RESET +
            DB + ": type=" + RESET + DATA + "{}" + RESET +
            DB + ", id=" + RESET + DATA + "{}" + RESET;

    public static final String FIND_BY_FIELDS =
            DB + "Finding entity by fields: type=" + RESET + DATA + "{}" + RESET;

    public static final String COUNT_BY_FIELDS =
            DB + "Counting entities by fields: type=" + RESET + DATA + "{}" + RESET;

    public static final String COUNT_RESULT =
            DB + "Count result: " + RESET + DATA + "{}" + RESET +
            DB + " entities of type=" + RESET + DATA + "{}" + RESET;

    public static final String PERSIST =
            DB + "Persisting entity: type=" + RESET + DATA + "{}" + RESET;

    public static final String PERSISTED =
            DB + "Entity persisted: type=" + RESET + DATA + "{}" + RESET;

    public static final String DELETE =
            DB + "Deleting entity: type=" + RESET + DATA + "{}" + RESET +
            DB + ", id=" + RESET + DATA + "{}" + RESET;

    public static final String DELETED =
            DB + "Entity deleted: type=" + RESET + DATA + "{}" + RESET +
            DB + ", id=" + RESET + DATA + "{}" + RESET;

    private PostgresStepsLogTemplates() {}
}
