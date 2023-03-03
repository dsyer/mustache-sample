package com.example;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.Application.Menu;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.Compiler;
import com.samskivert.mustache.Template.Fragment;

/**
 * @author Dave Syer
 *
 */
@SpringBootApplication
public class DemoApplication {

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests()
				.requestMatchers("/login", "/error", "/webjars/**")
				.permitAll().requestMatchers("/**").authenticated().and()
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

@ControllerAdvice
class LayoutAdvice {

	private static Pattern NAME = Pattern.compile(".*name=\\\"([a-zA-Z0-9]*)\\\".*");

	private static Pattern TYPE = Pattern.compile(".*type=\\\"([a-zA-Z0-9]*)\\\".*");

	private final Mustache.Compiler compiler;

	private Application application;

	public LayoutAdvice(Compiler compiler, Application application) {
		this.compiler = compiler;
		this.application = application;
	}

	@ModelAttribute("menus")
	public Iterable<Menu> menus(@ModelAttribute Layout layout) {
		for (Menu menu : application.getMenus()) {
			menu.setActive(false);
		}
		return application.getMenus();
	}

	@ModelAttribute("menu")
	public Mustache.Lambda menu(@ModelAttribute Layout layout) {
		return (frag, out) -> {
			Menu menu = application.getMenu(frag.execute());
			menu.setActive(true);
			layout.title = menu.getTitle();
		};
	}

	@ModelAttribute("inputField")
	public Mustache.Lambda inputField(Map<String, Object> model) {
		return (frag, out) -> {
			String body = frag.execute();
			String label = body.substring(0, body.indexOf("<") - 1).trim();
			Form form = (Form) frag.context();
			String target = form.getName();
			String name = match(NAME, body, "unknown");
			String type = match(TYPE, body, "text");
			BindingResult status = (BindingResult) model
					.get("org.springframework.validation.BindingResult." + target);
			InputField field = new InputField(label, name, type, status);
			compiler.compile("{{>inputField}}").execute(field, out);
		};
	}

	private String match(Pattern pattern, String body, String fallback) {
		Matcher matcher = pattern.matcher(body);
		return matcher.matches() ? matcher.group(1) : fallback;
	}

	@ModelAttribute("form")
	public Map<String, Form> form(Map<String, Object> model) {
		return new LinkedHashMap<String, Form>() {
			@Override
			public boolean containsKey(Object key) {
				if (!super.containsKey(key)) {
					put((String) key, new Form((String) key, model.get(key)));
				}
				return super.containsKey(key);
			}

			@Override
			public Form get(Object key) {
				if (!super.containsKey(key)) {
					put((String) key, new Form((String) key, model.get(key)));
				}
				return super.get(key);
			}

		};
	}

	@ModelAttribute("layout")
	public Mustache.Lambda layout(Map<String, Object> model) {
		return new Layout(compiler);
	}

}

class InputField {

	String label;

	String name;

	boolean date;

	boolean valid;

	String value;

	List<String> errors = Collections.emptyList();

	public InputField(String label, String name, String type, BindingResult status) {
		this.label = label;
		this.name = name;
		if (status != null) {
			valid = !status.hasFieldErrors(name);
			errors = status.getFieldErrors(name).stream()
					.map(error -> error.getDefaultMessage()).collect(Collectors.toList());
			value = status.getFieldValue(name) == null ? ""
					: status.getFieldValue(name).toString();
		}
		this.date = "date".equals(type);
	}

}

class Form implements Mustache.Lambda {

	private Object target;

	private String name;

	public Form(String name, Object target) {
		this.name = name;
		this.target = target;
	}

	@Override
	public void execute(Fragment frag, Writer out) throws IOException {
		frag.execute(this, out);
	}

	public Object getTarget() {
		return target;
	}

	public String getName() {
		return name;
	}

}

class Layout implements Mustache.Lambda {

	String title = "Demo Application";

	String body;

	private Compiler compiler;

	public Layout(Compiler compiler) {
		this.compiler = compiler;
	}

	@Override
	public void execute(Fragment frag, Writer out) throws IOException {
		body = frag.execute();
		compiler.compile("{{>layout}}").execute(frag.context(), out);
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

	@GetMapping
	public String home(@ModelAttribute Foo foo) {
		return "index";
	}

	@PostMapping
	public String post(@ModelAttribute Foo foo, Map<String, Object> model) {
		model.put("value", foo.getValue());
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
