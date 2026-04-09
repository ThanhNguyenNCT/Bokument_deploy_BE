package com.qldapm_L01.backend_api.Exception;

public class CommentRequiresRatingException extends RuntimeException {
    public CommentRequiresRatingException(String message) {
        super(message);
    }
}
