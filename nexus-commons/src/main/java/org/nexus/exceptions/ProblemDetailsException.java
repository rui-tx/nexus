package org.nexus.exceptions;

import org.nexus.interfaces.ProblemDetails;

public class ProblemDetailsException extends RuntimeException {

  private final ProblemDetails problemDetails;

  public ProblemDetailsException(ProblemDetails problemDetails) {
    super(problemDetails instanceof ProblemDetails.Single single
        ? single.detail()
        : "Multiple problems occurred");
    this.problemDetails = problemDetails;
  }

  public ProblemDetails getProblemDetails() {
    return problemDetails;
  }
}
