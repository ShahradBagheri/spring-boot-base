package com.notshahrad.spring_boot_base.mail;

public final class EmailDeliveryException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final Reason reason;

    public EmailDeliveryException(Reason reason) {
        super(switch (reason) {
            case INVALID_MESSAGE -> "The email message cannot be prepared";
            case AUTHENTICATION_FAILED -> "The email transport rejected authentication";
            case TRANSPORT_FAILED -> "The email transport could not confirm delivery";
        });
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        INVALID_MESSAGE,
        AUTHENTICATION_FAILED,
        TRANSPORT_FAILED
    }
}
