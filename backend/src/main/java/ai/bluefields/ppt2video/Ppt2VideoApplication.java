package ai.bluefields.ppt2video;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the PowerPoint to Video POC. This Spring Boot application provides
 * REST APIs for converting PowerPoint presentations into video stories using AI-powered content
 * generation and text-to-speech synthesis.
 */
@SpringBootApplication
public class Ppt2VideoApplication {

  /**
   * Application entry point.
   *
   * @param args command line arguments passed to the application
   */
  public static void main(String[] args) {
    SpringApplication.run(Ppt2VideoApplication.class, args);
  }
}
