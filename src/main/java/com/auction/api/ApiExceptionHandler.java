package com.auction.api;

import com.auction.application.AuctionNotFoundException;
import com.auction.domain.auction.exceptions.AuctionDomainException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Translates domain and persistence failures into meaningful HTTP responses.
 * A rejected command is a business outcome, not a server error.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AuctionDomainException.class)
    public ProblemDetail handleDomainRule(AuctionDomainException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        problem.setTitle("Business rule violated");
        problem.setType(URI.create("https://api.auction.local/problems/business-rule"));
        return problem;
    }

    @ExceptionHandler(AuctionNotFoundException.class)
    public ProblemDetail handleNotFound(AuctionNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Resource not found");
        return problem;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentUpdate(OptimisticLockingFailureException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The auction was modified concurrently. Reload and retry.");
        problem.setTitle("Concurrent modification");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }
}