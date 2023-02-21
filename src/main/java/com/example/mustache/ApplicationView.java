package com.example.mustache;

import java.util.Map;

import org.springframework.web.servlet.View;

import io.jstach.jstachio.JStachio;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ApplicationView implements View {

	private final Page page;

	public ApplicationView(Page page) {
		this.page = page;
	}

	@Override
	public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response)
			throws Exception {
		response.setContentType("text/html");
		response.getWriter().print(JStachio.render(page));
	}

	public Page getPage() {
		return page;
	}
}