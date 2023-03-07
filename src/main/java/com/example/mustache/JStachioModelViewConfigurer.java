package com.example.mustache;

import java.util.Map;

import org.springframework.web.server.ServerWebExchange;

public interface JStachioModelViewConfigurer {
	void configure(Object page, Map<String, Object> model, ServerWebExchange request);
}
