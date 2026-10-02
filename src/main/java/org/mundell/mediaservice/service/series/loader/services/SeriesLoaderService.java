package org.mundell.mediaservice.service.series.loader.services;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.mundell.mediaserver.dal.repositories.series.SeriesEpisodeRepository;
import org.mundell.mediaserver.dal.repositories.series.SeriesRepository;
import org.mundell.mediaservice.service.series.loader.config.SeriesLoaderConfiguration;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Syncs the series library on disk into the database: marks everything as a deletion candidate, rescans every
 * configured root, then removes rows whose files have gone.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeriesLoaderService {

    private final SeriesLoaderConfiguration configuration;
    private final SeriesRepository seriesRepository;
    private final SeriesEpisodeRepository episodeRepository;
    private final SeriesLibraryScanner scanner;

    public void process() throws IOException {
        log.info("Series loading started: {}", Instant.now());

        configuration.ensureDefaults();
        List<String> folders = configuration.getSeriesFolders();

        // Verify at least one configured folder is accessible before touching the DB.
        List<String> accessible = folders.stream()
                .filter(p -> {
                    File dir = new File(p);
                    boolean ok = dir.exists() && dir.isDirectory();
                    if (!ok) log.error("Series folder not accessible: {}", p);
                    return ok;
                })
                .toList();

        if (accessible.isEmpty()) {
            throw new IOException("None of the configured series folders are accessible: " + folders
                    + " — aborting to prevent data loss");
        }

        // Mark ALL existing records as candidates for deletion (update=true).
        // Records found on disk are reset to update=false during scanning.
        episodeRepository.bulkUpdate(true);
        seriesRepository.bulkUpdate(true);

        boolean complete = accessible.size() == folders.size();
        int totalLoaded = 0;
        for (String location : accessible) {
            try {
                int loaded = scanner.scan(location);
                totalLoaded += loaded;
                log.info("Loaded {} episodes from: {}", loaded, location);
            } catch (IOException e) {
                complete = false;
                log.error("Failed to load series from location '{}': {}", location, e.getMessage(), e);
            }
        }

        // Only remove orphaned records when every root was scanned and at least one file was found;
        // otherwise a missing mount would wipe that root's catalog.
        if (complete && totalLoaded > 0) {
            removeOrphans();
        } else {
            log.warn("Skipping cleanup to prevent data loss (all roots scanned: {}, episodes found: {})",
                    complete, totalLoaded);
            episodeRepository.bulkUpdate(false);
            seriesRepository.bulkUpdate(false);
        }

        log.info("Series loading completed: {}", Instant.now());
    }

    private void removeOrphans() {
        var orphanedEpisodes = episodeRepository.findAllByUpdate(true);
        if (!orphanedEpisodes.isEmpty()) {
            log.info("Removing {} orphaned episode record(s) no longer present on disk", orphanedEpisodes.size());
            orphanedEpisodes.forEach(e -> log.info("Removing: {}/{}", e.getPath(), e.getFileName()));
            episodeRepository.deleteAll(orphanedEpisodes);
        }

        // A series with no episodes found this run has had all its episodes removed above.
        var orphanedSeries = seriesRepository.findAllByUpdate(true);
        if (!orphanedSeries.isEmpty()) {
            log.info("Removing {} orphaned series record(s) no longer present on disk", orphanedSeries.size());
            orphanedSeries.forEach(s -> log.info("Removing: '{}'", s.getName()));
            seriesRepository.deleteAll(orphanedSeries);
        }
    }
}
