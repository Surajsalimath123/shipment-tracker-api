package com.argus.shipmenttracker;

import org.springframework.boot.SpringApplication;

public class TestShipmentTrackerApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(ShipmentTrackerApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
