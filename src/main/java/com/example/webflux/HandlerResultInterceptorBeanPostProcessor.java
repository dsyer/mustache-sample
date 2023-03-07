package com.example.webflux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.reactive.HandlerResultHandler;
import org.springframework.web.reactive.result.view.AbstractView;
import org.springframework.web.reactive.result.view.RequestContext;
import org.springframework.web.reactive.result.view.RequestDataValueProcessor;
import org.springframework.web.reactive.result.view.ViewResolutionResultHandler;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

// TODO: make it an autoconfiguration
@Component
public class HandlerResultInterceptorBeanPostProcessor implements BeanPostProcessor {

	private final ApplicationContext context;

	public HandlerResultInterceptorBeanPostProcessor(ApplicationContext context) {
		this.context = context;
	}

	/**
	 * Look for a {@link ViewResolutionResultHandler} and replace it with a wrapper
	 * that applies {@link HandlerResultInterceptor}s in the current context.
	 * 
	 * @param bean     the bean that is being created
	 * @param beanName the name of the bean
	 * @return Object a bean wrapped with {@link ViewSetupResultHandler} if needed
	 * @throws BeansException
	 */
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof ViewResolutionResultHandler handler) {
			return new ViewSetupResultHandler(this.context, handler);
		}
		return bean;
	}

	class ViewSetupResultHandler implements HandlerResultHandler, Ordered {

		private final ViewResolutionResultHandler delegate;

		private final List<HandlerResultInterceptor> configurers = new ArrayList<>();

		private final ApplicationContext context;

		ViewSetupResultHandler(ApplicationContext context, ViewResolutionResultHandler handler) {
			for (String name : context.getBeanNamesForType(HandlerResultInterceptor.class)) {
				this.configurers.add((HandlerResultInterceptor) context.getBean(name));
			}
			this.delegate = handler;
			this.context = context;
		}

		@Override
		public int getOrder() {
			return this.delegate.getOrder();
		}

		@Override
		public Mono<Void> handleResult(ServerWebExchange exchange, HandlerResult result) {
			for (HandlerResultInterceptor configurer : configurers) {
				configurer.postHandle(exchange, result);
			}
			return this.delegate.handleResult(exchange, result);
		}

		protected RequestContext createRequestContext(ServerWebExchange exchange, Map<String, Object> model) {
			return new RequestContext(exchange, model, this.context, getRequestDataValueProcessor());
		}

		@Nullable
		protected RequestDataValueProcessor getRequestDataValueProcessor() {
			ApplicationContext context = this.context;
			if (context != null && context.containsBean(AbstractView.REQUEST_DATA_VALUE_PROCESSOR_BEAN_NAME)) {
				return context.getBean(AbstractView.REQUEST_DATA_VALUE_PROCESSOR_BEAN_NAME,
						RequestDataValueProcessor.class);
			}
			return null;
		}

		@Override
		public boolean supports(HandlerResult result) {
			return this.delegate.supports(result);
		}

	}
}
