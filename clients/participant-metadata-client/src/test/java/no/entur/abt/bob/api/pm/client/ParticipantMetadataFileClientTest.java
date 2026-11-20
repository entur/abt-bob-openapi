package no.entur.abt.bob.api.pm.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Test;

public class ParticipantMetadataFileClientTest {

	private static final String ETAG = "\"382520493\"";
	private static final String DATE = "Fri, 20 Nov 2026 14:51:01 GMT";

	@Test
	public void returnsFileAndHeaders() throws IOException {
		byte[] jws = read("/participantMetadataJws.json");

		BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
		response.setEntity(new ByteArrayEntity(jws, ContentType.APPLICATION_JSON));
		response.addHeader("ETag", ETAG);
		response.addHeader("Cache-Control", "max-age=3600");
		response.addHeader("Date", DATE);

		ParticipantMetadataFileResponse result = ParticipantMetadataFileClient.handle(response, null);

		assertTrue(result.isModified());
		assertEquals(200, result.getStatusCode());
		assertArrayEquals(jws, result.getBody());
		assertEquals(new String(jws, StandardCharsets.UTF_8), result.getBodyAsString());
		assertEquals(ETAG, result.getEtag());
		assertEquals("max-age=3600", result.getCacheControl());
		assertEquals(Duration.ofHours(1), result.getMaxAge());
		assertEquals(Instant.parse("2026-11-20T14:51:01Z"), result.getDate());
	}

	@Test
	public void returnsNotModified() throws IOException {
		BasicClassicHttpResponse response = new BasicClassicHttpResponse(304);
		response.addHeader("ETag", ETAG);
		response.addHeader("Cache-Control", "max-age=3600");

		ParticipantMetadataFileResponse result = ParticipantMetadataFileClient.handle(response, "\"1\"");

		assertFalse(result.isModified());
		assertEquals(304, result.getStatusCode());
		assertNull(result.getBody());
		assertNull(result.getBodyAsString());
		assertEquals(ETAG, result.getEtag());
		assertEquals(Duration.ofHours(1), result.getMaxAge());
		assertNull(result.getDate());
	}

	@Test
	public void notModifiedWithoutEtagKeepsRequestEtag() throws IOException {
		ParticipantMetadataFileResponse result = ParticipantMetadataFileClient.handle(new BasicClassicHttpResponse(304), ETAG);

		assertFalse(result.isModified());
		assertEquals(ETAG, result.getEtag());
		assertNull(result.getCacheControl());
		assertNull(result.getMaxAge());
	}

	@Test
	public void throwsForMissingBody() {
		assertThrows(IOException.class, () -> ParticipantMetadataFileClient.handle(new BasicClassicHttpResponse(200), null));
	}

	@Test
	public void throwsForUnexpectedStatusCode() {
		BasicClassicHttpResponse response = new BasicClassicHttpResponse(409);
		response.setEntity(new ByteArrayEntity("busy".getBytes(StandardCharsets.UTF_8), ContentType.TEXT_PLAIN));

		HttpResponseException e = assertThrows(HttpResponseException.class, () -> ParticipantMetadataFileClient.handle(response, ETAG));

		assertEquals(409, e.getStatusCode());
	}

	@Test
	public void parsesMaxAge() {
		assertEquals(Duration.ofSeconds(3600), ParticipantMetadataFileClient.parseMaxAge("max-age=3600"));
		assertEquals(Duration.ofSeconds(60), ParticipantMetadataFileClient.parseMaxAge("public, MAX-AGE = 60, must-revalidate"));
		assertEquals(Duration.ofSeconds(60), ParticipantMetadataFileClient.parseMaxAge("s-maxage=10, max-age=\"60\""));
		assertEquals(Duration.ZERO, ParticipantMetadataFileClient.parseMaxAge("no-cache, max-age=0"));

		assertNull(ParticipantMetadataFileClient.parseMaxAge(null));
		assertNull(ParticipantMetadataFileClient.parseMaxAge("no-store"));
		assertNull(ParticipantMetadataFileClient.parseMaxAge("max-age=abc"));
		assertNull(ParticipantMetadataFileClient.parseMaxAge("max-age=-1"));
	}

	private byte[] read(String resource) throws IOException {
		try (InputStream in = getClass().getResourceAsStream(resource)) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
			return out.toByteArray();
		}
	}

}
