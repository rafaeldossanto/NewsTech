package com.web.newsTach;

import org.springframework.boot.SpringApplication;

public class TestNewsTachApplication {

	public static void main(String[] args) {
		SpringApplication.from(NewsTachApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
