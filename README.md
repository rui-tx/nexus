# nexus

Netty-based web server

# Requirements

- Java 25
- Maven
- GraalVM, for [native image](https://www.graalvm.org/latest/reference-manual/native-image/)

# Features

- Async by default (and for now)
- Route table generated at build time

# Usage

Check if everything is working

```
mvn test
```

## Jar

Generate the `.jar`

```
mvn clean package -Puber-jar
```

Run it

```
java -jar nexus-core/target/nexus-core-[version]-jar-with-dependencies.jar
```

## GraalVM native image

Generate bin file

```
mvn clean package -Pnative
```

Run it

```
./nexus-core/target/native/nexus-[version]
```

## Docker

Build and run

```
podman compose up --build
```

## Test it

Server port is `15000`

```
curl -s 'http://localhost:15000/api/v1/heartbeat'
{
  "date": "2025-10-28T13:14:57.258053Z[Europe/Lisbon]",
  "status": 200,
  "data": "up"
}
```
