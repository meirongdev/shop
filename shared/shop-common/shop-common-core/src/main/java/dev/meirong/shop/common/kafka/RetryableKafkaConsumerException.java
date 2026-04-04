package dev.meirong.shop.common.kafka;

public class RetryableKafkaConsumerException extends RuntimeException {

    public RetryableKafkaConsumerException(String message) {
        super(message);
    }

    public RetryableKafkaConsumerException(String message, Throwable cause) {
        super(message, cause);
    }
}
