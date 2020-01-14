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

package de.adorsys.psd2.xs2a.service.validator.tpp;

import de.adorsys.psd2.xs2a.core.domain.TppMessageInformation;
import de.adorsys.psd2.xs2a.core.tpp.TppInfo;
import de.adorsys.psd2.xs2a.service.TppService;
import de.adorsys.psd2.xs2a.service.profile.AspspProfileServiceWrapper;
import de.adorsys.psd2.xs2a.service.validator.ValidationResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TppDomainValidatorTest {
    private static final String INVALID_DOMAIN_MESSAGE = "TPP URIs are not compliant with the domain secured by the eIDAS QWAC certificate of the TPP in the field CN or SubjectAltName of the certificate";
    private static final String URL_HEADER_CORRECT = "www.example-TPP.com/xs2a-client/v1/ASPSPidentifcation/mytransaction-id";
    private static final String URL_HEADER_CORRECT_WITH_PAGE = "www.example-TPP.com/xs2a-client/super.html";
    private static final String URL_HEADER_CORRECT_DE = "www.example-TPP.de/xs2a-client/v1/ASPSPidentifcation/mytransaction-id";
    private static final String URL_HEADER_SUBDOMAIN_CORRECT = "redirections.example-TPP.com/xs2a-client/v1/ASPSPidentifcation/mytransaction-id";
    private static final String URL_HEADER_WRONG_DOMAIN = "www.bad-example-TPP.com/xs2a-client/v1/ASPSPidentifcation/mytransaction-id";
    private static final String URL_HEADER_WRONG_TLD = "www.example-TPP.bad/xs2a-client/v1/ASPSPidentifcation/mytransaction-id";
    private static final String URL_HEADER_WRONG = "example-TPP";

    private static final String TPP_NAME_DOMAIN = "www.example-TPP.com";
    private static final String TPP_NAME_NON_DOMAIN = "Some bank name";
    private static final String TPP_DNS_DOMAIN = "www.example-TPP.de";
    private static final String TPP_WILDCARD_DOMAIN = "*.example-TPP.de";
    private static final TppMessageInformation TPP_MESSAGE_INFORMATION = TppMessageInformation.buildWarning(INVALID_DOMAIN_MESSAGE);

    @InjectMocks
    private TppDomainValidator tppDomainValidator;
    @Mock
    private TppService tppService;
    @Mock
    private AspspProfileServiceWrapper aspspProfileServiceWrapper;

    @Before
    public void setUp() {
        when(aspspProfileServiceWrapper.isCheckUriComplianceToDomainSupported())
            .thenReturn(true);
    }

    @Test
    public void validate_valid() {
        //Given
        //When
        ValidationResult validate = tppDomainValidator.validate(null);
        //Then
        assertEquals(ValidationResult.valid(), validate);
    }

    @Test
    public void buildWarningMessages_valid() {
        //Given
        when(aspspProfileServiceWrapper.isCheckUriComplianceToDomainSupported())
            .thenReturn(false);
        //When
        Set<TppMessageInformation> validate = tppDomainValidator.buildWarningMessages(URL_HEADER_WRONG_DOMAIN);
        //Then
        assertTrue(validate.isEmpty());
    }

    @Test
    public void buildWarningMessages_NoHeader_Valid() {
        //Given
        //When
        Set<TppMessageInformation> validate = tppDomainValidator.buildWarningMessages(null);
        //Then
        assertTrue(validate.isEmpty());
    }

    @Test
    public void buildWarningMessages_ScaEmbedded_Valid() {
        //Given
        when(tppService.getTppInfo())
            .thenReturn(buildTppInfo(null, null));
        //When
        Set<TppMessageInformation> validate = tppDomainValidator.buildWarningMessages(URL_HEADER_WRONG_DOMAIN);
        //Then
        assertTrue(validate.isEmpty());
    }

    @Test
    public void buildWarningMessages_NoDomainsInTpp_Valid() {
        //Given
        when(tppService.getTppInfo())
            .thenReturn(buildTppInfo(null, null));
        //When
        Set<TppMessageInformation> validate = tppDomainValidator.buildWarningMessages(URL_HEADER_CORRECT);
        //Then
        assertTrue(validate.isEmpty());
    }

    @Test
    public void buildWarningMessages_NotCorrectDomainsInTpp_Valid() {
        //Given
        when(tppService.getTppInfo())
            .thenReturn(buildTppInfo("example-TPP", "dns-example-TPP"));
        //When
        Set<TppMessageInformation> validate = tppDomainValidator.buildWarningMessages(URL_HEADER_CORRECT);
        //Then
        assertTrue(validate.isEmpty());
    }

    @Test
    public void buildWarningMessages_UrlHeaderCorrect_Valid() {
        //Given
        when(tppService.getTppInfo())
            .thenReturn(buildTppInfo(TPP_NAME_DOMAIN, TPP_DNS_DOMAIN));
        //When
        Set<TppMessageInformation> validate = tppDomainValidator.buildWarningMessages(URL_HEADER_CORRECT);
        //Then
        assertTrue(validate.isEmpty());
    }

    @Test
    public void buildWarningMessages_UrlHeaderCorrect_withPage_Valid() {
        //Given
        when(tppService.getTppInfo())
            .thenReturn(buildTppInfo(TPP_NAME_DOMAIN, TPP_DNS_DOMAIN));
        //When
        Set<TppMessageInformation> validate = tppDomainValidator.buildWarningMessages(URL_HEADER_CORRECT_WITH_PAGE);
        //Then
        assertTrue(validate.isEmpty());
    }

    @Test
    public void buildWarningMessages_UrlHeaderSubdomainCorrect_Valid() {
        //Given
        when(tppService.getTppInfo())
            .thenReturn(buildTppInfo(TPP_NAME_DOMAIN, TPP_DNS_DOMAIN));
        //When
        Set<TppMessageInformation> validate = tppDomainValidator.buildWarningMessages(URL_HEADER_SUBDOMAIN_CORRECT);
        //Then
        assertTrue(validate.isEmpty());
    }

    @Test
    public void buildWarningMessages_UrlHeaderSubdomainCorrectTppWildCardDomain_Valid() {
        //Given
        when(tppService.getTppInfo())
            .thenReturn(buildTppInfo(TPP_NAME_DOMAIN, TPP_WILDCARD_DOMAIN));
        //When
        Set<TppMessageInformation> validate = tppDomainValidator.buildWarningMessages(URL_HEADER_SUBDOMAIN_CORRECT);
        //Then
        assertTrue(validate.isEmpty());
    }

    @Test
    public void buildWarningMessages_UrlHeaderWrong_Invalid() {
        //Given
        when(tppService.getTppInfo())
            .thenReturn(buildTppInfo(TPP_NAME_DOMAIN, TPP_DNS_DOMAIN));

        //When
        Set<TppMessageInformation> validate = tppDomainValidator.buildWarningMessages(URL_HEADER_WRONG);
        //Then
        assertFalse(validate.isEmpty());
        assertEquals(TPP_MESSAGE_INFORMATION, validate.iterator().next());
    }

    @Test
    public void buildWarningMessages_UrlHeaderWrongDomain_Invalid() {
        //Given
        when(tppService.getTppInfo())
            .thenReturn(buildTppInfo(TPP_NAME_DOMAIN, TPP_DNS_DOMAIN));

        //When
        Set<TppMessageInformation> validate = tppDomainValidator.buildWarningMessages(URL_HEADER_WRONG_DOMAIN);
        //Then
        assertFalse(validate.isEmpty());
        assertEquals(TPP_MESSAGE_INFORMATION, validate.iterator().next());
    }

    @Test
    public void buildWarningMessages_UrlHeaderWrongTld_Invalid() {
        //Given
        when(tppService.getTppInfo())
            .thenReturn(buildTppInfo(TPP_NAME_DOMAIN, TPP_DNS_DOMAIN));

        //When
        Set<TppMessageInformation> validate = tppDomainValidator.buildWarningMessages(URL_HEADER_WRONG_TLD);
        //Then
        assertFalse(validate.isEmpty());
        assertEquals(TPP_MESSAGE_INFORMATION, validate.iterator().next());
    }

    @Test
    public void buildWarningMessages_nonDomainTppName_valid() {
        //Given
        when(tppService.getTppInfo())
            .thenReturn(buildTppInfo(TPP_NAME_NON_DOMAIN, TPP_DNS_DOMAIN));
        //When
        Set<TppMessageInformation> validate = tppDomainValidator.buildWarningMessages(URL_HEADER_CORRECT_DE);
        //Then
        assertTrue(validate.isEmpty());
    }

    @Test
    public void buildWarningMessages_nonDomainTppName_invalid() {
        //Given
        when(tppService.getTppInfo())
            .thenReturn(buildTppInfo(TPP_NAME_NON_DOMAIN, TPP_DNS_DOMAIN));

        //When
        Set<TppMessageInformation> validate = tppDomainValidator.buildWarningMessages(URL_HEADER_WRONG_DOMAIN);
        //Then
        assertFalse(validate.isEmpty());
        assertEquals(TPP_MESSAGE_INFORMATION, validate.iterator().next());
    }

    private TppInfo buildTppInfo(String name, String dns) {
        TppInfo tppInfo = new TppInfo();
        tppInfo.setTppName(name);
        List<String> dnsList = dns == null
                                   ? Collections.emptyList()
                                   : Collections.singletonList(dns);
        tppInfo.setDnsList(dnsList);

        return tppInfo;
    }
}