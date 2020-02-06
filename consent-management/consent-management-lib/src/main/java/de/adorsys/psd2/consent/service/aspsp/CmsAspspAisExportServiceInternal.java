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

package de.adorsys.psd2.consent.service.aspsp;

import de.adorsys.psd2.consent.api.ais.CmsAisAccountConsent;
import de.adorsys.psd2.consent.aspsp.api.ais.CmsAspspAisExportService;
import de.adorsys.psd2.consent.domain.AuthorisationEntity;
import de.adorsys.psd2.consent.domain.consent.ConsentEntity;
import de.adorsys.psd2.consent.repository.AuthorisationRepository;
import de.adorsys.psd2.consent.repository.ConsentJpaRepository;
import de.adorsys.psd2.consent.repository.specification.AisConsentSpecification;
import de.adorsys.psd2.consent.service.mapper.AisConsentMapper;
import de.adorsys.psd2.xs2a.core.authorisation.AuthorisationType;
import de.adorsys.psd2.xs2a.core.psu.PsuIdData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CmsAspspAisExportServiceInternal implements CmsAspspAisExportService {
    private final AisConsentSpecification aisConsentSpecification;
    private final ConsentJpaRepository consentJpaRepository;
    private final AisConsentMapper aisConsentMapper;
    private final AuthorisationRepository authorisationRepository;
    private final ConsentFilteringService consentFilteringService;

    @Override
    public Collection<CmsAisAccountConsent> exportConsentsByTpp(String tppAuthorisationNumber,
                                                                @Nullable LocalDate createDateFrom,
                                                                @Nullable LocalDate createDateTo,
                                                                @Nullable PsuIdData psuIdData, @NotNull String instanceId) {
        if (StringUtils.isBlank(tppAuthorisationNumber) || StringUtils.isBlank(instanceId)) {
            log.info("TPP ID: [{}], InstanceId: [{}]. Export Consents by TPP: Some of these two values are empty", tppAuthorisationNumber, instanceId);
            return Collections.emptyList();
        }

        return consentJpaRepository.findAll(aisConsentSpecification.byTppIdAndCreationPeriodAndPsuIdDataAndInstanceId(
            tppAuthorisationNumber,
            createDateFrom,
            createDateTo,
            psuIdData,
            instanceId
        ))
                   .stream()
                   .map(this::mapToCmsAisAccountConsentWithAuthorisations)
                   .collect(Collectors.toList());
    }

    @Override
    public Collection<CmsAisAccountConsent> exportConsentsByPsu(PsuIdData psuIdData, @Nullable LocalDate createDateFrom,
                                                                @Nullable LocalDate createDateTo,
                                                                @NotNull String instanceId) {
        if (psuIdData == null || psuIdData.isEmpty() || StringUtils.isBlank(instanceId)) {
            log.info("InstanceId: [{}]. Export consents by Psu failed, psuIdData or instanceId is empty or null.",
                     instanceId);
            return Collections.emptyList();
        }

        return consentJpaRepository.findAll(aisConsentSpecification.byPsuIdDataAndCreationPeriodAndInstanceId(psuIdData,
                                                                                                              createDateFrom,
                                                                                                              createDateTo,
                                                                                                              instanceId
        ))
                   .stream()
                   .map(this::mapToCmsAisAccountConsentWithAuthorisations)
                   .collect(Collectors.toList());
    }

    @Override
    public Collection<CmsAisAccountConsent> exportConsentsByAccountId(@NotNull String aspspAccountId,
                                                                      @Nullable LocalDate createDateFrom,
                                                                      @Nullable LocalDate createDateTo,
                                                                      @NotNull String instanceId) {

        if (StringUtils.isBlank(instanceId)) {
            log.info("InstanceId: [{}], aspspAccountId: [{}]. Export consents by accountId failed, instanceId is empty or null.",
                     instanceId, aspspAccountId);
            return Collections.emptyList();
        }

        List<ConsentEntity> consents = consentJpaRepository.findAll(aisConsentSpecification.byCreationPeriodAndInstanceId(createDateFrom,
                                                                                                                          createDateTo,
                                                                                                                          instanceId));
        List<ConsentEntity> filteredConsents = consentFilteringService.filterAisConsentsByAspspAccountId(consents, aspspAccountId);
        return filteredConsents
                   .stream()
                   .map(this::mapToCmsAisAccountConsentWithAuthorisations)
                   .collect(Collectors.toList());
    }

    private CmsAisAccountConsent mapToCmsAisAccountConsentWithAuthorisations(ConsentEntity aisConsentEntity) {
        List<AuthorisationEntity> authorisations =
            authorisationRepository.findAllByParentExternalIdAndAuthorisationType(aisConsentEntity.getExternalId(), AuthorisationType.AIS);
        return aisConsentMapper.mapToCmsAisAccountConsent(aisConsentEntity, authorisations);
    }
}
