package com.techlook.http;

import com.techlook.Fault;
import com.techlook.http.content.ChunkedContentReader;
import com.techlook.http.content.ContentReader;
import com.techlook.http.content.Decoder;
import com.techlook.http.content.WholeContentReader;

import java.nio.charset.Charset;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.techlook.http.content.Decoder.Deflate;
import static com.techlook.http.content.Decoder.GZIP;


public class HttpSession {
    private final static Pattern CHARSET_PATTERN = Pattern.compile("charset\\s*=\\s*([^\\s;]+)");
    private final static String CONTENT_LENGTH = "content-length";
    private final static String TRANSFER_ENCODING = "transfer-encoding";
    private final static String TRANSFER_ENCODING_CHUNCKED = "chunked";
    private final static String CONTENT_ENCODING = "content-encoding";

    private final static Logger log = Logger.getLogger(HttpSession.class.getName());

    private final StringBuilder buffer = new StringBuilder();
    private final Map<String, String> headers = new TreeMap<>();
    private final List<Decoder> decompressMethods = new ArrayList<>(Decoder.values().length);
    private final HttpListener listener;

    private Charset charset;
    private int position = 0;
    private boolean continueParse = true;
    private long contentLength;
    private ContentReader contentReader;


    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    public Charset getCharset() {
        return charset;
    }


    public HttpSession(HttpListener listener) {
        this.listener = listener;
    }

    void read(byte[] chunk) {
        String string;
        string = charset == null ? new String(chunk) : new String(chunk, charset);

        buffer.append(string);
        if (continueParse) {
            int index;

            while ((index = buffer.indexOf("\n", position)) > 0) {
                int residueIndex = index + 1;
                if (buffer.charAt(index - 1) == '\r') {
                    --index;
                }
                String line = buffer.substring(position, index).trim();
                if (line.isEmpty()) {
                    completeResponseHeader(Arrays.copyOfRange(chunk, residueIndex, chunk.length));

                    break;
                } else {
                    pushLine(line);
                }

                position = residueIndex;
            }
        } else {
            contentReader.read(chunk);
        }
    }

    private void completeResponseHeader(byte[] residue) {
        continueParse = false;

        if (headers.containsKey(CONTENT_LENGTH)) {
            String lengthValue = headers.get(CONTENT_LENGTH);
            try {
                contentLength = Long.parseLong(lengthValue);
            } catch (NumberFormatException e) {
                log.log(Level.SEVERE, Fault.ContentLengthNotRecognized.format(lengthValue), e);
                listener.failure(Fault.ContentLengthNotRecognized, lengthValue);
                return;
            }

            if (contentLength > Integer.MAX_VALUE) {
                log.log(Level.SEVERE, Fault.ContentSizeIsTooLarge.getDescription());
                listener.failure(Fault.ContentSizeIsTooLarge);
                return;
            }
            contentReader = new WholeContentReader(listener, charset, (int) contentLength, residue);
        } else if (headers.containsKey(TRANSFER_ENCODING)) {
            String transfer = headers.get(TRANSFER_ENCODING).trim().toLowerCase();

            if (!transfer.equals(TRANSFER_ENCODING_CHUNCKED)) {
                log.log(Level.SEVERE, Fault.TransferEncodingUnsupported.format(transfer));
                listener.failure(Fault.TransferEncodingUnsupported, transfer);
                return;
            }

            contentReader = new ChunkedContentReader(listener, charset, residue);
        } else {
            log.log(Level.SEVERE, Fault.NonChunckedContentWithoutLength.getDescription());
            listener.failure(Fault.NonChunckedContentWithoutLength);
            return;
        }

        if (headers.containsKey(CONTENT_ENCODING)) {
            String contentEncoding = headers.get(CONTENT_ENCODING);
            String[] compressMethods = contentEncoding.split(",");

            for (String method : compressMethods) {
                if (GZIP.matchToken(method)) {
                    decompressMethods.add(GZIP);
                } else if (Deflate.matchToken(method)) {
                    decompressMethods.add(Deflate);
                } else {
                    log.log(Level.SEVERE, Fault.UnsupportedCompressMethods.format(contentEncoding));
                    listener.failure(Fault.UnsupportedCompressMethods, contentEncoding);
                    return;
                }
            }

            contentReader.setDecoders(decompressMethods);
        }
    }

    private void pushLine(String line) {
        int index = line.indexOf(':');
        if (index < 0) {
            return;
        }

        String key = line.substring(0, index).trim().toLowerCase();
        String value = line.substring(index + 1, line.length()).trim();

        Matcher matcher = CHARSET_PATTERN.matcher(value);
        if (matcher.find()) {
            String charsetValue = matcher.group(1);

            if (charsetValue != null) try {
                charset = Charset.forName(charsetValue);
            } catch (Exception e) {
                log.log(Level.WARNING, Fault.BadEncoding.format(charsetValue), e);
                listener.failure(Fault.BadEncoding, charsetValue);
            }
        }

        headers.put(key, value);
    }
}
