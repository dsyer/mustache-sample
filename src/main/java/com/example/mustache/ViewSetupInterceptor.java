package com.example.mustache;


import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.jstach.opt.spring.webmvc.JStachioModelView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ViewSetupInterceptor implements HandlerInterceptor, WebMvcConfigurer {

	private final List<PageConfigurer> configurers = new ArrayList<>();

	private final ApplicationContext context;

	public ViewSetupInterceptor(ObjectProvider<PageConfigurer> configurers, ApplicationContext context) {
		for (String name : context.getBeanNamesForType(PageConfigurer.class)) {
			this.configurers.add((PageConfigurer) context.getBean(name));
		}
		this.context = context;
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) throws Exception {
		JStachioModelView view = findView(modelAndView);
		if (view != null) {
			Object page = view.model();
			for (PageConfigurer configurer : configurers) {
				configurer.configure(page, modelAndView.getModel(), request);
			}
		}
	}

	private JStachioModelView findView(ModelAndView modelAndView) {
		if (modelAndView != null) {
			if (modelAndView.getView() instanceof JStachioModelView) {
				return (JStachioModelView) modelAndView.getView();
			}
			if (this.context.getBean(modelAndView.getViewName()) instanceof JStachioModelView) {
				return (JStachioModelView) this.context.getBean(modelAndView.getViewName());
			}
		}
		return null;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this);
	}

}
