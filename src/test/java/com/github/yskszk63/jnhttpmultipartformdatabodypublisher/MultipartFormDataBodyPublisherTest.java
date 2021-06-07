package com.github.yskszk63.jnhttpmultipartformdatabodypublisher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.channels.Channels;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.util.Optional;
import java.util.List;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

public class MultipartFormDataBodyPublisherTest {
    @Test
    public void testStringPart() throws IOException {
        var part = new StringPart("key", "val");
        assertEquals(part.name(), "key");

        var buf = new byte[3];
        var input = part.open();
        assertEquals(3, input.read(ByteBuffer.wrap(buf)));
        assertArrayEquals("val".getBytes(), buf);

        assertEquals(Optional.empty(), part.filename());
        assertEquals(Optional.empty(), part.contentType());
    }

    @Test
    public void testFilePart() throws Exception {
        var part = new FilePart("key", Path.of("pom.xml"));
        assertEquals(part.name(), "key");

        var buf = new byte[1024 * 8];
        var actualDigest = MessageDigest.getInstance("SHA-256");
        var input = new DigestInputStream(Channels.newInputStream(part.open()), actualDigest);
        while (input.read(buf) != -1) {
            // nop
        }

        var expectDigest = MessageDigest.getInstance("SHA-256");
        var input2 = new DigestInputStream(Channels.newInputStream(part.open()), expectDigest);
        while (input2.read(buf) != -1) {
            // nop
        }
        assertArrayEquals(expectDigest.digest(), actualDigest.digest());

        assertEquals(Optional.of("pom.xml"), part.filename());
        assertEquals(Optional.of("application/octet-stream"), part.contentType());
    }

    @Test
    public void testMultipartFormDataChannel() throws Exception {
        var channel = new MultipartFormDataChannel("----boundary", List.<Part>of(
            new StringPart("key", "value")
        ));
        assertEquals(true, channel.isOpen());
        var content = new StringBuilder();
        try (channel) {
            var r = Channels.newReader(channel, "utf8");
            var buf = new char[1024 * 8];
            int n;
            while ((n = r.read(buf)) != -1) {
                content.append(buf, 0, n);
            }
        }
        assertEquals(false, channel.isOpen());

        var expect = """
            ----boundary\r
            Content-Disposition: form-data; name="key"\r
            \r
            value\r
            ----boundary--""";
        assertEquals(expect, content.toString());
    }

    @Test
    public void testMultipartFormData() throws Exception {
        var httpd = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        new Thread(() -> httpd.start()).start();
        try {
            var client = HttpClient.newHttpClient();
            var request = HttpRequest.newBuilder(new URI("http://localhost:%d/".formatted(httpd.getAddress().getPort())))
                .POST(new MultipartFormDataBodyPublisher().add("key", "value").addFile("f", Path.of("pom.xml"), "application/xml"))
                .build();
            client.send(request, BodyHandlers.discarding());

        } finally {
            httpd.stop(0);
        }
    }
}
