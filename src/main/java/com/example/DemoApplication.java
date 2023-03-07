package com.example;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.template.TemplateAvailabilityProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.reactive.result.view.BindStatus;
import org.springframework.web.reactive.result.view.RequestContext;
import org.springframework.web.reactive.result.view.View;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;

import com.example.Application.Menu;
import com.example.mustache.JStachioModelView;
import com.example.mustache.JStachioModelViewConfigurer;
import com.example.mustache.ViewSetupBeanPostProcessor;

import io.jstach.jstache.JStache;
import io.jstach.jstache.JStacheFlags;
import io.jstach.jstache.JStacheFlags.Flag;
import io.jstach.jstache.JStacheFormatterTypes;
import io.jstach.jstache.JStacheLambda;
import io.jstach.jstache.JStacheLambda.Raw;
import io.jstach.jstache.JStachePath;
import io.jstach.jstachio.JStachio;
import reactor.core.publisher.Mono;

/**
 * @author Dave Syer
 *
 */
@SpringBootApplication
@JStachePath(prefix = "templates/", suffix = ".mustache")
@JStacheFlags(flags = Flag.DEBUG)
@JStacheFormatterTypes(types = LocalDate.class)
public class DemoApplication {

	@Bean
	public SecurityWebFilterChain filterChain(ServerHttpSecurity http) throws Exception {
		http.authorizeExchange(exchange -> exchange
				.pathMatchers("/login", "/error", "/webjars/**")
				.permitAll().pathMatchers("/**").authenticated().and()
				.formLogin(login -> login.loginPage("/login")));
		return http.build();
	}

	@Bean
	@SuppressWarnings("deprecation")
	public MapReactiveUserDetailsService inMemoryUserDetailsManager() {
		return new MapReactiveUserDetailsService(
				User.withDefaultPasswordEncoder().username("foo").password("bar")
						.roles(new String[] { "USER" }).build());
	}

	@Bean
	public ViewSetupBeanPostProcessor viewSetupBeanPostProcessor(ApplicationContext context) {
		return new ViewSetupBeanPostProcessor(context);
	}

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}

@Component
@ConfigurationProperties("app")
class Application {

	private static Log logger = LogFactory.getLog(Application.class);

	private static Menu DEFAULT_MENU;

	static {
		DEFAULT_MENU = new Menu();
		DEFAULT_MENU.setName("Home");
		DEFAULT_MENU.setPath("/");
		DEFAULT_MENU.setActive(true);
	}

	private List<Menu> menus = new ArrayList<>();

	public List<Menu> getMenus() {
		return menus;
	}

	public static class Menu {

		private String name;

		private String path;

		private String title;

		public boolean active;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public String getPath() {
			return path;
		}

		public void setPath(String path) {
			this.path = path;
		}

		public boolean isActive() {
			return active;
		}

		public void setActive(boolean active) {
			this.active = active;
		}

	}

	public Menu getMenu(String name) {
		for (Menu menu : menus) {
			if (menu.getName().equalsIgnoreCase(name)) {
				return menu;
			}
		}
		logger.error("No menu found for " + name + " (" + menus + ")");
		return DEFAULT_MENU;
	}

}

@JStache(path = "inputField")
class InputField {

	public String label;

	public String name;

	public boolean date;

	public boolean valid = true;

	public String value;

	public String[] errors = new String[0];

	public InputField(String label, String name, String value, String type, BindStatus status) {
		this.label = label;
		this.name = name;
		this.value = value == null ? "" : value;
		if (status != null) {
			valid = !status.isError();
			errors = status.getErrorMessages();
			value = status.getValue() == null ? ""
					: status.getValue().toString();
		}
		this.date = "date".equals(type);
	}

}

record Form(String name, Object target, CsrfToken _csrf) {
}

@JStache(path = "index")
class IndexPage extends BasePage {

	private final Foo foo;

	public IndexPage(Foo foo) {
		activate("home");
		this.foo = foo;
	}

	public Foo foo() {
		return foo;
	}

	public Form form() {
		return new Form("foo", foo, _csrf());
	}

	public InputField field() {
		return new InputField("Value", "value", foo.getValue(), "text", status("foo"));
	}

	public InputField date() {
		return new InputField("Date", "date", foo.getDate().toString(), "date", status("foo", "date"));
	}

}

class BasePage {
	private Application application;
	private CsrfToken _csrf;
	private String active = "home";
	private RequestContext context;

	@Autowired
	public void setApplication(Application application) {
		this.application = application;
	}

