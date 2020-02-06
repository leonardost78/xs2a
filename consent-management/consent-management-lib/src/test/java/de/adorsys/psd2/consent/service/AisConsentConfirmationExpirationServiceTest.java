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

package de.adorsys.psd2.consent.service;

import de.adorsys.psd2.aspsp.profile.domain.AspspSettings;
import de.adorsys.psd2.aspsp.profile.domain.ais.AisAspspProfileSetting;
import de.adorsys.psd2.aspsp.profile.domain.ais.ConsentTypeSetting;
import de.adorsys.psd2.aspsp.profile.service.AspspProfileService;
import de.adorsys.psd2.consent.domain.account.AisConsent;
import de.adorsys.psd2.consent.repository.AisConsentJpaRepository;
import de.adorsys.psd2.consent.repository.AuthorisationRepository;
import de.adorsys.psd2.xs2a.core.consent.ConsentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AisConsentConfirmationExpirationServiceTest {
    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);
    private static final LocalDate TODAY = LocalDate.now();

    @InjectMocks
    private AisConsentConfirmationExpirationServiceImpl expirationService;

    @Mock
    private AisConsentJpaRepository aisConsentJpaRepository;
    @Mock
    private AuthorisationRepository authorisationRepository;
    @Mock
    private AspspProfileService aspspProfileService;

    @Test
    void expireConsent() {
        // Given
        ArgumentCaptor<AisConsent> aisConsentCaptor = ArgumentCaptor.forClass(AisConsent.class);
        // When
        expirationService.expireConsent(new AisConsent());
        // Then
        verify(aisConsentJpaRepository).save(aisConsentCaptor.capture());

        AisConsent aisConsent = aisConsentCaptor.getValue();
        assertEquals(ConsentStatus.EXPIRED, aisConsent.getConsentStatus());
        assertEquals(TODAY, aisConsent.getExpireDate());
        assertEquals(TODAY, aisConsent.getLastActionDate());
    }

    @Test
    void updateConsentOnConfirmationExpiration() {
        // Given
        ArgumentCaptor<AisConsent> aisConsentCaptor = ArgumentCaptor.forClass(AisConsent.class);

        // When
        expirationService.updateOnConfirmationExpiration(buildAisConsent(ConsentStatus.RECEIVED, TOMORROW));

        // Then
        verify(aisConsentJpaRepository).save(aisConsentCaptor.capture());
        assertEquals(ConsentStatus.REJECTED, aisConsentCaptor.getValue().getConsentStatus());
    }

    @Test
    void checkAndUpdateOnConfirmationExpiration_expired() {
        // Given
        ArgumentCaptor<AisConsent> aisConsentCaptor = ArgumentCaptor.forClass(AisConsent.class);
        when(aspspProfileService.getAspspSettings()).thenReturn(buildAspspSettings(100L));

        AisConsent consent = buildAisConsent(ConsentStatus.RECEIVED, TOMORROW);
        consent.setCreationTimestamp(OffsetDateTime.now().minusHours(1));

        // When
        expirationService.checkAndUpdateOnConfirmationExpiration(consent);

        // Then
        verify(aisConsentJpaRepository).save(aisConsentCaptor.capture());
        assertEquals(ConsentStatus.REJECTED, aisConsentCaptor.getValue().getConsentStatus());
    }

    @Test
    void checkAndUpdateOnConfirmationExpiration_nonExpired() {
        // Given
        when(aspspProfileService.getAspspSettings()).thenReturn(buildAspspSettings(86400L));

        AisConsent consent = buildAisConsent(ConsentStatus.RECEIVED, TOMORROW);
        // When
        AisConsent actual = expirationService.checkAndUpdateOnConfirmationExpiration(consent);

        // Then
        assertEquals(consent, actual);
    }

    @Test
    void updateConsentListOnConfirmationExpiration() {
        // Given
        ArgumentCaptor<List<AisConsent>> aisConsentListCaptor = ArgumentCaptor.forClass(List.class);

        // When
        expirationService.updateConsentListOnConfirmationExpiration(Collections.singletonList(buildAisConsent(ConsentStatus.RECEIVED, TOMORROW)));

        // Then
        verify(aisConsentJpaRepository).saveAll(aisConsentListCaptor.capture());
        assertEquals(ConsentStatus.REJECTED, aisConsentListCaptor.getValue().get(0).getConsentStatus());
    }

    private AisConsent buildAisConsent(ConsentStatus consentStatus, LocalDate validUntil) {
        AisConsent aisConsent = new AisConsent();
        aisConsent.setConsentStatus(consentStatus);
        aisConsent.setValidUntil(validUntil);
        return aisConsent;
    }

    private AspspSettings buildAspspSettings(Long notConfirmedConsentExpirationTimeMs) {
        return new AspspSettings(new AisAspspProfileSetting(new ConsentTypeSetting(false, false, false, 0, notConfirmedConsentExpirationTimeMs, 0, false), null, null, null, null), null, null, null);
    }
}
