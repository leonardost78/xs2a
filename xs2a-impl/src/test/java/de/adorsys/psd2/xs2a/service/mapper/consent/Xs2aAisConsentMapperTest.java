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

package de.adorsys.psd2.xs2a.service.mapper.consent;

import de.adorsys.psd2.consent.api.ais.AisAccountAccessInfo;
import de.adorsys.psd2.consent.api.ais.AisAccountConsent;
import de.adorsys.psd2.consent.api.ais.CreateAisConsentRequest;
import de.adorsys.psd2.xs2a.core.consent.ConsentStatus;
import de.adorsys.psd2.xs2a.core.psu.PsuIdData;
import de.adorsys.psd2.xs2a.core.tpp.TppInfo;
import de.adorsys.psd2.xs2a.core.tpp.TppRedirectUri;
import de.adorsys.psd2.xs2a.domain.consent.AccountConsent;
import de.adorsys.psd2.xs2a.domain.consent.CreateConsentReq;
import de.adorsys.psd2.xs2a.domain.consent.UpdateConsentPsuDataReq;
import de.adorsys.psd2.xs2a.domain.consent.Xs2aAccountAccess;
import de.adorsys.psd2.xs2a.service.authorization.processor.model.AuthorisationProcessorResponse;
import de.adorsys.psd2.xs2a.service.mapper.spi_xs2a_mappers.Xs2aToSpiAccountAccessMapper;
import de.adorsys.psd2.xs2a.service.mapper.spi_xs2a_mappers.Xs2aToSpiAccountReferenceMapper;
import de.adorsys.psd2.xs2a.service.mapper.spi_xs2a_mappers.Xs2aToSpiPsuDataMapper;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountConsent;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.xs2a.reader.JsonReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {Xs2aAisConsentMapper.class, Xs2aToSpiPsuDataMapper.class, Xs2aToSpiAccountAccessMapper.class,
    Xs2aToSpiAccountReferenceMapper.class})
class Xs2aAisConsentMapperTest {
    private static final String CONSENT_ID = "c966f143-f6a2-41db-9036-8abaeeef3af7";
    private static final String INTERNAL_REQUEST_ID = "5c2d5564-367f-4e03-a621-6bef76fa4208";

    @Autowired
    private Xs2aAisConsentMapper mapper;
    private JsonReader jsonReader = new JsonReader();

    @Test
    void mapToAccountConsent() {
        AisAccountConsent aisAccountConsent = jsonReader.getObjectFromFile("json/service/mapper/consent/account-consent.json", AisAccountConsent.class);
        AccountConsent expectedAccountConsent = jsonReader.getObjectFromFile("json/service/mapper/consent/xs2a-account-consent.json", AccountConsent.class);

        AccountConsent actualAccountConsent = mapper.mapToAccountConsent(aisAccountConsent);
        assertEquals(expectedAccountConsent, actualAccountConsent);
    }

    @Test
    void mapToAccountConsent_nullValue() {
        AccountConsent actualAccountConsent = mapper.mapToAccountConsent(null);
        assertNull(actualAccountConsent);
    }

    @Test
    void mapToAccountConsentWithNewStatus() {
        AccountConsent accountConsent = jsonReader.getObjectFromFile("json/service/mapper/consent/xs2a-account-consent.json", AccountConsent.class);
        AccountConsent expectedAccountConsent = jsonReader.getObjectFromFile("json/service/mapper/consent/xs2a-account-consent-rejected.json", AccountConsent.class);

        AccountConsent actualAccountConsent = mapper.mapToAccountConsentWithNewStatus(accountConsent, ConsentStatus.REJECTED);
        assertEquals(expectedAccountConsent, actualAccountConsent);
    }

    @Test
    void mapToAccountConsentWithNewStatus_nullValue() {
        AccountConsent actualAccountConsent = mapper.mapToAccountConsentWithNewStatus(null, ConsentStatus.REJECTED);
        assertNull(actualAccountConsent);
    }

    @Test
    void mapToAisAccountAccessInfo() {
        Xs2aAccountAccess xs2aAccountAccess = jsonReader.getObjectFromFile("json/service/mapper/consent/xs2a-account-access.json", Xs2aAccountAccess.class);
        AisAccountAccessInfo expectedAisAccountAccessInfo = jsonReader.getObjectFromFile("json/service/mapper/consent/account-access-info.json", AisAccountAccessInfo.class);

        AisAccountAccessInfo aisAccountAccessInfo = mapper.mapToAisAccountAccessInfo(xs2aAccountAccess);
        assertEquals(expectedAisAccountAccessInfo, aisAccountAccessInfo);
    }

    @Test
    void mapToAisAccountAccessInfo_emptyFields() {
        Xs2aAccountAccess xs2aAccountAccess = jsonReader.getObjectFromFile("json/service/mapper/consent/xs2a-account-access-empty.json", Xs2aAccountAccess.class);
        AisAccountAccessInfo expectedAisAccountAccessInfo = jsonReader.getObjectFromFile("json/service/mapper/consent/account-access-info-empty.json", AisAccountAccessInfo.class);

        AisAccountAccessInfo aisAccountAccessInfo = mapper.mapToAisAccountAccessInfo(xs2aAccountAccess);
        assertEquals(expectedAisAccountAccessInfo, aisAccountAccessInfo);
    }

