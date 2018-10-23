FROM gradle:jdk8-alpine

# Install app dependencies
WORKDIR /home/gradle
COPY settings.gradle build.gradle ./
RUN gradle getDeps

# Build app
COPY src ./src
RUN gradle bootRepackage \
	&& cp build/libs/*.jar /home/gradle/tix-time-condenser.jar

# Bundle compiled app into target image
FROM openjdk:8-jre-alpine
WORKDIR /root
RUN apk add bash
COPY run.sh .
COPY wait-for-it.sh .
COPY --from=0 /home/gradle/tix-time-condenser.jar .
CMD ["./run.sh"]
