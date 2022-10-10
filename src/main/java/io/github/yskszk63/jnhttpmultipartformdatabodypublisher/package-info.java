/**
 * multipart/form-data for `java.net.HttpClient`.
 *
 * <p>
 * Example
 * 
 * <pre>
 * {@code
 * var body = new MultipartFormData()
 *     .add(StringPart("name", "Hello,")
 *     .add(StringPart("value", "World!")
 *     .addFile("f", Path.of("index.html"), "text/html")
 *     .addFile("cpuinfo", Path.of("/proc/cpuinfo"), "text/html");
 *
 * var client = HttpClient.newHttpClient();
 * var request = HttpRequest.newBuilder(URI.create("http://localhost:8080/"))
 *     .header("Content-Type", body.contentType())
 *     .POST(body)
 *     .build();
 * var response = client.send(request, BodyHandlers.ofLines());
 * response.body().forEach(line -> System.out.println(line));
 * }
 * </pre>
 */
package io.github.yskszk63.jnhttpmultipartformdatabodypublisher;
