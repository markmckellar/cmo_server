package com.mckellar.pimaster.pimaster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@SpringBootApplication
@RestController
public class PimasterApplication {
	
	
    private static final Logger Log = LoggerFactory.getLogger(PimasterApplication.class);



	public static void main(String[] args) {
		SpringApplication.run(PimasterApplication.class, args);
	}
	
    @RequestMapping("/")
    String home() {
    	Log.info("I am at home");
        return "Hello, World!";
    }
    
    @RequestMapping("/hello2")
    String heeeyyy() {
    	Log.info("I am at heeeyyy");

        return "Heeeeeeeeeeeeeeeey";
    }
    
    @RequestMapping("/echotheid/{id}")
    String EchoId(@PathVariable Long id)  {
    	Log.info("I am at EchoId:"+id);

        return (id*2)+"";
    }

}
