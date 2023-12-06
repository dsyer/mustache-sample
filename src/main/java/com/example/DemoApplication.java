package com.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.support.BindStatus;
import org.springframework.web.servlet.support.RequestContext;

import com.example.Application.Menu;

import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Dave Syer
 *
 */
@SpringBootApplication
public class DemoApplication {

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(security -> security
				.requestMatchers("/login", "/error", "/webjars/**")
				.permitAll().requestMatchers("/**").authenticated())
				.formLogin(login -> login.loginPage("/login"));
		return http.build();
	}

	@Bean
	public InMemoryUserDetailsManager inMemoryUserDetailsManager() {
		return new InMemoryUserDetailsManager(
				User.withUsername("foo").password("{noop}bar")
						.roles(new String[] { "USER" }).build());
	}

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}

@Component
@ConfigurationProperties("app")
class Application {

	private List<Menu> menus = new ArrayList<>();

	public List<Menu> getMenus() {
		return menus;
	}

	public static class Menu {

		private String name;

		private String path;

		private String title;

		private boolean active;

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
		return menus.get(0);
	}

}

@Component
class LayoutAdvice implements HandlerInterceptor, WebMvcConfigurer {

	private Application application;

	public LayoutAdvice(Application application) {
		this.application = application;
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			@Nullable ModelAndView modelAndView) throws Exception {
		for (Menu menu : application.getMenus()) {
			menu.setActive(false);
		}
		if (modelAndView != null) {
			Map<String, Object> map = modelAndView.getModel();
			map.put("menus", application.getMenus());
			if (map.containsKey("foo")) {
				application.getMenu("home").setActive(true);
			}
			else {
				application.getMenu("login").setActive(true);
			}
			RequestContext context = new RequestContext(request, map);
			for (String key : new HashSet<>(map.keySet())) {
				if (key.startsWith("org.springframework.validation.BindingResult.")) {
					String name = key.substring(key.lastIndexOf(".") + 1);
					modelAndView.addObject("errors", context.getBindStatus(name + ".*").getErrorMessages());
				}
				if (map.get(key) instanceof Form field) {
					field.setContext(context);
				}
			}
		}
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this);
	}

}

class InputField {

	String label;

	String name;

	boolean date;

	boolean valid;

	String value;

	List<String> errors = Collections.emptyList();

	public InputField(String label, String name, String type) {
		this.label = label;
		this.name = name;
		this.date = "date".equals(type);
	}

	public void setStatus(BindStatus status) {
		if (status != null) {
			valid = !status.isError();
			errors = Arrays.asList(status.getErrorMessages());
			value = status.getValue() == null ? "" : status.getValue().toString();
		}
	}

}

class Form {

	private final InputField target = new InputField("Value", "value", "text");
	private final Foo foo;

	public Form(Foo foo) {
		this.foo = foo;
	}

	public Foo getFoo() {
		return foo;
	}

	public InputField getValue() {
		return target;
	}

	public void setContext(RequestContext context) {
		this.target.setStatus(context.getBindStatus("foo.value"));
	}

}

class Foo {

	private String value = "";

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

	@Override
	public String toString() {
		return "Foo [value=" + this.value + "]";
	}

}

@Controller
@RequestMapping("/")
class HomeController {

	@ModelAttribute
	public Form form(@ModelAttribute Foo foo) {
		return new Form(foo);
	}

	@GetMapping
	public String home() {
		return "index";
	}

	@PostMapping
	public String post() {
		return "index";
	}

}

@Controller
@RequestMapping("/login")
class LoginController {

	@GetMapping
	public String form() {
		return "login";
	}

}
