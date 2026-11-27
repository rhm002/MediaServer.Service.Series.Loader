# MediaServer.Service.Series.Loader

## Overview

`series-loader-service` (Maven artifact `org.mundell.mediaserver:series-loader-service`) is a **one-shot, headless batch job** in the MediaServer suite — the TV-series counterpart of [MediaServer.Service.Movie.Loader](../MediaServer.Service.Movie.Loader). It has no HTTP server (`spring.main.web-application-type=none`): it starts, scans the configured series folder(s), syncs what it finds into the shared MediaServer database, and exits.

Recurring execution is handled externally (Kubernetes `CronJob`, or a manual `docker compose run`).

## What It Does

On startup (`Application` implements `CommandLineRunner`) `SeriesLoaderService.process()`:

1. Seeds the `SERIES_LOCATION` row in the DB-backed `config` table if missing (default `\\CUBE\NAS\Series`). The value is a semicolon-separated list of root folders.
2. Verifies at least one root is a reachable directory, otherwise aborts with an error rather than risk wiping the database.
3. Marks all `series_shows` and `series_episodes` rows `updated = true` (deletion candidates).
4. `SeriesLibraryScanner` walks each root recursively and loads every video file (`mp4`, `avi`, `mov`, `mkv`, `mpg`, `m4v`, `ts`, case-insensitive) that carries an episode marker. Files named `sample`, and folders starting with `.`, `@` or `#` (NAS metadata), are skipped.
5. Each file is parsed by `EpisodeFileNameParser`:
   - Episode marker: `S01E02`, `S1E2`, `S01.E02`, `S01E02E03` (first episode kept) or `1x02`
   - Show name: text before the marker (dots/underscores → spaces, trailing year such as `(2005)` split off)
   - Title / quality: text after the marker, e.g. `- Pilot [1080p]` → `Pilot`, `1080p` (`4K`/`UHD` → `2160p`)
6. The **show** is the top-level folder under the root, unless that folder is itself an episode folder (the EZTV downloader layout), in which case the show name comes from the file name. Both layouts work, and can be mixed:
   ```
   <root>/Breaking Bad (2008)/Season 01/Breaking Bad - S01E01 - Pilot [1080p].mkv
   <root>/Breaking Bad S01E01/Breaking.Bad.S01E01.720p.HDTV.x264.mkv
   ```
7. Episodes are upserted by folder + file name. Each show touched is cross-referenced against `tmdb_shows` (populated by MediaServer.Service.TMDB) by name (case/punctuation-insensitive, year used to disambiguate) to fill in TMDB/IMDb IDs, overview, rating, status, network, season/episode counts, genres and the poster (downloaded and stored as Base64).
8. Rows still marked `updated = true` are deleted as orphaned — but **only if every configured root was scanned and at least one file was found**, so an unavailable mount never wipes its part of the catalog.
9. The app exits via `SpringApplication.exit(...)` / `System.exit(...)`.

## Data Model (MediaServer.DAL)

| Table             | Entity          | Notes                                                    |
| ----------------- | --------------- | -------------------------------------------------------- |
| `series_shows`    | `Series`        | One row per show; TMDB metadata, poster, `updated` flag  |
| `series_episodes` | `SeriesEpisode` | One row per file; season, episode, title, quality, size  |
| `series_genre`    | —               | Join table to the shared `genre` table                   |

Schema migrations (`V303__create_series_tables.sql`) live in MediaServer.DAL and are applied by the main **MediaServer** app; Flyway is disabled here.

## Project Structure

```
MediaServer.Service.Series.Loader/
├── src/main/java/org/mundell/mediaservice/service/series/loader/
│   ├── Application.java                 # @SpringBootApplication + CommandLineRunner entry point
│   ├── config/SeriesLoaderConfiguration.java   # seeds/reads SERIES_LOCATION
│   ├── constants/Constants.java
│   ├── services/
│   │   ├── SeriesLoaderService.java     # orchestrates scan, orphan cleanup, safety checks
│   │   ├── SeriesLibraryScanner.java    # per-root walk, show/episode upsert
│   │   └── TmdbMetadataService.java     # TMDB cross-reference, genres, poster download
│   └── utils/EpisodeFileNameParser.java # SxxExx / NxNN parsing
├── src/main/resources/                  # application(-profile).properties, logback-spring.xml
├── pom.xml
└── Dockerfile
```

## Dependencies

Spring Boot **4.2.0**, Java **25**.

Internal: `org.mundell:utilities:2.0`, `org.mundell:common:2.0`, `org.mundell:dal-common:2.0`, `org.mundell.mediaserver:dal:2.0` (Series/SeriesEpisode/Genre), `org.mundell.mediaserver:tmdb-dal:2.0` (TmdbShow).

External: `spring-boot-starter-data-jpa`, `postgresql`, `mssql-jdbc`, `commons-io`, Lombok.

## Configuration Profiles

Same set as the Movie Loader: default (`application.properties`), `psql-docker`, `psql-sphere`, `mssql`, `mssql-docker`, `mssql-sphere`, `k8s-psql`, `k8s-mssql`.

Environment variables: `SPRING_PROFILES_ACTIVE` (Dockerfile default `k8s-psql`), `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`. The password has no default in the committed properties — supply it via `.env` (git-ignored), Docker `-e`, or a Kubernetes secret.

## Building

```bash
mvn clean package
docker build -t rhm002/media-server:series-loader-latest -f ./MediaServer.Service.Series.Loader/Dockerfile .
```

## Running

```bash
# Standalone Docker
docker run --rm \
  -e SPRING_PROFILES_ACTIVE=psql-docker \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://<host>:5432/mediaserver" \
  -e SPRING_DATASOURCE_USERNAME=media \
  -e SPRING_DATASOURCE_PASSWORD=<password> \
  -v /path/to/series:/mnt/series:ro \
  rhm002/media-server:series-loader-latest

# Local
./start.sh            # or start.bat [profile]
```

In VS Code, `.vscode/launch.json` has launch configs for `mssql`, `psql-docker` and `mssql-docker`, plus **"Attach: Series Loader (remote/docker)"** on port `5005`.

## Related Services

- **MediaServer.Service.TMDB** — populates `tmdb_shows`, the metadata source for this service
- **MediaServer.Service.EZTV.Downloader** — drops downloaded episodes into `SERIES_LOCATION`
- **MediaServer.DAL** — `Series` / `SeriesEpisode` entities, repositories and migrations
- **MediaServer** — main API, applies schema migrations
