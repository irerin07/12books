package com.irene.twelvebooks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TwelvebooksApplication {

	public static void main(String[] args) {
		SpringApplication.run(TwelvebooksApplication.class, args);
	}

}
