package no.entur.abt.bob.api.pm.client;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicHeaderValueParser;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.util.Timeout;

/**
 * Client for the signed participant metadata file (<code>GET /api/v2/participantMetadata</code>), supporting conditional requests.
 * <p>
 * The response body (a JWS) is returned as received, without parsing or verifying it, together with the headers needed for caching. Pass the
 * <code>ETag</code> of the last downloaded file to get a <code>304 Not Modified</code> response when the file has not changed.
 * <p>
 * Each request uses its own HTTP client, which is closed afterwards, as the file is only fetched a few times a day. Proxy and TLS settings are
 * taken from the standard system properties.
 * <p>
 * Instances are thread safe.
 */

public class ParticipantMetadataFileClient {

	public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
	public static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofSeconds(60);

	private static final String MAX_AGE = "max-age";

	private final URI uri;
	private final Timeout connectTimeout;
	private final Timeout responseTimeout;

	/**
	 * Use the default timeouts.
	 *
	 * @param uri location of the participant metadata file, for example <code>https://bobmetadata.samtrafiken.se/api/v2/participantMetadata</code>
	 */

	public ParticipantMetadataFileClient(URI uri) {
		this(uri, DEFAULT_CONNECT_TIMEOUT, DEFAULT_RESPONSE_TIMEOUT);
	}

	/**
	 * @param uri             location of the participant metadata file, for example
	 *                        <code>https://bobmetadata.samtrafiken.se/api/v2/participantMetadata</code>
	 * @param connectTimeout  timeout for establishing a connection
	 * @param responseTimeout timeout for waiting for data from the server
	 */

	public ParticipantMetadataFileClient(URI uri, Duration connectTimeout, Duration responseTimeout) {
		if (uri == null) {
			throw new IllegalArgumentException("Expected uri");
		}
		if (connectTimeout == null || responseTimeout == null) {
			throw new IllegalArgumentException("Expected timeouts");
		}
		this.uri = uri;
		this.connectTimeout = Timeout.of(connectTimeout);
		this.responseTimeout = Timeout.of(responseTimeout);
	}

	/**
	 * Get the participant metadata file unconditionally.
	 *
	 * @return the file and its caching headers
	 * @throws HttpResponseException if the status code was not <code>200</code>
	 * @throws IOException           if the request failed
	 */

	public ParticipantMetadataFileResponse get() throws IOException {
		return get(null);
	}

	/**
	 * Get the participant metadata file, if it has changed.
	 *
	 * @param etag <code>ETag</code> of the last downloaded file, exactly as received (including quotes), or null to get the file unconditionally
	 * @return the file and its caching headers, or no file if not modified
	 * @throws HttpResponseException if the status code was not <code>200</code> or <code>304</code>
	 * @throws IOException           if the request failed
	 */

	public ParticipantMetadataFileResponse get(String etag) throws IOException {
		HttpGet request = new HttpGet(uri);
		request.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
		if (etag != null) {
			request.setHeader(HttpHeaders.IF_NONE_MATCH, etag);
		}
		try (CloseableHttpClient httpClient = createHttpClient()) {
			return httpClient.execute(request, response -> handle(response, etag));
		}
	}

	private CloseableHttpClient createHttpClient() {
		ConnectionConfig connectionConfig = ConnectionConfig.custom().setConnectTimeout(connectTimeout).setSocketTimeout(responseTimeout).build();
		RequestConfig requestConfig = RequestConfig.custom().setResponseTimeout(responseTimeout).build();

		return HttpClients.custom()
				.setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create().useSystemProperties().setDefaultConnectionConfig(connectionConfig).build())
				.setDefaultRequestConfig(requestConfig)
				.useSystemProperties()
				.build();
	}

	static ParticipantMetadataFileResponse handle(ClassicHttpResponse response, String requestEtag) throws IOException {
		int statusCode = response.getCode();

		String cacheControl = getHeaders(response, HttpHeaders.CACHE_CONTROL);
		Duration maxAge = parseMaxAge(cacheControl);
		String dateHeader = getHeaders(response, HttpHeaders.DATE);
		Instant date = dateHeader != null ? DateUtils.parseStandardDate(dateHeader) : null;
		String etag = getHeaders(response, HttpHeaders.ETAG);

		if (statusCode == HttpStatus.SC_OK) {
			byte[] body = response.getEntity() != null ? EntityUtils.toByteArray(response.getEntity()) : null;
			if (body == null || body.length == 0) {
				throw new ClientProtocolException("Expected participant metadata file in response body");
			}
			return new ParticipantMetadataFileResponse(statusCode, body, etag, cacheControl, maxAge, date);
		}

		EntityUtils.consume(response.getEntity());

		if (statusCode == HttpStatus.SC_NOT_MODIFIED) {
			// a 304 response should repeat the ETag, but fall back to the one sent
			return new ParticipantMetadataFileResponse(statusCode, null, etag != null ? etag : requestEtag, cacheControl, maxAge, date);
		}

		throw new HttpResponseException(statusCode, response.getReasonPhrase());
	}

	private static String getHeaders(ClassicHttpResponse response, String name) {
		Header[] headers = response.getHeaders(name);
		if (headers.length == 0) {
			return null;
		}
		StringBuilder builder = new StringBuilder();
		for (Header header : headers) {
			if (builder.length() > 0) {
				builder.append(", ");
			}
			builder.append(header.getValue());
		}
		return builder.toString();
	}

	/**
	 * @param cacheControl value of the <code>Cache-Control</code> header, or null
	 * @return the <code>max-age</code> directive, or null if not present or invalid
	 */

	static Duration parseMaxAge(String cacheControl) {
		if (cacheControl == null) {
			return null;
		}
		// the parser handles quoted values, which may contain commas
		HeaderElement[] directives = BasicHeaderValueParser.INSTANCE.parseElements(cacheControl, new ParserCursor(0, cacheControl.length()));
		for (HeaderElement directive : directives) {
			if (!MAX_AGE.equalsIgnoreCase(directive.getName())) {
				continue;
			}
			if (directive.getValue() == null) {
				return null;
			}
			try {
				long seconds = Long.parseLong(directive.getValue().trim());
				return seconds >= 0 ? Duration.ofSeconds(seconds) : null;
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

}
