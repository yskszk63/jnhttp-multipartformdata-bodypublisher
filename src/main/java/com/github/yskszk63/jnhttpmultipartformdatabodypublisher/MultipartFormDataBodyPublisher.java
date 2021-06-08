package com.github.yskszk63.jnhttpmultipartformdatabodypublisher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Flow.Subscriber;
import java.util.function.Supplier;
import java.util.StringJoiner;

public class MultipartFormDataBodyPublisher implements BodyPublisher {
    private static String nextBoundary() {
        var random = new BigInteger(128, new Random());
        try (var formatter = new Formatter()) {
            return formatter.format("-----------------------------%039d", random).toString();
        }
    }

    private final String boundary = nextBoundary();
    private final List<Part> parts = new ArrayList<>();
    private Charset charset;
    private final BodyPublisher delegate = BodyPublishers.ofInputStream(
            () -> Channels.newInputStream(new MultipartFormDataChannel(this.boundary, this.parts, this.charset)));

    public MultipartFormDataBodyPublisher() {
        this(Charset.forName("utf8"));
    }

    public MultipartFormDataBodyPublisher(Charset charset) {
        this.charset = charset;
    }

    private MultipartFormDataBodyPublisher add(Part part) {
        this.parts.add(part);
        return this;
    }

    public MultipartFormDataBodyPublisher add(String key, String value) {
        return this.add(new StringPart(key, value, this.charset));
    }

    public MultipartFormDataBodyPublisher addFile(String key, Path path) {
        return this.add(new FilePart(key, path));
    }

    public MultipartFormDataBodyPublisher addFile(String key, Path path, String contentType) {
        return this.add(new FilePart(key, path, contentType));
    }

    public MultipartFormDataBodyPublisher addStream(String key, String filename, Supplier<InputStream> supplier) {
        return this.add(new StreamPart(key, filename, () -> Channels.newChannel(supplier.get())));
    }

    public MultipartFormDataBodyPublisher addStream(String key, String filename, Supplier<InputStream> supplier,
            String contentType) {
        return this.add(new StreamPart(key, filename, () -> Channels.newChannel(supplier.get()), contentType));
    }

    public MultipartFormDataBodyPublisher addChannel(String key, String filename,
            Supplier<ReadableByteChannel> supplier) {
        return this.add(new StreamPart(key, filename, supplier));
    }

    public MultipartFormDataBodyPublisher addChannel(String key, String filename,
            Supplier<ReadableByteChannel> supplier, String contentType) {
        return this.add(new StreamPart(key, filename, supplier, contentType));
    }

    public String contentType() {
        try (var formatter = new Formatter()) {
            return formatter.format("multipart/form-data;boundary=%s", this.boundary).toString();
        }
    }

    @Override
    public void subscribe(Subscriber<? super ByteBuffer> s) {
        delegate.subscribe(s);
    }

    @Override
    public long contentLength() {
        return delegate.contentLength();
    }

}

interface Part {
    String name();

    default Optional<String> filename() {
        return Optional.empty();
    }

    default Optional<String> contentType() {
        return Optional.empty();
    }

    ReadableByteChannel open() throws IOException;
}

class StringPart implements Part {
    private final String name;
    private final String value;
    private final Charset charset;

    StringPart(String name, String value, Charset charset) {
        this.name = name;
        this.value = value;
        this.charset = charset;
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public ReadableByteChannel open() throws IOException {
        var input = new ByteArrayInputStream(this.value.getBytes(this.charset));
        return Channels.newChannel(input);
    }
}

class StreamPart implements Part {
    private final String name;
    private final String filename;
    private final Supplier<ReadableByteChannel> supplier;
    private final String contentType;

    StreamPart(String name, String filename, Supplier<ReadableByteChannel> supplier, String contentType) {
        this.name = name;
        this.filename = filename;
        this.supplier = supplier;
        this.contentType = contentType;
    }

    StreamPart(String name, String filename, Supplier<ReadableByteChannel> supplier) {
        this(name, filename, supplier, "application/octet-stream");
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public Optional<String> filename() {
        return Optional.of(filename);
    }

    @Override
    public Optional<String> contentType() {
        return Optional.of(this.contentType);
    }

    @Override
    public ReadableByteChannel open() throws IOException {
        return this.supplier.get();
    }
}

class FilePart implements Part {
    private final String name;
    private final Path path;
    private final String contentType;

    FilePart(String name, Path path, String contentType) {
        this.name = name;
        this.path = path;
        this.contentType = contentType;
    }

