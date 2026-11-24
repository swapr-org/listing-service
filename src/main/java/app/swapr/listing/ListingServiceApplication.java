package app.swapr.listing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
class ListingServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ListingServiceApplication.class, args);
  }
}
