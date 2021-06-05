package com.github.yskszk63.jnhttpmultipartformdatabodypublisher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Flow.Subscriber;

public class MultipartFormDataBodyPublisher implements BodyPublisher {
    private static String nextBoundary() {
        var random = new BigInteger(128, new Random());
        return "-----------------------------%039d".formatted(random);
    }

    private final String boundary = nextBoundary();
    private final List<Part> parts = new ArrayList<>();
    private final BodyPublisher delegate = BodyPublishers
            .ofInputStream(() -> Channels.newInputStream(new MultipartFormDataChannel(this.boundary, this.parts)));

    private MultipartFormDataBodyPublisher add(Part part) {
        this.parts.add(part);
        return this;
    }

    public MultipartFormDataBodyPublisher add(String key, String value) {
        return this.add(new StringPart(key, value));
    }

    public MultipartFormDataBodyPublisher addFile(String key, Path path, String contentType) {
        return this.add(new FilePart(key, path, contentType));
    }

    public String contentType() {
        return "multipart/form-data;boundary=%s".formatted(this.boundary);
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

    StringPart(String name, String value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public ReadableByteChannel open() throws IOException {
        var input = new ByteArrayInputStream(this.value.getBytes("utf8"));
        return Channels.newChannel(input);
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
    private boolean closed = false;
    private State state = State.Boundary;
    private final String boundary;
    private final Iterator<Part> parts;
    private ByteBuffer buf = ByteBuffer.allocate(0);
    private Part current = null;
    private ReadableByteChannel channel = null;

    MultipartFormDataChannel(String boundary, Iterable<Part> parts) {
        this.boundary = boundary;
        this.parts = parts.iterator();
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
                buf.put(this.buf.slice(this.buf.position(), n));
                this.buf.position(this.buf.position() + n);
                return n;
            }

            switch (this.state) {
            case Boundary -> {
                if (this.parts.hasNext()) {
                    this.current = this.parts.next();
                    this.buf = ByteBuffer.wrap("%s\r\n".formatted(this.boundary).getBytes());
                    this.state = State.Headers;
                } else {
                    this.buf = ByteBuffer.wrap("%s--".formatted(this.boundary).getBytes());
                    this.state = State.Done;
                }
            }

            case Headers -> {
                this.buf = ByteBuffer.wrap(this.currentHeaders().getBytes());
                this.state = State.Body;
            }

            case Body -> {
                if (this.channel == null) {
                    this.channel = this.current.open();
                }

                var n = this.channel.read(buf);
                if (n == -1) {
                    this.channel.close();
                    this.channel = null;
                    this.buf = ByteBuffer.wrap("\r\n".getBytes());
                    this.state = State.Boundary;
                } else {
                    return n;
                }
            }

            case Done -> {
                return -1;
            }
            }
        }
    }

    String currentHeaders() {
        var current = this.current;

        if (current == null) {
            throw new IllegalStateException();
        }

        var contentType = current.contentType();
        var filename = current.filename();
        if (contentType.isPresent() && filename.isPresent()) {
            return """
            Content-Disposition: form-data; name="%s"; filename="%s"\r
            Content-Type: %s\r
            \r
            """.formatted(current.name(), filename.get(), contentType.get());
        } else if (contentType.isPresent()) {
            return """
            Content-Disposition: form-data; name="%s"\r
            Content-Type: %s\r
            \r
            """.formatted(current.name(), contentType.get());
        } else if (filename.isPresent()) {
            return """
            Content-Disposition: form-data; name="%s"; filename="%s"\r
            \r
            """.formatted(current.name(), contentType.get());
        } else {
            return """
            Content-Disposition: form-data; name="%s"\r
            \r
            """.formatted(current.name());
        }
    }
}
