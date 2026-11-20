package no.entur.abt.bob.api.pm.client;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.apache.hc.core5.http.HttpStatus;

/**
 * Response from {@linkplain ParticipantMetadataFileClient}: the participant metadata file (a JWS) as received, unless not modified, and the
 * headers needed for caching.
 */

public class ParticipantMetadataFileResponse {

	private final int statusCode;
	private final byte[] body;
	private final String etag;
	private final String cacheControl;
	private final Duration maxAge;
	private final Instant date;

	public ParticipantMetadataFileResponse(int statusCode, byte[] body, String etag, String cacheControl, Duration maxAge, Instant date) {
		this.statusCode = statusCode;
		this.body = body;
		this.etag = etag;
		this.cacheControl = cacheControl;
		this.maxAge = maxAge;
		this.date = date;
	}

	/**
	 * @return <code>200</code> or <code>304</code>
	 */

	public int getStatusCode() {
		return statusCode;
	}

	/**
	 * @return true if a new file was downloaded (<code>200</code>), false if not modified (<code>304</code>)
	 */

	public boolean isModified() {
		return statusCode == HttpStatus.SC_OK;
	}

	/**
	 * @return the JWS, byte for byte as received, or null if not modified
	 */

	public byte[] getBody() {
		return body;
	}

	/**
	 * @return the JWS as UTF-8 text, or null if not modified
	 */

	public String getBodyAsString() {
		return body != null ? new String(body, StandardCharsets.UTF_8) : null;
	}

	/**
	 * @return the <code>ETag</code> header exactly as received (including quotes), for the next request; or null if not present
	 */

	public String getEtag() {
		return etag;
	}

	/**
	 * @return the <code>Cache-Control</code> header, or null if not present
	 */

	public String getCacheControl() {
		return cacheControl;
	}

	/**
	 * @return the <code>max-age</code> directive of the <code>Cache-Control</code> header, or null if not present
	 */

	public Duration getMaxAge() {
		return maxAge;
	}

	/**
	 * @return the <code>Date</code> header, or null if not present
	 */

	public Instant getDate() {
		return date;
	}

	@Override
	public String toString() {
		return "ParticipantMetadataFileResponse[statusCode=" + statusCode + ", bodyLength=" + (body != null ? body.length : 0) + ", etag=" + etag
				+ ", cacheControl=" + cacheControl + ", date=" + date + "]";
	}

}
