# Execute Command
# docker rmi series-loader
# docker build -t rhm002/media-server:series-loader-latest -f ./MediaServer.Service.Series.Loader/Dockerfile .
# docker run --name series-loader --rm rhm002/media-server:series-loader-latest
# docker run --name series-loader --rm -e SPRING_DATASOURCE_URL="jdbc:sqlserver://192.168.1.8:1433;DatabaseName=MediaServer;encrypt=false" -e SPRING_DATASOURCE_USERNAME=media -e SPRING_DATASOURCE_PASSWORD= rhm002/media-server:series-loader-latest
# docker run --name series-loader --rm -it rhm002/media-server:series-loader-latest bash
# docker exec -it series-loader bash

FROM rhm002/openjdk:25-jre-trixie

LABEL maintainer="Ronald Mundell <rhm002@gmail.com>"

ARG SPRING_PROFILES_ACTIVE=k8s-psql

RUN mkdir -p /media \
    && useradd -d /media --uid=1000 media \
    && echo 'media:media' | chpasswd \
    && chown -R media:media /media

USER media
WORKDIR /media

ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}

COPY target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]