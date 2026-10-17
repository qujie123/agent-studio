package com.agent.agentplatform.exception;

import java.time.Instant;

public record ApiError(int status, String message, Instant timestamp) {
}
