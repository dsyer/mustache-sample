package com.example.mustache;

import org.springframework.web.reactive.result.view.RequestContext;

public interface JStachioModelViewConfigurer {
	void configure(Object page, RequestContext request);
}
