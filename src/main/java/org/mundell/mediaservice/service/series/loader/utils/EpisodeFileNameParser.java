package org.mundell.mediaservice.service.series.loader.utils;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses series episode file and folder names.
 *
 * <p>Supported episode markers (case-insensitive, anywhere in the name):
 * <ul>
 *   <li>{@code S01E02}, {@code S1E2}, {@code S01.E02}, {@code S01E02E03} (multi-episode keeps the first)</li>
 *   <li>{@code 1x02}</li>
 * </ul>
 * Text before the marker is the show name (dots/underscores become spaces, a trailing year is split off);
 * text after it yields an optional episode title and quality.
 */
public final class EpisodeFileNameParser {

    public record ShowName(String name, Integer year) {}

    public record ParsedEpisode(String showName, Integer year, int season, int episode, String title, String quality) {}

    private static final Pattern SXXEXX = Pattern.compile(
            "(?i)(?<![a-z0-9])S(\\d{1,2})[ ._-]?E(\\d{1,3})(?:[ ._-]?E\\d{1,3})*(?![0-9])");
    private static final Pattern NXNN = Pattern.compile(
            "(?i)(?<![a-z0-9])(\\d{1,2})x(\\d{2,3})(?![0-9])");
    private static final Pattern TRAILING_YEAR = Pattern.compile(
            "^(.*?)[ -]*[(\\[]?((?:19|20)\\d{2})[)\\]]?$");
    private static final Pattern QUALITY = Pattern.compile(
            "(?i)(?<![a-z0-9])(2160p|1080p|720p|576p|480p|4k|uhd)(?![a-z0-9])");
    private static final Pattern RELEASE_TAG = Pattern.compile(
            "(?i)(?<![a-z0-9])(hdtv|web|webrip|web-dl|webdl|bluray|bdrip|brrip|dvdrip|x264|x265|h264|h265|hevc|"
                    + "aac|ac3|ddp?5\\.1|proper|repack|amzn|nf|hulu|dsnp|internal|multi|subbed)(?![a-z0-9])");

    private EpisodeFileNameParser() {}

    public static Optional<ParsedEpisode> parse(String baseName) {
        if (baseName == null) {
            return Optional.empty();
        }
        Matcher m = findMarker(baseName);
        if (m == null) {
            return Optional.empty();
        }
        ShowName show = parseShowName(baseName.substring(0, m.start()));
        String rest = baseName.substring(m.end());
        return Optional.of(new ParsedEpisode(
                show.name(),
                show.year(),
                Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)),
                parseTitle(rest),
                parseQuality(rest)));
    }

    /** True when the name contains an episode marker (e.g. an EZTV per-episode folder). */
    public static boolean isEpisodeName(String name) {
        return name != null && findMarker(name) != null;
    }

    /** Normalises a show name and splits off a trailing year, e.g. {@code Doctor.Who.(2005)} → Doctor Who / 2005. */
    public static ShowName parseShowName(String raw) {
        String name = normalise(raw);
        Matcher m = TRAILING_YEAR.matcher(name);
        if (m.matches() && !m.group(1).isBlank()) {
            return new ShowName(trimSeparators(m.group(1)), Integer.valueOf(m.group(2)));
        }
        return new ShowName(name, null);
    }

    /** Lower-case key used to group files and compare show names. */
    public static String key(String showName) {
        return showName.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    static String parseQuality(String rest) {
        Matcher m = QUALITY.matcher(rest);
        if (!m.find()) {
            return null;
        }
        String quality = m.group(1).toLowerCase(Locale.ROOT);
        return quality.equals("4k") || quality.equals("uhd") ? "2160p" : quality;
    }

    static String parseTitle(String rest) {
        int end = rest.length();
        end = Math.min(end, indexOrLength(rest, rest.indexOf('[')));
        end = Math.min(end, indexOrLength(rest, rest.indexOf('(')));
        Matcher quality = QUALITY.matcher(rest);
        if (quality.find()) {
            end = Math.min(end, quality.start());
        }
        Matcher tag = RELEASE_TAG.matcher(rest);
        if (tag.find()) {
            end = Math.min(end, tag.start());
        }
        String title = trimSeparators(normalise(rest.substring(0, end)));
        return title.isEmpty() ? null : title;
    }

    private static Matcher findMarker(String name) {
        Matcher m = SXXEXX.matcher(name);
        if (m.find()) {
            return m;
        }
        m = NXNN.matcher(name);
        return m.find() ? m : null;
    }

    private static String normalise(String raw) {
        return trimSeparators(raw.replaceAll("[._]+", " ").replaceAll("\\s+", " "));
    }

    private static String trimSeparators(String s) {
        return s.replaceAll("^[\\s\\-]+|[\\s\\-]+$", "");
    }

    private static int indexOrLength(String s, int index) {
        return index < 0 ? s.length() : index;
    }
}
