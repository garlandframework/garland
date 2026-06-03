package org.modulartestorchestrator.kafka;

public final class KafkaTestClientLogTemplates {

    private static final String KAFKA = "\033[38;5;208m";
    private static final String DATA  = "\033[38;5;222m";
    private static final String RESET = "\033[0m";

    public static final String CONSUME =
            KAFKA + "▶ consume: " + RESET + DATA + "{}" + RESET;

    public static final String CONSUME_MATCHING =
            KAFKA + "▶ consumeMatching: " + RESET + DATA + "{}" + RESET;

    public static final String PUBLISH =
            KAFKA + "▶ publish: " + RESET + DATA + "{}" + RESET;

    public static final String VERIFIED =
            KAFKA + "✓ content verified" + RESET;

    public static final String PUBLISHED =
            KAFKA + "✓ message published" + RESET;

    private KafkaTestClientLogTemplates() {}
}
