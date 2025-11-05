package no.entur.abt.bob.api.pm.client;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import no.entur.abt.bob.api.pm.model.JwkPublic;
import no.entur.abt.bob.api.pm.model.Jws;
import no.entur.abt.bob.api.pm.model.ParticipantMetadata;

public class MetadataTest {

	@Test
	public void parseResponse() throws IOException {
		// parse ParticipantMetadataFileApi
		ObjectMapper mapper = new ObjectMapper();

		InputStream is = getClass().getResourceAsStream("/participantMetadataJws.json");

		Jws jws = mapper.reader().readValue(is, Jws.class);

		String payload = jws.getPayload();

		byte[] decode = Base64.getDecoder().decode(payload);

		List<ParticipantMetadata> participantMetadata = Arrays.asList(mapper.readValue(decode, ParticipantMetadata[].class));

		List<JwkPublic> mtbPublicKeys = participantMetadata.get(0).getMtbPublicKeys();

		assertFalse(mtbPublicKeys.isEmpty());
	}

}
