package org.modulartestorchestrator.mongodb;

public final class MongoCheckStepsLogTemplates {

    private static final String MG    = "\033[38;5;45m";
    private static final String DATA  = "\033[38;5;159m";
    private static final String RESET = "\033[0m";

    public static final String CHECKING_EXISTS =
            MG + "Checking document exists in MongoDB" + RESET;

    public static final String EXISTS_PASSED =
            MG + "Document exists check passed" + RESET;

    public static final String CHECKING_NOT_EXISTS =
            MG + "Checking document does not exist in MongoDB" + RESET;

    public static final String NOT_EXISTS_PASSED =
            MG + "Document not-exists check passed" + RESET;

    private MongoCheckStepsLogTemplates() {}
}
