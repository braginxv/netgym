package com.techlook;

public enum Fault {
    AsyncClientError(true, "Async client common error: %s"),
    AsyncClientChannelConnectError(true, "Async client: channel not connected"),
    AsyncClientChannelConfigureError(true, "Client channel setup error"),
    AsyncClientChannelWriteError(true, "Client channel write error"),
    AsyncClientChannelReadError(true, "Client channel read error"),
    AsyncClientChannelClosingError(true, "Client channel close error"),
    BadEncoding(false, "Bad encoding: %s"),
    NonChunckedContentWithoutLength(true, "Content length or chuncked response not specified"),
    ContentLengthNotRecognized(true, "Content length not recognized: %s"),
    ContentSizeIsTooLarge(true, "Content size is too large and content cannot be received whole at once"),
    TransferEncodingUnsupported(true, "Only 'chunked' transfer encoding with whole single content compressing is supported, but server responded: %s"),
    UnsupportedCompressMethods(true, "Only GZIP, Deflate methods or their combinations are supported, but server responded: %s"),
    ByteBufferFillError(true, "Error during fill byte buffer: %s"),
    RequiredSessionHasBeenClosed(false, "Response cannot be processing because corresponding session has been closed"),
    DecompressionError(true, "Error during decompression of response content: %s"),
    SSLError(true, "SSL error"),
    ForkJoinError(true, "Error during fork-join pool multithreading: %s");


    private boolean isFatal;
    private String description;

    Fault(boolean isFatal, String description) {
        this.isFatal = isFatal;
        this.description = description;
    }

    public String format(Object... args) {
        return String.format(description, args);
    }

    public boolean isFatal() {
        return isFatal;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return description;
    }
}
