# nexus

Netty-based web server

# Requirements

- Java 25
- Maven
- GraalVM, for [native image](https://www.graalvm.org/latest/reference-manual/native-image/)

# Features

- [Netty-based](https://netty.io/), async by default
- GraalVM [native image](https://www.graalvm.org/latest/reference-manual/native-image/) compatible
- Route table generated at build time
- `reflection-config.json` automatically generated at build time for objects annotated with
  `@RequestBody` (GraalVM native image)
- DI registry with @Controller, @Service, @Repository annotations built at build time
- Middleware system

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
sh dockerfile-update.sh
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

```
Apple MacBook Air M3 16GB
bombardier version 2.0.2 darwin/arm64
bombardier -c 1024 -n 5000000 http://localhost:15000/api/v1/heartbeat
Bombarding http://localhost:15000/api/v1/heartbeat with 5000000 request(s) using 1024 connection(s)
 5000000 / 5000000 [============================================================================] 100.00% 109665/s 45s
Done!
Statistics        Avg      Stdev        Max
  Reqs/sec    110041.57   16194.75  170102.92
  Latency        9.31ms     5.04ms   297.72ms
  HTTP codes:
    1xx - 0, 2xx - 5000000, 3xx - 0, 4xx - 0, 5xx - 0
    others - 0
  Throughput:    30.90MB/s
```
