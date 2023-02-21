package com.example.mustache;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

public interface PageConfigurer {

	void configure(Page page, Map<String, ?> model, HttpServletRequest request);

}
