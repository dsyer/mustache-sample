package com.example;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.support.BindStatus;
import org.springframework.web.servlet.support.RequestContext;

import com.example.Application.Menu;
import com.example.mustache.PageConfigurer;

import io.jstach.jstache.JStache;
import io.jstach.jstache.JStacheLambda;
import io.jstach.jstache.JStacheLambda.Raw;
import io.jstach.jstachio.JStachio;
import io.jstach.opt.spring.webmvc.JStachioModelView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Dave Syer
 *
 */
@SpringBootApplication
public class DemoApplication {

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http, SecurityContextRepository repository)
			throws Exception {
		http.authorizeHttpRequests().requestMatchers("/login", "/error", "/webjars/**")
				.permitAll().requestMatchers("/**").authenticated().and().exceptionHandling()
				.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));
		http.addFilterBefore(new SecurityContextHolderFilter(repository), LogoutFilter.class);
		return http.build();
	}

	@Bean
	public HttpSessionSecurityContextRepository sessionRepository() {
		return new HttpSessionSecurityContextRepository();
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

@Component("error")
@RequestScope
class ErrorPageView implements JStachioModelView {

	private ErrorPage page = new ErrorPage();

	@Override
	public Object model() {
		return this.page;
	}

	@Override
	public String getContentType() {
		return contentType();
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

	private SavedRequestAwareAuthenticationSuccessHandler handler = new SavedRequestAwareAuthenticationSuccessHandler();

	private final SecurityContextRepository repository;

	public LoginController(SecurityContextRepository repository) {
		this.repository = repository;
	}

	@GetMapping
	public View form() {
		return JStachioModelView.of(new LoginPage());
	}

	@PostMapping
	public void authenticate(@RequestParam Map<String, String> map,
			HttpServletRequest request, HttpServletResponse response) throws Exception {
		Authentication result = new UsernamePasswordAuthenticationToken(
				map.get("username"), "N/A",
				AuthorityUtils.commaSeparatedStringToAuthorityList("ROLE_USER"));
		SecurityContextHolder.getContext().setAuthentication(result);
		repository.saveContext(SecurityContextHolder.getContext(), request, response);
		handler.onAuthenticationSuccess(request, response, result);
	}

}

@Component
class ApplicationPageConfigurer implements PageConfigurer {

	private final Application application;

	public ApplicationPageConfigurer(Application application) {
		this.application = application;
	}
	@Override
	public void configure(Object page, Map<String, ?> model, HttpServletRequest request) {
		if (page instanceof BasePage) {
			BasePage base = (BasePage)page;
			base.setCsrfToken((CsrfToken) request.getAttribute("_csrf"));
			Map<String, Object> map = new HashMap<>(model);
			base.setRequestContext(new RequestContext(request, map));
			base.setApplication(application);
		}
		if (page instanceof ErrorPage) {
			((ErrorPage) page).setMessage((String) model.get("error"));
		}
	}

}