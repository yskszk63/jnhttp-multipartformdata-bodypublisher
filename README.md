# java.net.HttpClient multipart/form-data BodyPublisher

## Dependency

```xml
  <groupId>com.github.yskszk63.jnhttp-multipartformdata-bodypublisher</groupId>
  <artifactId>jnhttp-multipartformdata-bodypublisher</artifactId>
  <version>0.0.1-SNAPSHOT</version>
```

## Example

```java
        var body = new MultipartFormData()
            .add(StringPart("name", "Hello,")
            .add(StringPart("value", "World!")
            .addFile("f", Path.of("index.html"), "text/html")
            .addFile("cpuinfo", Path.of("/proc/cpuinfo"), "text/html");

        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:8080/"))
            .header("Content-Type", body.contentType())
            .POST(body)
            .build();
        var response = client.send(request, BodyHandlers.ofLines());
        response.body().forEach(line -> System.out.println(line));
```

## License

Licensed under either of

 * Apache License, Version 2.0
   ([LICENSE-APACHE](LICENSE-APACHE) or http://www.apache.org/licenses/LICENSE-2.0)
 * MIT license
   ([LICENSE-MIT](LICENSE-MIT) or http://opensource.org/licenses/MIT)

at your option.

## Contribution

Unless you explicitly state otherwise, any contribution intentionally submitted
for inclusion in the work by you, as defined in the Apache-2.0 license, shall be
dual licensed as above, without any additional terms or conditions.
