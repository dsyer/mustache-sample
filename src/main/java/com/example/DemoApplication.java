package com.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.Application.Menu;

import io.jstach.jstache.JStache;
import io.jstach.jstache.JStacheFlags;
import io.jstach.jstache.JStacheFlags.Flag;
import io.jstach.jstache.JStachePartial;
import io.jstach.jstache.JStachePartials;
import io.jstach.jstachio.JStachio;

/**
 * @author Dave Syer
 *
 */
@SpringBootApplication
@JStacheFlags(flags = Flag.DEBUG)
public class DemoApplication {

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.authorizeRequests().antMatchers("/login", "/error", "/webjars/**")
				.permitAll().antMatchers("/**").authenticated().and().exceptionHandling()
				.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));
		return http.build();
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
		return menus.get(0);
	}

}

class InputField {

	public String label;

	public String name;

	public boolean date;

	public boolean valid = true;

	public String value;

	public List<String> errors = Collections.emptyList();

	public InputField(String label, String name, String value, String type, BindingResult status) {
		this.label = label;
		this.name = name;
		this.value = value == null ? "" : value;
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

record Form(String name, Object target, CsrfToken _csrf) {
}

@JStache(path = "templates/index.mustache")
@JStachePartials({ @JStachePartial(name = "layout", path = "templates/layout.mustache"),
		@JStachePartial(name = "inputField", path = "templates/inputField.mustache") })
record IndexPage(Application application, Foo foo, BindingResult status, CsrfToken _csrf) {

	public IndexPage(Application application, BindingResult status, CsrfToken _csrf) {
		this(application, new Foo(), status, _csrf);
	}

	public Form form() {
		return new Form("foo", foo, _csrf);
	}

	public InputField field() {
		return new InputField("Value", "value", foo.getValue(), "text", status);
	}

	public List<Menu> getMenus() {
		return application.getMenus();
	}

}

@JStache(path = "templates/login.mustache")
@JStachePartials(@JStachePartial(name = "layout", path = "templates/layout.mustache"))
record LoginPage(Application application, CsrfToken _csrf) {
	public List<Menu> getMenus() {
		return application.getMenus();
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

@RestController
@RequestMapping("/")
class HomeController {

	private final Application application;

	public HomeController(Application application) {
		this.application = application;
	}

	@GetMapping
	public String home(@ModelAttribute Foo target, @RequestAttribute("_csrf") CsrfToken _csrf) {
		return JStachio.render(new IndexPage(application, target, null, _csrf));
	}

	@PostMapping
	public String post(@ModelAttribute Foo foo, @RequestAttribute("_csrf") CsrfToken _csrf, BindingResult status) {
		return JStachio.render(new IndexPage(application, foo, status, _csrf));
	}

}

@RestController
@RequestMapping("/login")
class LoginController {

	private SavedRequestAwareAuthenticationSuccessHandler handler = new SavedRequestAwareAuthenticationSuccessHandler();

	private final Application application;

	public LoginController(Application application) {
		this.application = application;
	}

	@GetMapping
	public String form(@RequestAttribute("_csrf") CsrfToken _csrf) {
		return JStachio.render(new LoginPage(application, _csrf));
	}

	@PostMapping
	public void authenticate(@RequestParam Map<String, String> map,
			HttpServletRequest request, HttpServletResponse response) throws Exception {
		Authentication result = new UsernamePasswordAuthenticationToken(
				map.get("username"), "N/A",
				AuthorityUtils.commaSeparatedStringToAuthorityList("ROLE_USER"));
		SecurityContextHolder.getContext().setAuthentication(result);
		handler.onAuthenticationSuccess(request, response, result);
	}

}
