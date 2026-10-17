package com.agent.agentplatform.service;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

final class LimitedResponseBody implements HttpResponse.BodySubscriber<String> {
    private final HttpResponse.BodySubscriber<String> delegate = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
    private final int limit;
    private long received;
    private Flow.Subscription subscription;
    private boolean stopped;
    static HttpResponse.BodyHandler<String> handler(int limit) { return info -> new LimitedResponseBody(limit); }
    private LimitedResponseBody(int limit) { this.limit = limit; }
    @Override public CompletionStage<String> getBody() { return delegate.getBody(); }
    @Override public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
    @Override public void onNext(List<ByteBuffer> buffers) {
        if (stopped) { return; }
        for (var buffer : buffers) { received += buffer.remaining(); }
        if (received > limit) { stopped = true; subscription.cancel(); delegate.onError(new TooLarge()); }
        else { delegate.onNext(buffers); }
    }
    @Override public void onError(Throwable failure) { if (!stopped) { stopped = true; delegate.onError(failure); } }
    @Override public void onComplete() { if (!stopped) { stopped = true; delegate.onComplete(); } }
    static boolean exceeded(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof TooLarge) { return true; }
        }
        return false;
    }
    private static final class TooLarge extends IOException { TooLarge() { super("Response size limit exceeded"); } }
}
