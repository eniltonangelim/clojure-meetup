FROM java:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/clojure-sse-meetup-0.0.1-SNAPSHOT-standalone.jar /clojure-sse-meetup/app.jar

EXPOSE 8080

CMD ["java", "-jar", "/clojure-sse-meetup/app.jar"]