    @Test
    void mapToSpiScaConfirmation() {
        PsuIdData psuIdData = new PsuIdData("psuId", "", "", "", "");
        UpdateConsentPsuDataReq request = new UpdateConsentPsuDataReq();
        request.setConsentId(CONSENT_ID);
        request.setScaAuthenticationData("123456");

        SpiScaConfirmation spiScaConfirmation = mapper.mapToSpiScaConfirmation(request, psuIdData);

        assertEquals(CONSENT_ID, spiScaConfirmation.getConsentId());
        assertEquals("psuId", spiScaConfirmation.getPsuId());
        assertEquals("123456", spiScaConfirmation.getTanNumber());
    }

    @Test
    void mapToSpiScaConfirmation_psuIdDataIsNull() {
        SpiScaConfirmation spiScaConfirmation = mapper.mapToSpiScaConfirmation(new UpdateConsentPsuDataReq(), null);
        assertNotNull(spiScaConfirmation);
        assertNull(spiScaConfirmation.getPsuId());
    }

    @Test
    void mapToCreateAisConsentRequest() {
        PsuIdData psuData = new PsuIdData("1", "2", "3", "4", "5");
        TppInfo tppInfo = new TppInfo();
        CreateConsentReq request = new CreateConsentReq();
        request.setFrequencyPerDay(3);
        LocalDate validUntil = LocalDate.now();
        request.setValidUntil(validUntil);
        request.setRecurringIndicator(true);
        request.setCombinedServiceIndicator(true);
        TppRedirectUri tppRedirectUri = new TppRedirectUri("12", "13");
        request.setTppRedirectUri(tppRedirectUri);
        request.setAccess(jsonReader.getObjectFromFile("json/service/mapper/consent/xs2a-account-access.json", Xs2aAccountAccess.class));

        AisAccountAccessInfo expectedAisAccountAccessInfo = jsonReader.getObjectFromFile("json/service/mapper/consent/account-access-info.json", AisAccountAccessInfo.class);


        CreateAisConsentRequest createAisConsentRequest = mapper.mapToCreateAisConsentRequest(request, psuData, tppInfo, 34, INTERNAL_REQUEST_ID);

        assertEquals(psuData, createAisConsentRequest.getPsuData());
        assertEquals(tppInfo, createAisConsentRequest.getTppInfo());
        assertEquals(3, createAisConsentRequest.getRequestedFrequencyPerDay());
        assertEquals(34, (int) createAisConsentRequest.getAllowedFrequencyPerDay());
        assertEquals(validUntil, createAisConsentRequest.getValidUntil());
        assertTrue(createAisConsentRequest.isRecurringIndicator());
        assertTrue(createAisConsentRequest.isCombinedServiceIndicator());
        assertEquals(tppRedirectUri, createAisConsentRequest.getTppRedirectUri());
        assertEquals(expectedAisAccountAccessInfo, createAisConsentRequest.getAccess());
    }

    @Test
    void mapToCreateAisConsentRequest_nullValue() {
        PsuIdData psuData = new PsuIdData("1", "2", "3", "4", "5");
        TppInfo tppInfo = new TppInfo();

        CreateAisConsentRequest createAisConsentRequest = mapper.mapToCreateAisConsentRequest(null, psuData, tppInfo, 34, INTERNAL_REQUEST_ID);

        assertNull(createAisConsentRequest);
    }

    @Test
    void mapToSpiAccountConsent() {
        //Given
        AccountConsent accountConsent = jsonReader.getObjectFromFile("json/service/mapper/consent/xs2a-account-consent.json", AccountConsent.class);
        //When
        SpiAccountConsent spiAccountConsent = mapper.mapToSpiAccountConsent(accountConsent);
        SpiAccountConsent spiAccountConsentExpected = jsonReader.getObjectFromFile("json/service/mapper/consent/spi-account-consent.json", SpiAccountConsent.class);
        //Then
        assertEquals(spiAccountConsentExpected, spiAccountConsent);
    }

    @Test
    void mapToSpiUpdateConsentPsuDataReq() {
        UpdateConsentPsuDataReq updateAuthorisationRequest = jsonReader.getObjectFromFile("json/service/mapper/consent/update-consent-psu-data-req.json", UpdateConsentPsuDataReq.class);
        AuthorisationProcessorResponse authorisationProcessorResponse = jsonReader.getObjectFromFile("json/service/mapper/consent/authorisation-processor-response2.json", AuthorisationProcessorResponse.class);

        UpdateConsentPsuDataReq actual = mapper.mapToUpdateConsentPsuDataReq(updateAuthorisationRequest, authorisationProcessorResponse);

        UpdateConsentPsuDataReq expected = jsonReader.getObjectFromFile("json/service/mapper/consent/update-consent-psu-data-req-mapped.json", UpdateConsentPsuDataReq.class);
        assertEquals(expected, actual);
    }

    @Test
    void mapToSpiUpdateConsentPsuDataReq_nullValue() {
        assertNull(mapper.mapToUpdateConsentPsuDataReq(null, null));
    }
}
