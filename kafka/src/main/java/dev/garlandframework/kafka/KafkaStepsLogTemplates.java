package dev.garlandframework.kafka;

public final class KafkaStepsLogTemplates {

    private static final String KAFKA = "\033[38;5;208m";
    private static final String DATA  = "\033[38;5;222m";
    private static final String RESET = "\033[0m";

    public static final String CONSUMING =
            KAFKA + "Consuming message from topic: " + RESET + DATA + "{}" + RESET;

    public static final String CONSUMED =
            KAFKA + "Message consumed: topic=" + RESET + DATA + "{}" + RESET +
            KAFKA + ", key=" + RESET + DATA + "{}" + RESET +
            KAFKA + ", partition=" + RESET + DATA + "{}" + RESET +
            KAFKA + ", offset=" + RESET + DATA + "{}" + RESET;

    public static final String NOT_CONSUMED =
            KAFKA + "No message received from topic: " + RESET + DATA + "{}" + RESET;

    public static final String PRODUCING =
            KAFKA + "Producing message to topic: " + RESET + DATA + "{}" + RESET +
            KAFKA + ", key=" + RESET + DATA + "{}" + RESET;

    public static final String PRODUCED =
            KAFKA + "Message produced: topic=" + RESET + DATA + "{}" + RESET +
            KAFKA + ", key=" + RESET + DATA + "{}" + RESET;

    private KafkaStepsLogTemplates() {}
}
