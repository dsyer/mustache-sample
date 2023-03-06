package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class DemoApplicationTests {

	@Autowired
	private TestRestTemplate rest;

	@LocalServerPort
	private int port;

	private String cookie;

	@Test
	public void contextLoads() {
		ResponseEntity<String> response = rest.getForEntity("http://localhost:" + port, String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
	}

	@Test
	public void loginSuccessful() {
		ResponseEntity<String> response = login();
		assertThat(response.getBody()).isNull();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
	}

	@Test
	public void homePage() {
		login();
		RequestEntity<?> request = RequestEntity.get("http://localhost:" + port + "/")
				.header("cookie", this.cookie).build();
		ResponseEntity<String> response = rest
				.exchange(request, String.class);
		assertThat(response.getBody()).contains("<form");
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	public void errorPage() {
		login();
		RequestEntity<?> request = RequestEntity.get("http://localhost:" + port + "/no-such-path")
				.header("cookie", this.cookie).header(HttpHeaders.ACCEPT, "text/html").build();
		ResponseEntity<String> response = rest.exchange(request, String.class);
		assertThat(response.getBody()).contains("nav-tabs");
		assertThat(response.getBody()).contains("Not Found");
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	private HttpHeaders cookie(HttpHeaders headers) {
		HttpHeaders result = new HttpHeaders();
		String cookie = headers.get("set-cookie").get(0).replaceAll(";.*", "");
		this.cookie = cookie;
		result.add("cookie", cookie);
		return result;
	}

	private HttpEntity<MultiValueMap<String, String>> request(String path) {
		ResponseEntity<String> login = rest
				.getForEntity("http://localhost:" + port + path, String.class);
		String csrf = login.getBody().split("name=\"_csrf\"")[1].replaceAll(".*value=\"", "").replaceAll("(?s)\".*",
				"");
		HttpHeaders headers = cookie(login.getHeaders());
		MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		map.add("_csrf", csrf);
		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
		return request;
	}

	private ResponseEntity<String> login() {
		HttpEntity<MultiValueMap<String, String>> request = request("/login");
		MultiValueMap<String, String> map = request.getBody();
		map.add("username", "foo");
		map.add("password", "bar");
		ResponseEntity<String> response = rest
				.postForEntity("http://localhost:" + port + "/login",
						request, String.class);
		cookie(response.getHeaders());
		return response;
	}

}
