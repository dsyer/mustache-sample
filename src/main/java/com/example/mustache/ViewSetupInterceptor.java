package com.example.mustache;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ViewSetupInterceptor implements HandlerInterceptor, WebMvcConfigurer {

	private final ObjectProvider<PageConfigurer> configurers;

	public ViewSetupInterceptor(ObjectProvider<PageConfigurer> configurers) {
		this.configurers = configurers;
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) throws Exception {
		if (modelAndView != null && modelAndView.getView() instanceof ApplicationView) {
			Page page = ((ApplicationView) modelAndView.getView()).getPage();
			for (PageConfigurer configurer: configurers) {
				configurer.configure(page, modelAndView.getModel(), request);
			}
		}
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this);
	}
}
