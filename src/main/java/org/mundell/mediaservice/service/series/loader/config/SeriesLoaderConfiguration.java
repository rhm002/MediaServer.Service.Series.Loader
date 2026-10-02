package org.mundell.mediaservice.service.series.loader.config;

import java.util.Arrays;
import java.util.List;

import org.mundell.dal.common.services.ConfigurationService;
import org.mundell.mediaservice.service.series.loader.constants.Constants;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;

@Configuration
@ComponentScan(basePackages = "org.mundell")
@RequiredArgsConstructor
public class SeriesLoaderConfiguration {

    private final ConfigurationService configurationService;

    /** Seeds the SERIES_LOCATION config row on first run. */
    public void ensureDefaults() {
        if (configurationService.get(Constants.SERIES_LOCATION) == null) {
            configurationService.add(Constants.SERIES_LOCATION, Constants.DEFAULT_SERIES_LOCATION, true);
        }
    }

    public List<String> getSeriesFolders() {
        return Arrays.stream(configurationService.get(Constants.SERIES_LOCATION).getValue().split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
