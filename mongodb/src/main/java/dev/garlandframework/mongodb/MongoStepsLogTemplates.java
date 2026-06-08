package dev.garlandframework.mongodb;

public final class MongoStepsLogTemplates {

    private static final String MG    = "\033[38;5;45m";
    private static final String DATA  = "\033[38;5;159m";
    private static final String RESET = "\033[0m";

    public static final String FIND_BY_ID =
            MG + "Finding document by id: type=" + RESET + DATA + "{}" + RESET +
            MG + ", id=" + RESET + DATA + "{}" + RESET;

    public static final String FOUND =
            MG + "Document found: type=" + RESET + DATA + "{}" + RESET +
            MG + ", id=" + RESET + DATA + "{}" + RESET;

    public static final String NOT_FOUND =
            MG + "Document not found: type=" + RESET + DATA + "{}" + RESET +
            MG + ", id=" + RESET + DATA + "{}" + RESET;

    public static final String FIND_BY_FIELDS =
            MG + "Finding document by fields: type=" + RESET + DATA + "{}" + RESET;

    public static final String COUNT_BY_FIELDS =
            MG + "Counting documents by fields: type=" + RESET + DATA + "{}" + RESET;

    public static final String COUNT_RESULT =
            MG + "Count result: " + RESET + DATA + "{}" + RESET +
            MG + " documents of type=" + RESET + DATA + "{}" + RESET;

    public static final String EXISTS_CHECK =
            MG + "Checking document existence: type=" + RESET + DATA + "{}" + RESET +
            MG + ", id=" + RESET + DATA + "{}" + RESET;

    public static final String EXISTS_RESULT =
            MG + "Document exists=" + RESET + DATA + "{}" + RESET +
            MG + ": type=" + RESET + DATA + "{}" + RESET +
            MG + ", id=" + RESET + DATA + "{}" + RESET;

    public static final String PERSIST =
            MG + "Persisting document: type=" + RESET + DATA + "{}" + RESET;

    public static final String PERSISTED =
            MG + "Document persisted: type=" + RESET + DATA + "{}" + RESET;

    public static final String DELETE =
            MG + "Deleting document: type=" + RESET + DATA + "{}" + RESET +
            MG + ", id=" + RESET + DATA + "{}" + RESET;

    public static final String DELETED =
            MG + "Document deleted: type=" + RESET + DATA + "{}" + RESET +
            MG + ", id=" + RESET + DATA + "{}" + RESET;

    private MongoStepsLogTemplates() {}
}
