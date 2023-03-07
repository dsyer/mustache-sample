package com.example.webflux;

import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.server.ServerWebExchange;

public interface HandlerResultInterceptor {
	void postHandle(ServerWebExchange request, HandlerResult result);
}
