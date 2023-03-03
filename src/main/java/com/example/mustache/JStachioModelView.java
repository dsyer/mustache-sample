package com.example.mustache;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.result.view.View;
import org.springframework.web.server.ServerWebExchange;

import io.jstach.jstache.JStache;
import io.jstach.jstachio.JStachio;
import reactor.core.publisher.Mono;

public interface JStachioModelView extends View {

	@Override
	default Mono<Void> render(Map<String, ?> model, MediaType contentType, ServerWebExchange exchange) {
		if (contentType != null) {
			exchange.getResponse().getHeaders().setContentType(contentType);
		}
		return exchange.getResponse().writeWith(Mono.fromCallable(() -> {
			try {
				byte[] bytes = String.valueOf(JStachio.render(model())).getBytes(StandardCharsets.UTF_8);
				return exchange.getResponse().bufferFactory().wrap(bytes); // just
																			// wrapping,
																			// no
																			// allocation
			}
			catch (Exception ex) {
				throw new IllegalStateException("Failed to render script template", ex);
			}
		}));
	}

	@Override
	default List<MediaType> getSupportedMediaTypes() {
		return List.of(MediaType.TEXT_HTML);
	}

	/**
	 * The model to be rendered by {@link #jstachio()}.
	 * @return model defaulting to <code>this</code> instance.
	 */
	default Object model() {
		return this;
	}

	/**
	 * Creates a spring view from a model
	 * @param model an instance of a class annotated with {@link JStache}.
	 * @return view ready for rendering
	 */
	public static JStachioModelView of(Object model) {
		return new JStachioModelView() {
			@Override
			public Object model() {
				return model;
			}
		};
	}

}
