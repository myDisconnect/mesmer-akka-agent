package io.scalac.agent.akka.http;

import akka.http.scaladsl.HttpExt;
import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.stream.scaladsl.Flow;
import net.bytebuddy.asm.Advice;

public class HttpExtAdvice {
    @Advice.OnMethodEnter
    public static void bindAndHandle(@Advice.Argument(value = 0, readOnly = false) Flow<HttpRequest, HttpResponse, Object> handler, @Advice.This Object self) {
        handler = HttpInstrumentation.bindAndHandleImpl(handler, (HttpExt) self);
    }
}