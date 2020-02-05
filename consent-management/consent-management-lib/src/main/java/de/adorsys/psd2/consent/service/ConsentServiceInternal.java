/*
 * Copyright 2018-2019 adorsys GmbH & Co KG
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

import de.adorsys.psd2.aspsp.profile.service.AspspProfileService;
import de.adorsys.psd2.consent.api.CmsResponse;
import de.adorsys.psd2.consent.api.WrongChecksumException;
import de.adorsys.psd2.consent.api.ais.*;
import de.adorsys.psd2.consent.api.service.ConsentService;
import de.adorsys.psd2.consent.domain.AuthorisationTemplateEntity;
import de.adorsys.psd2.consent.domain.PsuData;
import de.adorsys.psd2.consent.domain.TppInfoEntity;
import de.adorsys.psd2.consent.domain.account.*;
import de.adorsys.psd2.consent.repository.ConsentJpaRepository;
import de.adorsys.psd2.consent.repository.ConsentVerifyingRepository;
import de.adorsys.psd2.consent.repository.TppInfoRepository;
import de.adorsys.psd2.consent.service.mapper.ConsentMapper;
import de.adorsys.psd2.consent.service.mapper.PsuDataMapper;
import de.adorsys.psd2.consent.service.mapper.TppInfoMapper;
import de.adorsys.psd2.consent.service.psu.CmsPsuService;
import de.adorsys.psd2.xs2a.core.consent.ConsentStatus;
import de.adorsys.psd2.xs2a.core.psu.PsuIdData;
import de.adorsys.psd2.xs2a.core.tpp.TppRedirectUri;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static de.adorsys.psd2.consent.api.CmsError.LOGICAL_ERROR;
import static de.adorsys.psd2.consent.api.CmsError.TECHNICAL_ERROR;
import static de.adorsys.psd2.xs2a.core.consent.ConsentStatus.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsentServiceInternal implements ConsentService {
    private final ConsentJpaRepository consentJpaRepository;
    private final ConsentVerifyingRepository aisConsentRepository;
    private final TppInfoRepository tppInfoRepository;
    private final ConsentMapper consentMapper;
    private final PsuDataMapper psuDataMapper;
    private final AspspProfileService aspspProfileService;
    private final ConsentConfirmationExpirationService consentConfirmationExpirationService;
    private final TppInfoMapper tppInfoMapper;
    private final CmsPsuService cmsPsuService;
    private final CoreConsentsConvertService coreConsentsConvertService;

    /**
     * Creates AIS consent.
     *
     * @param request needed parameters for creating AIS consent
     * @return create consent response, containing consent and its encrypted ID
     */
    @Override
    @Transactional(rollbackFor = WrongChecksumException.class)
    public CmsResponse<CreateConsentResponse> createConsent(CreateConsentRequest request) throws WrongChecksumException {
        if (request.getAllowedFrequencyPerDay() == null) {
            log.info("TPP ID: [{}]. Consent cannot be created, because request contains no allowed frequency per day",
                     request.getTppInfo().getAuthorisationNumber());
            return CmsResponse.<CreateConsentResponse>builder()
                       .error(LOGICAL_ERROR)
                       .build();
        }
        Consent consent = createConsentFromRequest(request);
        tppInfoRepository.findByAuthorisationNumber(request.getTppInfo().getAuthorisationNumber())
            .ifPresent(consent::setTppInfo);

        Consent savedConsent = aisConsentRepository.verifyAndSave(consent);

        if (savedConsent.getId() != null) {
            return CmsResponse.<CreateConsentResponse>builder()
                       .payload(new CreateConsentResponse(savedConsent.getExternalId(), consentMapper.mapToAccountConsent(savedConsent), consent.getTppNotificationContentPreferred()))
                       .build();
        } else {
            log.info("TPP ID: [{}], External Consent ID: [{}]. AIS consent cannot be created, because when saving to DB got null ID",
                     request.getTppInfo().getAuthorisationNumber(), consent.getExternalId());
            return CmsResponse.<CreateConsentResponse>builder()
                       .error(TECHNICAL_ERROR)
                       .build();
        }
    }

    /**
     * Reads status of consent by ID.
     *
     * @param consentId ID of consent
     * @return ConsentStatus
     */
    @Override
    @Transactional
    public CmsResponse<ConsentStatus> getConsentStatusById(String consentId) {
        Optional<ConsentStatus> consentStatusOptional = consentJpaRepository.findByExternalId(consentId)
                                                            .map(consentConfirmationExpirationService::checkAndUpdateOnConfirmationExpiration)
                                                            .map(this::checkAndUpdateOnExpiration)
                                                            .map(Consent::getConsentStatus);
        if (consentStatusOptional.isPresent()) {
            return CmsResponse.<ConsentStatus>builder()
                       .payload(consentStatusOptional.get())
                       .build();
        } else {
            log.info("Consent ID: [{}]. Get consent status failed, because consent not found", consentId);
            return CmsResponse.<ConsentStatus>builder()
                       .error(LOGICAL_ERROR)
                       .build();
        }
    }

    /**
     * Updates consent status by ID.
     *
     * @param consentId ID of consent
     * @param status    new consent status
     * @return Boolean
     */
    @Override
    @Transactional(rollbackFor = WrongChecksumException.class)
    public CmsResponse<Boolean> updateConsentStatusById(String consentId, ConsentStatus status) throws WrongChecksumException {
        Optional<Consent> consentOptional = getActualAisConsent(consentId);

        if (consentOptional.isPresent()) {
            Consent consent = consentOptional.get();
            boolean result = setStatusAndSaveConsent(consent, status);

            return CmsResponse.<Boolean>builder()
                       .payload(result)
                       .build();
        }

        log.info("Consent ID [{}]. Update consent status by ID failed, because consent not found", consentId);
        return CmsResponse.<Boolean>builder()
                   .error(LOGICAL_ERROR)
                   .build();
    }

    /**
     * Reads full information of consent by ID.
     *
     * @param consentId ID of consent
     * @return AisAccountConsent
     */
    @Override
    @Transactional
    public CmsResponse<CmsAccountConsent> getAccountConsentById(String consentId) {
        Optional<Consent> consentOptional = consentJpaRepository.findByExternalId(consentId);

        if (consentOptional.isPresent()) {
            Optional<CmsAccountConsent> accountConsentOptional = consentOptional.map(consentConfirmationExpirationService::checkAndUpdateOnConfirmationExpiration)
                                                                     .map(this::checkAndUpdateOnExpiration)
                                                                     .map(consentMapper::mapToAccountConsent);

            if (accountConsentOptional.isPresent()) {
                CmsAccountConsent aisAccountConsent = accountConsentOptional.get();
                transferCoreConsentToCommonConsent(aisAccountConsent, consentOptional.get());
                return CmsResponse.<CmsAccountConsent>builder()
                           .payload(aisAccountConsent)
                           .build();
            }
        }

        log.info("Consent ID [{}]. Get consent by ID failed, because consent not found", consentId);
        return CmsResponse.<CmsAccountConsent>builder()
                   .error(LOGICAL_ERROR)
                   .build();
    }

    private void transferCoreConsentToCommonConsent(CmsAccountConsent cmsAccountConsent, Consent consent) {
        if (consent.getBody() != null) {
            return;
        }

        byte[] consentBody = coreConsentsConvertService.data(consent, consent.getConsentType());
        if (ArrayUtils.isNotEmpty(consentBody)) {
            consent.setBody(consentBody);
            consentJpaRepository.save(consent);
            cmsAccountConsent.setBody(consentBody);
        }
    }

    /**
     * Searches the old AIS consents and updates their statuses according to authorisation states and PSU data.
     *
     * @param newConsentId ID of new consent that was created
     * @return true if old consents were updated, false otherwise
     */
    @Override
    @Transactional
    public CmsResponse<Boolean> findAndTerminateOldConsentsByNewConsentId(String newConsentId) {
        Consent newConsent = consentJpaRepository.findByExternalId(newConsentId)
                                 .orElseThrow(() -> {
                                     log.info("Consent ID: [{}]. Cannot find consent by ID", newConsentId);
                                     return new IllegalArgumentException("Wrong consent ID: " + newConsentId);
                                 });

        if (newConsent.isOneAccessType()) {
            log.info("Consent ID: [{}]. Cannot find old consents, because consent is OneAccessType", newConsentId);
            return CmsResponse.<Boolean>builder()
                       .payload(false)
                       .build();
        }

        if (newConsent.isWrongConsentData()) {
            log.info("Consent ID: [{}]. Find old consents failed, because consent PSU data list is empty or TPP Info is null", newConsentId);
            throw new IllegalArgumentException("Wrong consent data");
        }

        List<PsuData> psuDataList = newConsent.getPsuDataList();
        Set<String> psuIds = psuDataList.stream()
                                 .filter(Objects::nonNull)
                                 .map(PsuData::getPsuId)
                                 .collect(Collectors.toSet());
        TppInfoEntity tppInfo = newConsent.getTppInfo();

        List<Consent> oldConsents = consentJpaRepository.findOldConsentsByNewConsentParams(psuIds,
                                                                                           tppInfo.getAuthorisationNumber(),
                                                                                           newConsent.getInstanceId(),
                                                                                           newConsent.getExternalId(),
                                                                                           EnumSet.of(RECEIVED, PARTIALLY_AUTHORISED, VALID));

        List<Consent> oldConsentsWithExactPsuDataLists = oldConsents.stream()
                                                             .distinct()
                                                             .filter(c -> cmsPsuService.isPsuDataListEqual(c.getPsuDataList(), psuDataList))
                                                             .collect(Collectors.toList());

        if (oldConsentsWithExactPsuDataLists.isEmpty()) {
            log.info("Consent ID: [{}]. Cannot find old consents, because consent hasn't exact PSU data lists as old consents", newConsentId);
            return CmsResponse.<Boolean>builder()
                       .payload(false)
                       .build();
        }

        oldConsentsWithExactPsuDataLists.forEach(this::updateStatus);
        consentJpaRepository.saveAll(oldConsentsWithExactPsuDataLists);
        return CmsResponse.<Boolean>builder()
                   .payload(true)
                   .build();
    }

    /**
     * Updates AIS consent account access by ID.
     *
     * @param request   needed parameters for updating AIS consent
     * @param consentId ID of the consent to be updated
     * @return String   consent ID
     */
    @Override
    @Transactional(rollbackFor = WrongChecksumException.class)
    public CmsResponse<String> updateAspspAccountAccess(String consentId, AisAccountAccessInfo request) throws WrongChecksumException {
        Optional<Consent> consentOptional = aisConsentRepository.getActualAisConsent(consentId);

        if (!consentOptional.isPresent()) {
            log.info("Consent ID [{}]. Update aspsp account access failed, because consent not found",
                     consentId);
            return CmsResponse.<String>builder()
                       .error(LOGICAL_ERROR)
                       .build();
        }

        Consent consent = consentOptional.get();
        consent.addAspspAccountAccess(new AspspAccountAccessHolder(request).getAccountAccesses());

        String externalId = aisConsentRepository.verifyAndSave(consent).getExternalId();

        return CmsResponse.<String>builder()
                   .payload(externalId)
                   .build();
    }

    @Override
    @Transactional(rollbackFor = WrongChecksumException.class)
    public CmsResponse<CmsAccountConsent> updateAspspAccountAccessWithResponse(String consentId, AisAccountAccessInfo request) throws WrongChecksumException {
        Optional<Consent> consentOptional = aisConsentRepository.getActualAisConsent(consentId);

        if (!consentOptional.isPresent()) {
            log.info("Consent ID [{}]. Update aspsp account access with response failed, because consent not found",
                     consentId);
            return CmsResponse.<CmsAccountConsent>builder()
                       .error(LOGICAL_ERROR)
                       .build();
        }

        Consent consent = consentOptional.get();

        Set<AspspAccountAccess> aspspAccesses = new AspspAccountAccessHolder(request).getAccountAccesses();

        List<AspspAccountAccess> cmsAccesses = consent.getAspspAccountAccesses();

        consent.setAspspAccountAccesses(getUpdatedAccesses(cmsAccesses, aspspAccesses));

        Consent aisConsent = aisConsentRepository.verifyAndUpdate(consent);

        return CmsResponse.<CmsAccountConsent>builder()
                   .payload(consentMapper.mapToAccountConsent(aisConsent))
                   .build();
    }

    private AspspAccountAccess enrichAccount(Set<AspspAccountAccess> accounts, AspspAccountAccess element) {

        return accounts.stream()
                   .filter(a -> a.getAccountIdentifier().equals(element.getAccountIdentifier())
                                    && a.getAccountReferenceType() == element.getAccountReferenceType()
                                    && a.getCurrency() == element.getCurrency()
                                    && a.getTypeAccess() == (element.getTypeAccess()))
                   .findFirst()
                   .orElse(element);
    }

    private List<AspspAccountAccess> getUpdatedAccesses(List<AspspAccountAccess> cmsAccesses, Set<AspspAccountAccess> requestAccesses) {
        if (CollectionUtils.isEmpty(cmsAccesses)) {
            return new ArrayList<>(requestAccesses);
        }

        Set<AspspAccountAccess> updatedCmsAccesses = new HashSet<>();

        for (AspspAccountAccess access : cmsAccesses) {
            updatedCmsAccesses.add(enrichAccount(requestAccesses, access));
        }

        return new ArrayList<>(updatedCmsAccesses);
    }

    @Override
    public CmsResponse<List<PsuIdData>> getPsuDataByConsentId(String consentId) {
        Optional<List<PsuIdData>> psuIdDataOptional = getActualAisConsent(consentId)
                                                          .map(ac -> psuDataMapper.mapToPsuIdDataList(ac.getPsuDataList()));

        if (psuIdDataOptional.isPresent()) {
            return CmsResponse.<List<PsuIdData>>builder()
                       .payload(psuIdDataOptional.get())
                       .build();
        }

        log.info("Consent ID [{}]. Get psu data by consent id failed, because consent not found",
                 consentId);
        return CmsResponse.<List<PsuIdData>>builder()
                   .error(LOGICAL_ERROR)
                   .build();
    }

    @Override
    @Transactional(rollbackFor = WrongChecksumException.class)
    public CmsResponse<Boolean> updateMultilevelScaRequired(String consentId, boolean multilevelScaRequired) throws WrongChecksumException {
        Optional<Consent> aisConsentOptional = consentJpaRepository.findByExternalId(consentId);
        if (!aisConsentOptional.isPresent()) {
            log.info("Consent ID: [{}]. Get update multilevel SCA required status failed, because consent authorisation is not found",
                     consentId);
            return CmsResponse.<Boolean>builder()
                       .payload(false)
                       .build();
        }
        Consent consent = aisConsentOptional.get();
        consent.setMultilevelScaRequired(multilevelScaRequired);

        aisConsentRepository.verifyAndSave(consent);

        return CmsResponse.<Boolean>builder()
                   .payload(true)
                   .build();
    }

    private Consent checkAndUpdateOnExpiration(Consent consent) {
        if (consent != null && consent.shouldConsentBeExpired()) {
            return consentConfirmationExpirationService.expireConsent(consent);
        }

        return consent;
    }

    private Consent createConsentFromRequest(CreateConsentRequest request) {
        Consent consent = new Consent();
        consent.setExternalId(UUID.randomUUID().toString());
        consent.setConsentStatus(RECEIVED);
        consent.setAllowedFrequencyPerDay(request.getAllowedFrequencyPerDay());
        consent.setTppFrequencyPerDay(request.getRequestedFrequencyPerDay());
        consent.setRequestDateTime(LocalDateTime.now());
        consent.setValidUntil(adjustExpireDate(request.getValidUntil()));
        consent.setPsuDataList(psuDataMapper.mapToPsuDataList(Collections.singletonList(request.getPsuData())));
        consent.setTppInfo(tppInfoMapper.mapToTppInfoEntity(request.getTppInfo()));
        AuthorisationTemplateEntity authorisationTemplate = new AuthorisationTemplateEntity();
        TppRedirectUri tppRedirectUri = request.getTppRedirectUri();
        if (tppRedirectUri != null) {
            authorisationTemplate.setRedirectUri(tppRedirectUri.getUri());
            authorisationTemplate.setNokRedirectUri(tppRedirectUri.getNokUri());
        }
        consent.setAuthorisationTemplate(authorisationTemplate);
        consent.addAccountAccess(new TppAccountAccessHolder(request.getAccess())
                                     .getAccountAccesses());
        consent.setRecurringIndicator(request.isRecurringIndicator());
        consent.setTppRedirectPreferred(request.isTppRedirectPreferred());
        consent.setCombinedServiceIndicator(request.isCombinedServiceIndicator());
        consent.setAvailableAccounts(request.getAccess().getAvailableAccounts());
        consent.setAllPsd2(request.getAccess().getAllPsd2());
        consent.setAvailableAccountsWithBalance(request.getAccess().getAvailableAccountsWithBalance());
        consent.setLastActionDate(LocalDate.now());
        setAdditionalInformationTypes(consent, request.getAccess().getAccountAdditionalInformationAccess());
        consent.setInternalRequestId(request.getInternalRequestId());
        consent.setTppNotificationUri(request.getTppNotificationUri());
        consent.setTppNotificationContentPreferred(request.getNotificationSupportedModes());
        consent.setBody(request.getBody());
        return consent;
    }

    private void setAdditionalInformationTypes(Consent consent, AccountAdditionalInformationAccess info) {
        AdditionalAccountInformationType ownerNameType = info == null
                                                             ? AdditionalAccountInformationType.NONE
                                                             : AdditionalAccountInformationType.findTypeByList(info.getOwnerName());
        consent.setOwnerNameType(ownerNameType);
    }

    private LocalDate adjustExpireDate(LocalDate validUntil) {
        int lifetime = aspspProfileService.getAspspSettings().getAis().getConsentTypes().getMaxConsentValidityDays();
        if (lifetime <= 0) {
            return validUntil;
        }

        //Expire date is inclusive and TPP can access AIS consent from current date
        LocalDate lifeTimeDate = LocalDate.now().plusDays(lifetime - 1L);
        return lifeTimeDate.isBefore(validUntil) ? lifeTimeDate : validUntil;
    }

    private Optional<Consent> getActualAisConsent(String consentId) {
        return consentJpaRepository.findByExternalId(consentId)
                   .filter(c -> !c.getConsentStatus().isFinalisedStatus());
    }

    private boolean setStatusAndSaveConsent(Consent consent, ConsentStatus status) throws WrongChecksumException {
        if (consent.getConsentStatus().isFinalisedStatus()) {
            log.info("Consent ID: [{}], Consent status [{}]. Update consent status by ID failed, because consent status is finalised",
                     consent.getExternalId(), consent.getConsentStatus());
            return false;
        }
        consent.setLastActionDate(LocalDate.now());
        consent.setConsentStatus(status);

        Consent aisConsent = aisConsentRepository.verifyAndSave(consent);

        return Optional.ofNullable(aisConsent)
                   .isPresent();
    }

    private void updateStatus(Consent aisConsent) {
        aisConsent.setConsentStatus(aisConsent.getConsentStatus() == RECEIVED || aisConsent.getConsentStatus() == PARTIALLY_AUTHORISED
                                        ? REJECTED
                                        : TERMINATED_BY_TPP);
    }
}