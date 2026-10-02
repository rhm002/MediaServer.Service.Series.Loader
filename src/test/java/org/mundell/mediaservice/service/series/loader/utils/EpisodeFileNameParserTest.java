package org.mundell.mediaservice.service.series.loader.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mundell.mediaservice.service.series.loader.utils.EpisodeFileNameParser.ParsedEpisode;
import org.mundell.mediaservice.service.series.loader.utils.EpisodeFileNameParser.ShowName;

class EpisodeFileNameParserTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "null", value = {
            "Breaking Bad S01E01                                | Breaking Bad | null | 1 | 1  | null   | null",
            "Breaking Bad - S01E01 - Pilot [1080p]              | Breaking Bad | null | 1 | 1  | Pilot  | 1080p",
            "Breaking.Bad.S05E14.Ozymandias.720p.HDTV.x264-LOL   | Breaking Bad | null | 5 | 14 | Ozymandias | 720p",
            "the.office.us.s02e10.hdtv.x264-lol                 | the office us| null | 2 | 10 | null   | null",
            "Doctor Who (2005) - S10E01 - The Pilot [4K]        | Doctor Who   | 2005 | 10| 1  | The Pilot | 2160p",
            "Show_Name_S1E2                                     | Show Name    | null | 1 | 2  | null   | null",
            "Friends 3x05 The One with Frank Jr                 | Friends      | null | 3 | 5  | The One with Frank Jr | null",
            "Lost S01E01E02 Pilot WEB-DL                        | Lost         | null | 1 | 1  | Pilot  | null",
            "1923 S01E01 1080p WEB                              | 1923         | null | 1 | 1  | null   | 1080p",
    })
    void parse_supportedFormats(String input, String show, Integer year, int season, int episode, String title, String quality) {
        ParsedEpisode ep = EpisodeFileNameParser.parse(input.trim()).orElseThrow();
        assertEquals(show.trim(), ep.showName());
        assertEquals(year, ep.year());
        assertEquals(season, ep.season());
        assertEquals(episode, ep.episode());
        assertEquals(title, ep.title());
        assertEquals(quality, ep.quality());
    }

    @Test
    void parse_noMarker_isEmpty() {
        assertTrue(EpisodeFileNameParser.parse("Some Movie (2020) [1080p]").isEmpty());
        assertTrue(EpisodeFileNameParser.parse("Video 1920x1080").isEmpty());
        assertTrue(EpisodeFileNameParser.parse(null).isEmpty());
    }

    @Test
    void isEpisodeName_detectsEztvEpisodeFolders() {
        assertTrue(EpisodeFileNameParser.isEpisodeName("Breaking Bad S01E01"));
        assertFalse(EpisodeFileNameParser.isEpisodeName("Breaking Bad"));
        assertFalse(EpisodeFileNameParser.isEpisodeName("Season 1"));
    }

    @Test
    void parseShowName_splitsTrailingYear() {
        assertEquals(new ShowName("Doctor Who", 2005), EpisodeFileNameParser.parseShowName("Doctor.Who.(2005)"));
        assertEquals(new ShowName("Doctor Who", 2005), EpisodeFileNameParser.parseShowName("Doctor Who 2005"));
        assertEquals(new ShowName("Breaking Bad", null), EpisodeFileNameParser.parseShowName("Breaking Bad"));
        assertEquals(new ShowName("1923", null), EpisodeFileNameParser.parseShowName("1923"));
    }

    @Test
    void key_ignoresCaseAndPunctuation() {
        assertEquals(EpisodeFileNameParser.key("Grey's Anatomy"), EpisodeFileNameParser.key("greys.anatomy"));
        assertNull(EpisodeFileNameParser.parse("no marker here").orElse(null));
    }
}