	public void setCsrfToken(CsrfToken _csrf) {
		this._csrf = _csrf;
	}

	public void activate(String name) {
		this.active = name;
	}

	public List<Menu> getMenus() {
		Menu menu = application.getMenu(active);
		if (menu != null) {
			application.getMenus().forEach(m -> m.setActive(false));
			menu.setActive(true);
		}
		return application.getMenus();
	}

	public CsrfToken _csrf() {
		return this._csrf;
	}

	public BindStatus status(String name) {
		return this.context.getBindStatus(name + ".*");
	}

	public BindStatus status(String name, String field) {
		return this.context.getBindStatus(name + "." + field);
	}

	public void setRequestContext(RequestContext context) {
		this.context = context;
	}

	@JStacheLambda
	@Raw
	public String render(Object field) {
		return JStachio.render(field);
	}

}

class ErrorPageView implements JStachioModelView {

	private ErrorPage page = new ErrorPage();

	private final LayoutHelper helper;

	public ErrorPageView(LayoutHelper helper) {
		this.helper = helper;
	}

	@Override
	public Object model() {
		return this.page;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Mono<Void> render(Map<String, ?> model, MediaType contentType, ServerWebExchange exchange) {
		HashMap<String, Object> map = new HashMap<>((Map<String, Object>) model);
		helper.enhance(page, exchange, map);
		page.setMessage((String) model.get("error"));
		return JStachioModelView.super.render(map, contentType, exchange);
	}

}

@JStache(path = "error")
class ErrorPage extends BasePage {
	private String message = "Oops!";

	public ErrorPage() {
		activate("home");
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}

@JStache(path = "login")
class LoginPage extends BasePage {
	public LoginPage() {
		activate("login");
	}
}

class Foo {

	private String value = "";

	private LocalDate date = LocalDate.now();

	public Foo() {
	}

	public Foo(String value) {
		this.value = value;
	}

	public String getValue() {
		return this.value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String date() {
		return date.toString();
	}

	public LocalDate getDate() {
		return date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	@Override
	public String toString() {
		return "Foo [value=" + this.value + "]";
	}

}

@Controller
@RequestMapping("/")
class HomeController {

	@GetMapping
	public View home(@ModelAttribute Foo target) {
		return JStachioModelView.of(new IndexPage(target));
	}

	@PostMapping
	public View post(@ModelAttribute Foo foo) {
		return JStachioModelView.of(new IndexPage(foo));
	}

}

@Controller
@RequestMapping("/login")
class LoginController {

	@GetMapping
	public View form() {
		return JStachioModelView.of(new LoginPage());
	}

}

@Component
class ApplicationPageConfigurer implements JStachioModelViewConfigurer {

	private final LayoutHelper helper;

	public ApplicationPageConfigurer(LayoutHelper helper) {
		this.helper = helper;
	}

	@Override
	public void configure(Object page, Map<String, Object> model, ServerWebExchange request) {
		if (page instanceof BasePage base) {
			helper.enhance(base, request, model);
		}
	}

}

@Component
class LayoutHelper {
	private final Application application;

	public LayoutHelper(Application application) {
		this.application = application;
	}

	public void enhance(BasePage base, ServerWebExchange exchange, Map<String, Object> model) {
		if (exchange.getAttribute(CsrfToken.class.getName()) != null) {
			@SuppressWarnings("unchecked")
			Mono<CsrfToken> token = (Mono<CsrfToken>) exchange.getAttribute(CsrfToken.class.getName());
			model.put("_csrf", token.doOnSuccess(value -> base.setCsrfToken(value)));
		}
		base.setRequestContext(new RequestContext(exchange, model, exchange.getApplicationContext()));
		base.setApplication(application);
	}

}

class DemoTemplateAvailabilityProvider implements TemplateAvailabilityProvider {

	@Override
	public boolean isTemplateAvailable(String view, Environment environment, ClassLoader classLoader,
			ResourceLoader resourceLoader) {
		if ("error/error".equals(view)) {
			return true;
		};
		return false;
	}
	
}

@Component
class ErrorPageViewResolver implements ViewResolver {

	private final LayoutHelper context;

	public ErrorPageViewResolver(LayoutHelper context) {
		this.context = context;
	}

	@Override
	public Mono<View> resolveViewName(String viewName, Locale locale) {
		if (!"error/error".equals(viewName)) {
			return Mono.empty();
		}
		return Mono.just(new ErrorPageView(context));
	}

}