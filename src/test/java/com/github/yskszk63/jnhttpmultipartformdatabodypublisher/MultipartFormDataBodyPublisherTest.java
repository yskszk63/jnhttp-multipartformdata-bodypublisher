package com.github.yskszk63.jnhttpmultipartformdatabodypublisher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.util.Optional;
import java.util.List;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

public class MultipartFormDataBodyPublisherTest {
    @Test
    public void testStringPart() throws IOException {
        var part = new StringPart("key", "val", Charset.forName("utf8"));
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
        var input2 = new DigestInputStream(Files.newInputStream(Path.of("pom.xml")), expectDigest);
        while (input2.read(buf) != -1) {
            // nop
        }
        assertArrayEquals(expectDigest.digest(), actualDigest.digest());

        assertEquals(Optional.of("pom.xml"), part.filename());
        assertEquals(Optional.of("application/octet-stream"), part.contentType());
    }

    @Test
    public void testStreamPart() throws Exception {
        var part = new StreamPart("key", "fname",
                () -> Channels.newChannel(new ByteArrayInputStream("hello, world!".getBytes())));
        assertEquals(part.name(), "key");

        var buf = new byte[13];
        var b = ByteBuffer.wrap(buf);
        var input = part.open();
        while (input.read(b) != -1 && b.hasRemaining()) {
            // nop
        }
        assertArrayEquals("hello, world!".getBytes(), buf);

        assertEquals(Optional.of("fname"), part.filename());
        assertEquals(Optional.of("application/octet-stream"), part.contentType());
    }

    @Test
    public void testMultipartFormDataChannel() throws Exception {
        var channel = new MultipartFormDataChannel("----boundary",
                List.<Part> of(new StringPart("key", "value", Charset.forName("utf8"))), Charset.forName("utf8"));
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

        var expect = "------boundary\r\n" + "Content-Disposition: form-data; name=\"key\"\r\n" + "\r\n" + "value\r\n"
                + "------boundary--\r\n";
        assertEquals(expect, content.toString());
    }

    @Test
    public void testMultipartFormDataChannelException() throws Exception {
        var exception = new IOException();
        var channel = new MultipartFormDataChannel("----boundary",
                List.<Part> of(new StreamPart("key", "fname", () -> new ReadableByteChannel() {
                    @Override
                    public void close() {
                    }

                    @Override
                    public boolean isOpen() {
                        return true;
                    }

                    @Override
                    public int read(ByteBuffer buf) throws IOException {
                        throw exception;
                    }
                })), Charset.forName("utf8"));
        try (channel) {
            while (channel.read(ByteBuffer.allocate(1)) != -1) {
                // nop
            }
            assertEquals(false, true); // unreachable
        } catch (IOException e) {
            assertEquals(exception, e);
        }
    }

    @Test
    public void testMultipartFormData() throws Exception {
        var httpd = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        new Thread(() -> httpd.start()).start();
        try {
            var publisher = new MultipartFormDataBodyPublisher().add("key", "value").addFile("f1", Path.of("pom.xml"))
                    .addFile("f2", Path.of("pom.xml"), "application/xml")
                    .addStream("f3", "fname", () -> new ByteArrayInputStream("".getBytes()))
                    .addStream("f4", "fname", () -> new ByteArrayInputStream("".getBytes()), "application/xml")
                    .addChannel("f5", "fname", () -> Channels.newChannel(new ByteArrayInputStream("".getBytes())))
                    .addChannel("f6", "fname", () -> Channels.newChannel(new ByteArrayInputStream("".getBytes())),
                            "application/xml");
            var client = HttpClient.newHttpClient();
            var request = HttpRequest
                    .newBuilder(new URI("http", null, "localhost", httpd.getAddress().getPort(), "/", null, null))
                    .header("Content-Type", publisher.contentType()).POST(publisher).build();
            client.send(request, BodyHandlers.discarding());

        } finally {
            httpd.stop(0);
        }
    }
}