    FilePart(String name, Path path) {
        this(name, path, "application/octet-stream");
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public Optional<String> filename() {
        return Optional.of(this.path.getFileName().toString());
    }

    @Override
    public Optional<String> contentType() {
        return Optional.of(this.contentType);
    }

    @Override
    public ReadableByteChannel open() throws IOException {
        return Files.newByteChannel(this.path);
    }
}

enum State {
    Boundary, Headers, Body, Done,
}

class MultipartFormDataChannel implements ReadableByteChannel {
    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");
    private boolean closed = false;
    private State state = State.Boundary;
    private final String boundary;
    private final Iterator<Part> parts;
    private ByteBuffer buf = ByteBuffer.allocate(0);
    private Part current = null;
    private ReadableByteChannel channel = null;
    private final Charset charset;

    MultipartFormDataChannel(String boundary, Iterable<Part> parts, Charset charset) {
        this.boundary = boundary;
        this.parts = parts.iterator();
        this.charset = charset;
    }

    @Override
    public void close() throws IOException {
        if (this.channel != null) {
            this.channel.close();
            this.channel = null;
        }
        this.closed = true;
    }

    @Override
    public boolean isOpen() {
        return !this.closed;
    }

    @Override
    public int read(ByteBuffer buf) throws IOException {
        while (true) {
            if (this.buf.hasRemaining()) {
                var n = Math.min(this.buf.remaining(), buf.remaining());
                var slice = this.buf.slice();
                slice.limit(n);
                buf.put(slice);
                this.buf.position(this.buf.position() + n);
                return n;
            }

            switch (this.state) {
            case Boundary:
                if (this.parts.hasNext()) {
                    this.current = this.parts.next();
                    this.buf = ByteBuffer.wrap((this.boundary + "\r\n").getBytes(LATIN1));
                    this.state = State.Headers;
                } else {
                    this.buf = ByteBuffer.wrap((this.boundary + "--").getBytes(LATIN1));
                    this.state = State.Done;
                }
                break;

            case Headers:
                this.buf = ByteBuffer.wrap(this.currentHeaders().getBytes(this.charset));
                this.state = State.Body;
                break;

            case Body:
                if (this.channel == null) {
                    this.channel = this.current.open();
                }

                var n = this.channel.read(buf);
                if (n == -1) {
                    this.channel.close();
                    this.channel = null;
                    this.buf = ByteBuffer.wrap("\r\n".getBytes(LATIN1));
                    this.state = State.Boundary;
                } else {
                    return n;
                }
                break;

            case Done:
                return -1;
            }
        }
    }

    static String escape(String s) {
        return s.replaceAll("\"", "\\\"");
    }

    String currentHeaders() {
        var current = this.current;

        if (current == null) {
            throw new IllegalStateException();
        }

        var contentType = current.contentType();
        var filename = current.filename();
        if (contentType.isPresent() && filename.isPresent()) {
            var format = new StringJoiner("\r\n", "", "\r\n")
                    .add("Content-Disposition: form-data; name=\"%s\"; filename=\"%s\"").add("Content-Type: %s")
                    .toString();
            try (var formatter = new Formatter()) {
                return formatter
                        .format(format, escape(current.name()), escape(filename.get()), escape(contentType.get()))
                        .toString() + "\r\n"; // FIXME
            }

        } else if (contentType.isPresent()) {
            var format = new StringJoiner("\r\n", "", "\r\n").add("Content-Disposition: form-data; name=\"%s\"")
                    .add("Content-Type: %s").toString();
            try (var formatter = new Formatter()) {
                return formatter.format(format, escape(current.name()), escape(contentType.get())).toString() + "\r\n"; // FIXME
                                                                                                                        // escape
            }

        } else if (filename.isPresent()) {
            var format = new StringJoiner("\r\n", "", "\r\n")
                    .add("Content-Disposition: form-data; name=\"%s\"; filename=\"%s\"").toString();
            try (var formatter = new Formatter()) {
                return formatter.format(format, escape(current.name()), escape(filename.get())).toString() + "\r\n"; // FIXME
                                                                                                                     // escape
            }

        } else {
            var format = new StringJoiner("\r\n", "", "\r\n").add("Content-Disposition: form-data; name=\"%s\"")
                    .toString();
            try (var formatter = new Formatter()) {
                return formatter.format(format, escape(current.name())).toString() + "\r\n"; // FIXME escape
            }
        }
    }
}
