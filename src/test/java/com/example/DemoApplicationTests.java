package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class DemoApplicationTests {

	@Autowired
	private RestTemplateBuilder rest;

	@LocalServerPort
	private int port;

	@Test
	public void contextLoads() {
		ResponseEntity<String> response = rest.build().getForEntity("http://localhost:" + port, String.class);
		assertThat(response.getBody()).contains("<label for=\"username\">Username:</label>");
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

}
