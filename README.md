# java.net.HttpClient multipart/form-data BodyPublisher

[![codecov](https://codecov.io/gh/yskszk63/jnhttp-multipartformdata-bodypublisher/branch/main/graph/badge.svg?token=KYD3EHTNI4)](https://codecov.io/gh/yskszk63/jnhttp-multipartformdata-bodypublisher)

[API Document](https://yskszk63.github.io/jnhttp-multipartformdata-bodypublisher/)

## Dependency

```xml
  <groupId>io.github.yskszk63</groupId>
  <artifactId>jnhttp-multipartformdata-bodypublisher</artifactId>
  <version>0.0.1-SNAPSHOT</version>
```

TODO: not released yet.

or copy from [MultipartFormDataBodyPublisher.java](https://github.com/yskszk63/jnhttp-multipartformdata-bodypublisher/blob/main/src/main/java/com/github/yskszk63/jnhttpmultipartformdatabodypublisher/MultipartFormDataBodyPublisher.java).

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
