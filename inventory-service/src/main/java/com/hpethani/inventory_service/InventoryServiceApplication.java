package com.hpethani.inventory_service;

import com.hpethani.inventory_service.entity.Inventory;
import com.hpethani.inventory_service.repository.InventoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication
@EnableMethodSecurity  // enables @PreAuthorize, @PostAuthorize on controller methods
public class InventoryServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(InventoryServiceApplication.class, args);
	}

//	@Bean
//	public CommandLineRunner init(InventoryRepository inventoryRepository) {
//		return args -> {
//			Inventory  inventory1 = new Inventory();
//			inventory1.setSkuCode("red_shirt");
//			inventory1.setQuantity(5);
//
//			Inventory  inventory2 = new Inventory();
//			inventory2.setSkuCode("green_shirt");
//			inventory2.setQuantity(3);
//
//			inventoryRepository.save(inventory1);
//			inventoryRepository.save(inventory2);
//		};
//	}
}
