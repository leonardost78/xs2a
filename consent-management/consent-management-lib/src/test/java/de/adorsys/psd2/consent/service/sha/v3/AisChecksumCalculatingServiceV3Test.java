/*
 * Copyright 2018-2020 adorsys GmbH & Co KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.adorsys.psd2.consent.service.sha.v3;

import de.adorsys.psd2.consent.service.sha.ChecksumConstant;
import de.adorsys.psd2.core.data.Consent;
import de.adorsys.psd2.core.data.ais.AisConsent;
import de.adorsys.psd2.xs2a.core.consent.ConsentType;
import de.adorsys.xs2a.reader.JsonReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AisChecksumCalculatingServiceV3Test {
    private static final String VERSION_03 = "003";
    private static final byte[] WRONG_CHECKSUM = "checksum in consent".getBytes();
    private static final byte[] WRONG_CHECKSUM_WITH_DELIMITER = ("checksum in consent" + ChecksumConstant.DELIMITER).getBytes();
    private static final byte[] WRONG_CHECKSUM_WITH_2_PARTS = ("checksum in consent" + ChecksumConstant.DELIMITER+"second part==").getBytes();
    private static final byte[] CHECKSUM_TPP_ACCESS_IBAN = getCorrectChecksumForTppAccessIban().getBytes();
    private static final byte[] CHECKSUM_ASPSP_ACCESS_IBAN = getCorrectChecksumForAspspAccessIban().getBytes();

    private static final byte[] CHECKSUM_TPP_ACCESS_IBAN_MASKEDPAN = getCorrectChecksumForTppAccessIbanAndMaskedPan().getBytes();
    private static final byte[] CHECKSUM_ASPSP_ACCESS_IBAN_MASKEDPAN = getCorrectChecksumForAspspAccessIbanAndMaskedPan().getBytes();
    private JsonReader jsonReader = new JsonReader();

    private final AisChecksumCalculatingServiceV3 aisChecksumCalculatingServiceV3 = new AisChecksumCalculatingServiceV3();

    @Test
    void verifyConsentWithChecksum_success_tppAccesses() {
        // given
        AisConsent aisConsent = buildConsentTppIban();

        // when
        boolean actualResult = aisChecksumCalculatingServiceV3.verifyConsentWithChecksum(aisConsent, CHECKSUM_TPP_ACCESS_IBAN);

        // then
        assertTrue(actualResult);
    }

    @Test
    void verifyConsentWithChecksum_success_aspspAccesses() {
        // given
        AisConsent aisConsent = buildConsentAspspIban();

        // when
        boolean actualResult = aisChecksumCalculatingServiceV3.verifyConsentWithChecksum(aisConsent, CHECKSUM_ASPSP_ACCESS_IBAN);

        // then
        assertTrue(actualResult);
    }

    @Test
    void verifyConsentWithChecksum_wrongChecksum() {
        // given
        AisConsent aisConsent = buildConsentTppIban();

        // when
        boolean actualResult = aisChecksumCalculatingServiceV3.verifyConsentWithChecksum(aisConsent, WRONG_CHECKSUM);

        // then
        assertFalse(actualResult);
    }

    @Test
    void verifyConsentWithChecksum_wrongChecksumWithDelimiter() {
        // given
        AisConsent aisConsent = buildConsentTppIban();

        // when
        boolean actualResult = aisChecksumCalculatingServiceV3.verifyConsentWithChecksum(aisConsent, WRONG_CHECKSUM_WITH_DELIMITER);

        // then
        assertFalse(actualResult);
    }

    @Test
    void verifyConsentWithChecksum_wrongChecksumWithTwoParts() {
        // given
        AisConsent aisConsent = buildConsentTppIban();

        // when
        boolean actualResult = aisChecksumCalculatingServiceV3.verifyConsentWithChecksum(aisConsent, WRONG_CHECKSUM_WITH_2_PARTS);

        // then
        assertFalse(actualResult);
    }

    @Test
    void calculateChecksumForConsent_success() {
        // given
        AisConsent aisConsent = buildConsentTppIban();

        // when
        byte[] actualResult = aisChecksumCalculatingServiceV3.calculateChecksumForConsent(aisConsent);

        // then
        assertArrayEquals(CHECKSUM_TPP_ACCESS_IBAN, actualResult);
    }

    @Test
    void calculateChecksumForConsent_success_aspspAccesses() {
        // given
        AisConsent aisConsent = buildConsentAspspIban();

        // when
        byte[] actualResult = aisChecksumCalculatingServiceV3.calculateChecksumForConsent(aisConsent);

        // then
        assertArrayEquals(CHECKSUM_ASPSP_ACCESS_IBAN, actualResult);
    }

    @Test
    void verifyConsentWithChecksum_success_tppAccesses_IbanAndMaskedPan() {
        // given
        AisConsent aisConsent = buildConsentTppIbanAndMaskedPan();

        // when
        boolean actualResult = aisChecksumCalculatingServiceV3.verifyConsentWithChecksum(aisConsent, CHECKSUM_TPP_ACCESS_IBAN_MASKEDPAN);

        // then
        assertTrue(actualResult);
    }

    @Test
    void verifyConsentWithChecksum_success_aspspAccesses_IbanAndMaskedPan() {
        // given
        AisConsent aisConsent = buildConsentAspspIbanAndMaskedPan();

        // when
        boolean actualResult = aisChecksumCalculatingServiceV3.verifyConsentWithChecksum(aisConsent, CHECKSUM_ASPSP_ACCESS_IBAN_MASKEDPAN);

        // then
        assertTrue(actualResult);
    }

    @Test
    void calculateChecksumForConsent_successIbanAndMaskedPan() {
        // given
        AisConsent aisConsent = buildConsentTppIbanAndMaskedPan();

        // when
        byte[] actualResult = aisChecksumCalculatingServiceV3.calculateChecksumForConsent(aisConsent);

        // then
        assertArrayEquals(CHECKSUM_TPP_ACCESS_IBAN_MASKEDPAN, actualResult);
    }

    @Test
    void calculateChecksumForConsent_success_aspspAccessesIbanAndMaskedPan() {
        // given
        AisConsent aisConsent = buildConsentAspspIbanAndMaskedPan();

        // when
        byte[] actualResult = aisChecksumCalculatingServiceV3.calculateChecksumForConsent(aisConsent);

        // then
        assertArrayEquals(CHECKSUM_ASPSP_ACCESS_IBAN_MASKEDPAN, actualResult);
    }

    @Test
    void verifyConsentWithChecksum_consent_is_null() {
        // when
        boolean actualResult = aisChecksumCalculatingServiceV3.verifyConsentWithChecksum(null, CHECKSUM_ASPSP_ACCESS_IBAN);

        // then
        assertFalse(actualResult);
    }

    @Test
    void verifyConsentWithChecksum_checksum_is_null() {
        // given
        AisConsent aisConsent = buildConsentAspspIbanAndMaskedPan();
        // when
        boolean actualResult = aisChecksumCalculatingServiceV3.verifyConsentWithChecksum(aisConsent, null);

        // then
        assertFalse(actualResult);
    }

    @Test
    void verifyConsentWithChecksum_consent_is_unknown_object() {
        // given
        // when
        boolean actualResult = aisChecksumCalculatingServiceV3.verifyConsentWithChecksum(new TestObject(), CHECKSUM_ASPSP_ACCESS_IBAN);

        // then
        assertFalse(actualResult);
    }

    @Test
    void calculateChecksumForConsent_consent_is_null() {
        // given
        byte[] emptyArray = new byte[0];
        // when
        byte[] actualResult = aisChecksumCalculatingServiceV3.calculateChecksumForConsent(null);

        // then
        assertArrayEquals(actualResult, emptyArray);
    }

    @Test
    void calculateChecksumForConsent_consent_is_unknown_object() {
        // given
        byte[] emptyArray = new byte[0];
        // when
        byte[] actualResult = aisChecksumCalculatingServiceV3.calculateChecksumForConsent(new TestObject());

        // then
        assertArrayEquals(actualResult, emptyArray);
    }

    @Test
    void getVersion() {
        // when
        String actualResult = aisChecksumCalculatingServiceV3.getVersion();

        //then
        assertEquals(VERSION_03, actualResult);
    }

    private AisConsent buildConsentTppIban() {
        return jsonReader.getObjectFromFile("json/dedicated-ais-consent_tpp_access.json", AisConsent.class);
    }

    private AisConsent buildConsentAspspIban() {
        return jsonReader.getObjectFromFile("json/dedicated-ais-consent_aspsp_access.json", AisConsent.class);
    }

    private static String getCorrectChecksumForTppAccessIban() {
        return "003_%_Mj7nHAyka6LdA3zDA0e4TQU0283X1iFDGyJSRePNNpaRlHeuAG5a24vid4K/5jNIWyTsaEo4JZSPmYkiSJ5YoA==";
    }

    private static String getCorrectChecksumForAspspAccessIban() {
        return "003_%_Mj7nHAyka6LdA3zDA0e4TQU0283X1iFDGyJSRePNNpaRlHeuAG5a24vid4K/5jNIWyTsaEo4JZSPmYkiSJ5YoA==_%_eyJpYmFuIjoidDg2OTRsdXd1RUkvQTRQM1NvYkh5c0NhMVRqdjJFbEk4cXltWjkwK3duN2o4cXdMcnBOck5VQWFpbWF2RlZ6OE0vZEhFbUlsbzZJNEZ5VGpaNUdIU3c9PSJ9";
    }

    private AisConsent buildConsentTppIbanAndMaskedPan() {
        return jsonReader.getObjectFromFile("json/dedicated-ais-consent_tpp_access_iban&maskedpan.json", AisConsent.class);
    }

    private AisConsent buildConsentAspspIbanAndMaskedPan() {
        return jsonReader.getObjectFromFile("json/dedicated-ais-consent_aspsp_access_iban&maskedpan.json", AisConsent.class);
    }

    private static String getCorrectChecksumForTppAccessIbanAndMaskedPan() {
        return "003_%_dsuFMYCrZd1YWY7+3/zF7mgrO0PFjhkHn9foi2ylWZOzCWRaUBXNBXkllfmnQ8JXLFEZk3Ta7l+jbdRHHkYT0Q==";
    }

    private static String getCorrectChecksumForAspspAccessIbanAndMaskedPan() {
        return "003_%_dsuFMYCrZd1YWY7+3/zF7mgrO0PFjhkHn9foi2ylWZOzCWRaUBXNBXkllfmnQ8JXLFEZk3Ta7l+jbdRHHkYT0Q==_%_eyJpYmFuIjoidDg2OTRsdXd1RUkvQTRQM1NvYkh5c0NhMVRqdjJFbEk4cXltWjkwK3duN2o4cXdMcnBOck5VQWFpbWF2RlZ6OE0vZEhFbUlsbzZJNEZ5VGpaNUdIU3c9PSIsIm1hc2tlZFBhbiI6Ild6TG9rYjM1cXFaMElkcVdFZ09PSEtDSEtFMVg1dDY1amxQMURRREJ1UkQya2VJUDVrYmhUMFRKQ3YwWFQ0Sk9ueGxkYWljTzY2Tk9ZcFBsY1JhdmhnPT0ifQ==";
    }

    private class TestObject extends Consent {
        @Override
        public ConsentType getConsentType() {
            return null;
        }
    }

}
