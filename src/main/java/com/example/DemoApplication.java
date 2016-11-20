package com.example;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@SpringBootApplication
public class DemoApplication extends WebSecurityConfigurerAdapter {

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http.authorizeRequests().antMatchers("/login", "/error").permitAll()
				.antMatchers("/**").authenticated().and().exceptionHandling()
				.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));
	}

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}
}

@Controller
@RequestMapping("/")
class HomeController {
	@GetMapping
	public String home() {
		return "index";
	}
}

@Controller
@RequestMapping("/login")
class LoginController {

	private SavedRequestAwareAuthenticationSuccessHandler handler = new SavedRequestAwareAuthenticationSuccessHandler();

	@GetMapping
	public String form() {
		return "login";
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
