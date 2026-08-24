package com.vivo4redes.syscor;
import org.springframework.boot.SpringApplication;

public class TestSyscorApplication {

	public static void main(String[] args) {
		SpringApplication.from(SyscorApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
