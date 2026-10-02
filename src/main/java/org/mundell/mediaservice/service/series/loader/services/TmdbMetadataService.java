package org.mundell.mediaservice.service.series.loader.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.mundell.mediaserver.dal.models.Genre;
import org.mundell.mediaserver.dal.models.Series;
import org.mundell.mediaserver.dal.repositories.media.GenreRepository;
import org.mundell.mediaserver.tmdb.dal.models.TmdbShow;
import org.mundell.mediaserver.tmdb.dal.repositories.TmdbShowRepository;
import org.mundell.mediaservice.service.series.loader.utils.EpisodeFileNameParser;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cross-references a {@link Series} against the {@code tmdb_shows} table (populated by MediaServer.Service.TMDB)
 * and copies over metadata, genres and the poster image (stored as Base64).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TmdbMetadataService {

    private static final String POSTER_BASE = "https://image.tmdb.org/t/p/w500";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);
    private static final int CANDIDATE_LIMIT = 50;

    private final TmdbShowRepository tmdbShowRepository;
    private final GenreRepository genreRepository;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public void enrich(Series series) {
        Optional<TmdbShow> match = findMatch(series.getName(), series.getYear());
        if (match.isEmpty()) {
            log.info("TMDB SHOW NOT FOUND: '{}'{}", series.getName(),
                    series.getYear() != null ? " (" + series.getYear() + ")" : "");
            return;
        }

        TmdbShow show = match.get();
        series.setTmdbId(show.getTmdbId());
        series.setImdbId(show.getImdbId());
        series.setSynopsis(show.getOverview());
        series.setRating(show.getVoteAverage());
        series.setStatus(show.getStatus());
        series.setNetwork(show.getNetwork());
        series.setNumberOfSeasons(show.getNumberOfSeasons());
        series.setNumberOfEpisodes(show.getNumberOfEpisodes());
        if (series.getYear() == null) {
            series.setYear(parseYear(show.getFirstAirDate()));
        }

        String poster = posterUrl(show.getPosterPath());
        if (poster != null && (series.getPosterImage64() == null || !poster.equals(series.getPosterImage()))) {
            series.setPosterImage(poster);
            series.setPosterImage64(retrieveImage(poster));
        }

        addGenres(series, show.getGenres());
    }

    /**
     * Exact (punctuation/case-insensitive) name match; when several shows share a name, prefer the one whose
     * first-air year matches, otherwise the most popular.
     */
    Optional<TmdbShow> findMatch(String name, Integer year) {
        String key = EpisodeFileNameParser.key(name);
        List<TmdbShow> exact = tmdbShowRepository
                .findAllByNameContainingIgnoreCase(name, PageRequest.of(0, CANDIDATE_LIMIT))
                .stream()
                .filter(s -> key.equals(EpisodeFileNameParser.key(s.getName()))
                        || (s.getOriginalName() != null && key.equals(EpisodeFileNameParser.key(s.getOriginalName()))))
                .toList();

        if (year != null) {
            Optional<TmdbShow> byYear = exact.stream()
                    .filter(s -> year.equals(parseYear(s.getFirstAirDate())))
                    .findFirst();
            if (byYear.isPresent()) {
                return byYear;
            }
        }
        return exact.stream()
                .max(Comparator.comparing(s -> s.getPopularity() == null ? 0d : s.getPopularity()));
    }

    private void addGenres(Series series, String genres) {
        if (genres == null || genres.isBlank()) {
            return;
        }
        for (String displayName : genres.split(",")) {
            String trimmed = displayName.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Genre genre = genreRepository.findByName(trimmed.toLowerCase(Locale.ROOT));
            if (genre == null) {
                genre = genreRepository.save(new Genre(trimmed));
            }
            String genreName = genre.getName();
            if (series.getGenres().stream().noneMatch(g -> g.getName().equals(genreName))) {
                series.getGenres().add(genre);
            }
        }
    }

    private static String posterUrl(String posterPath) {
        if (posterPath == null || posterPath.isBlank()) {
            return null;
        }
        return posterPath.startsWith("http") ? posterPath : POSTER_BASE + posterPath;
    }

    private static Integer parseYear(String date) {
        if (date == null || date.length() < 4) {
            return null;
        }
        try {
            return Integer.valueOf(date.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String retrieveImage(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(HTTP_TIMEOUT).GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.error("Failed to retrieve image from '{}': HTTP {}", url, response.statusCode());
                return null;
            }
            return Base64.getEncoder().encodeToString(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted retrieving image from '{}'", url);
        } catch (Exception e) {
            log.error("Failed to retrieve image from '{}': {}", url, e.getMessage(), e);
        }
        return null;
    }
}
