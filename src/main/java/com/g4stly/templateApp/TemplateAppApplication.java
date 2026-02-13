package com.g4stly.templateApp;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TemplateAppApplication {

	public static void main(String[] args) {
		// Set default timezone to Turkey (Istanbul)
		TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"));
		SpringApplication.run(TemplateAppApplication.class, args);
	}

}
