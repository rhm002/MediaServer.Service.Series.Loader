package org.mundell.mediaservice.service.series.loader.services;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.mundell.mediaserver.dal.models.Series;
import org.mundell.mediaserver.dal.models.SeriesEpisode;
import org.mundell.mediaserver.dal.repositories.series.SeriesEpisodeRepository;
import org.mundell.mediaserver.dal.repositories.series.SeriesRepository;
import org.mundell.mediaservice.service.series.loader.utils.EpisodeFileNameParser;
import org.mundell.mediaservice.service.series.loader.utils.EpisodeFileNameParser.ParsedEpisode;
import org.mundell.mediaservice.service.series.loader.utils.EpisodeFileNameParser.ShowName;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Walks one series library root and upserts {@link Series} / {@link SeriesEpisode} rows.
 *
 * <p>Layout-agnostic: any video file with an episode marker is loaded. The show is taken from the top-level
 * folder under the root ({@code <root>/Show Name (2008)/Season 1/...}) unless that folder is itself an episode
 * folder, as produced by the EZTV downloader ({@code <root>/Show Name S01E01/...}), in which case the show
 * name comes from the file name.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeriesLibraryScanner {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("mp4", "avi", "mov", "mkv", "mpg", "m4v", "ts");
    private static final Pattern SAMPLE = Pattern.compile("(?i)(?<![a-z0-9])sample(?![a-z0-9])");

    private final SeriesRepository seriesRepository;
    private final SeriesEpisodeRepository episodeRepository;
    private final TmdbMetadataService tmdbMetadataService;

    /** @return number of episode files loaded from {@code location} */
    public int scan(String location) throws IOException {
        Path root = Paths.get(location);
        Map<String, Series> touched = new LinkedHashMap<>();
        int counter = 0;

        for (Path file : findVideoFiles(root)) {
            try {
                if (load(root, file, touched)) {
                    counter++;
                }
            } catch (IOException | RuntimeException e) {
                log.error("Failed to load '{}': {}", file, e.getMessage(), e);
            }
        }

        for (Series series : touched.values()) {
            try {
                tmdbMetadataService.enrich(series);
                series.setUpdate(false);
                seriesRepository.save(series);
            } catch (RuntimeException e) {
                log.error("Failed to update series '{}': {}", series.getName(), e.getMessage(), e);
            }
        }
        return counter;
    }

    private boolean load(Path root, Path file, Map<String, Series> touched) throws IOException {
        String fileName = file.getFileName().toString();
        String baseName = FilenameUtils.getBaseName(fileName);

        var parsed = EpisodeFileNameParser.parse(baseName);
        if (parsed.isEmpty()) {
            log.warn("Skipping file with no SxxExx / NxNN episode marker: {}", file);
            return false;
        }
        ParsedEpisode ep = parsed.get();

        ShowName show = new ShowName(ep.showName(), ep.year());
        Path showPath = file.getParent();
        Path relative = root.relativize(file);
        if (relative.getNameCount() > 1) {
            String topFolder = relative.getName(0).toString();
            if (!EpisodeFileNameParser.isEpisodeName(topFolder)) {
                ShowName folderShow = EpisodeFileNameParser.parseShowName(topFolder);
                if (!folderShow.name().isBlank()) {
                    show = folderShow;
                    showPath = root.resolve(topFolder);
                }
            }
        }
        if (show.name() == null || show.name().isBlank()) {
            log.warn("Skipping file with no show name: {}", file);
            return false;
        }

        String key = EpisodeFileNameParser.key(show.name());
        Series series = touched.get(key);
        if (series == null) {
            series = findOrCreateSeries(show, showPath);
            touched.put(key, series);
        }

        String folder = file.getParent().toString();
        SeriesEpisode episode = episodeRepository.findFirstByPathAndFileName(folder, baseName);
        if (episode == null) {
            episode = new SeriesEpisode();
            log.info("ADDING: '{}' S{}E{} [{}]", series.getName(),
                    String.format("%02d", ep.season()), String.format("%02d", ep.episode()), ep.quality());
        }

        episode.setSeries(series);
        episode.setSeason(ep.season());
        episode.setEpisode(ep.episode());
        episode.setTitle(ep.title());
        episode.setPath(folder);
        episode.setFileName(baseName);
        episode.setExtension(FilenameUtils.getExtension(fileName).toLowerCase(Locale.ROOT));
        episode.setContentType(Files.probeContentType(file));
        episode.setSize(Files.size(file));
        episode.setQuality(ep.quality());
        episode.setUpdate(false);
        episodeRepository.save(episode);
        return true;
    }

    private Series findOrCreateSeries(ShowName show, Path showPath) {
        Series series = seriesRepository.findFirstByNameIgnoreCase(show.name());
        if (series == null) {
            series = new Series();
            series.setName(show.name());
            series.setYear(show.year());
            log.info("ADDING SERIES: '{}'", show.name());
        }
        series.setPath(showPath.toString());
        series.setUpdate(false);
        return seriesRepository.save(series);
    }

    private static List<Path> findVideoFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // Skip hidden and NAS metadata folders (.@__thumb, @eaDir, #recycle)
                String name = dir.equals(root) ? "" : dir.getFileName().toString();
                return name.startsWith(".") || name.startsWith("@") || name.startsWith("#")
                        ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString();
                if (attrs.isRegularFile()
                        && SUPPORTED_EXTENSIONS.contains(FilenameUtils.getExtension(name).toLowerCase(Locale.ROOT))
                        && !SAMPLE.matcher(FilenameUtils.getBaseName(name)).find()) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                log.warn("Cannot read '{}': {}", file, exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(null);
        return files;
    }
}
