package io.scalac.mesmer.agent.akka.http;

import akka.http.scaladsl.HttpExt;
import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.stream.scaladsl.Flow;
import net.bytebuddy.asm.Advice;

public class HttpExtConnectionsAdvice {

    @Advice.OnMethodEnter
    public static void bindAndHandle(@Advice.Argument(value = 0, readOnly = false) Flow<HttpRequest, HttpResponse, Object> handler,
                                     @Advice.Argument(1) String _interface,
                                     @Advice.Argument(2) Integer port,
                                     @Advice.This Object self) {
        handler = HttpInstrumentation.bindAndHandleConnectionsImpl(handler, _interface, port, (HttpExt) self);
    }
}
