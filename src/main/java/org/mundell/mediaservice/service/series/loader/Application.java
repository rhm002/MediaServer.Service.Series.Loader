package org.mundell.mediaservice.service.series.loader;

import org.mundell.mediaservice.service.series.loader.services.SeriesLoaderService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import lombok.RequiredArgsConstructor;

@SpringBootApplication
@RequiredArgsConstructor
public class Application implements CommandLineRunner {

    private final SeriesLoaderService seriesLoaderService;

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(Application.class, args)));
    }

    @Override
    public void run(String... args) throws Exception {
        seriesLoaderService.process();
    }
}
