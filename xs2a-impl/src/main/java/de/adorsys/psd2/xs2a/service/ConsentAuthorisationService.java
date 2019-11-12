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

package de.adorsys.psd2.xs2a.service;

import de.adorsys.psd2.event.core.model.EventType;
import de.adorsys.psd2.xs2a.core.psu.PsuIdData;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.domain.ResponseObject;
import de.adorsys.psd2.xs2a.domain.authorisation.AuthorisationResponse;
import de.adorsys.psd2.xs2a.domain.consent.*;
import de.adorsys.psd2.xs2a.service.authorization.AuthorisationChainResponsibilityService;
import de.adorsys.psd2.xs2a.service.authorization.ais.AisAuthorizationService;
import de.adorsys.psd2.xs2a.service.authorization.ais.AisScaAuthorisationServiceResolver;
import de.adorsys.psd2.xs2a.service.authorization.processor.model.AisAuthorisationProcessorRequest;
import de.adorsys.psd2.xs2a.service.consent.Xs2aAisConsentService;
import de.adorsys.psd2.xs2a.service.context.LoggingContextService;
import de.adorsys.psd2.xs2a.service.event.Xs2aEventService;
import de.adorsys.psd2.xs2a.service.validator.AisEndpointAccessCheckerService;
import de.adorsys.psd2.xs2a.service.validator.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static de.adorsys.psd2.xs2a.core.error.MessageErrorCode.*;
import static de.adorsys.psd2.xs2a.domain.TppMessageInformation.of;
import static de.adorsys.psd2.xs2a.service.mapper.psd2.ErrorType.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentAuthorisationService {

    private final Xs2aAisConsentService aisConsentService;
    private final AisScaAuthorisationServiceResolver aisScaAuthorisationServiceResolver;
    private final AisEndpointAccessCheckerService endpointAccessCheckerService;
    private final Xs2aEventService xs2aEventService;
    private final ConsentValidationService consentValidationService;
    private final RequestProviderService requestProviderService;
    private final AuthorisationChainResponsibilityService authorisationChainResponsibilityService;
    private final LoggingContextService loggingContextService;

    public ResponseObject<AuthorisationResponse> createAisAuthorisation(PsuIdData psuData, String consentId, String password) {
        ResponseObject<CreateConsentAuthorizationResponse> createAisAuthorizationResponse = createConsentAuthorizationWithResponse(psuData, consentId);

        if (createAisAuthorizationResponse.hasError()) {
            return ResponseObject.<AuthorisationResponse>builder()
                       .fail(createAisAuthorizationResponse.getError())
                       .build();
        }

        if (psuData.isEmpty() || StringUtils.isBlank(password)) {
            loggingContextService.storeScaStatus(createAisAuthorizationResponse.getBody().getScaStatus());
            return ResponseObject.<AuthorisationResponse>builder()
                       .body(createAisAuthorizationResponse.getBody())
                       .build();
        }

        String authorisationId = createAisAuthorizationResponse.getBody().getAuthorisationId();

        UpdateConsentPsuDataReq updatePsuData = new UpdateConsentPsuDataReq();
        updatePsuData.setPsuData(psuData);
        updatePsuData.setConsentId(consentId);
        updatePsuData.setAuthorizationId(authorisationId);
        updatePsuData.setPassword(password);

        ResponseObject<UpdateConsentPsuDataResponse> updatePsuDataResponse = updateConsentPsuData(updatePsuData);
        if (updatePsuDataResponse.hasError()) {
            return ResponseObject.<AuthorisationResponse>builder()
                       .fail(updatePsuDataResponse.getError())
                       .build();
        }

        return ResponseObject.<AuthorisationResponse>builder()
                   .body(updatePsuDataResponse.getBody())
                   .build();
    }

    public ResponseObject<Xs2aAuthorisationSubResources> getConsentInitiationAuthorisations(String consentId) {
        xs2aEventService.recordAisTppRequest(consentId, EventType.GET_CONSENT_AUTHORISATION_REQUEST_RECEIVED);

        Optional<AccountConsent> accountConsent = aisConsentService.getAccountConsentById(consentId);
        if (!accountConsent.isPresent()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}]. Get consent initiation authorisations failed: consent not found by id",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId);
            return ResponseObject.<Xs2aAuthorisationSubResources>builder()
                       .fail(AIS_403, of(CONSENT_UNKNOWN_403)).build();
        }

        ValidationResult validationResult = consentValidationService.validateConsentAuthorisationOnGettingById(accountConsent.get());
        if (validationResult.isNotValid()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}]. Get consent authorisations - validation failed: {}",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId, validationResult.getMessageError());
            return ResponseObject.<Xs2aAuthorisationSubResources>builder()
                       .fail(validationResult.getMessageError())
                       .build();
        }

        loggingContextService.storeConsentStatus(accountConsent.get().getConsentStatus());

        return getAuthorisationSubResources(consentId)
                   .map(resp -> ResponseObject.<Xs2aAuthorisationSubResources>builder().body(resp).build())
                   .orElseGet(() -> {
                       log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}]. Get consent initiation authorisations failed: authorisation not found at CMS by consent id",
                                requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId);
                       return ResponseObject.<Xs2aAuthorisationSubResources>builder()
                                  .fail(AIS_404, of(RESOURCE_UNKNOWN_404))
                                  .build();
                   });
    }

    public ResponseObject<ScaStatus> getConsentAuthorisationScaStatus(String consentId, String authorisationId) {
        xs2aEventService.recordAisTppRequest(consentId, EventType.GET_CONSENT_SCA_STATUS_REQUEST_RECEIVED);

        Optional<AccountConsent> accountConsent = aisConsentService.getAccountConsentById(consentId);
        if (!accountConsent.isPresent()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}]. Get consent authorisation SCA status failed: consent not found by id",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId);
            return ResponseObject.<ScaStatus>builder()
                       .fail(AIS_403, of(CONSENT_UNKNOWN_403)).build();
        }

        ValidationResult validationResult = consentValidationService.validateConsentAuthorisationScaStatus(accountConsent.get(), authorisationId);
        if (validationResult.isNotValid()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}], Authorisation-ID [{}]. Get consent authorisation SCA status - validation failed: {}",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId, authorisationId, validationResult.getMessageError());
            return ResponseObject.<ScaStatus>builder()
                       .fail(validationResult.getMessageError())
                       .build();
        }

        Optional<ScaStatus> scaStatusOptional = aisScaAuthorisationServiceResolver.getServiceInitiation(authorisationId)
                                                    .getAuthorisationScaStatus(consentId, authorisationId);

        if (!scaStatusOptional.isPresent()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}]. Get consent authorisation SCA status failed: consent not found at CMS by id",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId);
            return ResponseObject.<ScaStatus>builder()
                       .fail(AIS_403, of(RESOURCE_UNKNOWN_403))
                       .build();
        }

        ScaStatus scaStatus = scaStatusOptional.get();

        loggingContextService.storeConsentStatus(accountConsent.get().getConsentStatus());
        loggingContextService.storeScaStatus(scaStatus);

        return ResponseObject.<ScaStatus>builder()
                   .body(scaStatus)
                   .build();
    }

    public ResponseObject<UpdateConsentPsuDataResponse> updateConsentPsuData(UpdateConsentPsuDataReq updatePsuData) {
        xs2aEventService.recordAisTppRequest(updatePsuData.getConsentId(), EventType.UPDATE_AIS_CONSENT_PSU_DATA_REQUEST_RECEIVED, updatePsuData);

        String consentId = updatePsuData.getConsentId();
        String authorisationId = updatePsuData.getAuthorizationId();

        if (!endpointAccessCheckerService.isEndpointAccessible(authorisationId, consentId)) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}], Authorisation-ID [{}]. Update consent PSU data failed: update endpoint is blocked for current authorisation",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId, authorisationId);
            return ResponseObject.<UpdateConsentPsuDataResponse>builder()
                       .fail(AIS_403, of(SERVICE_BLOCKED))
                       .build();
        }

        // TODO temporary solution: CMS should be refactored to return response objects instead of Strings, Enums, Booleans etc., so we should receive this error from CMS https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/581
        Optional<AccountConsent> accountConsent = aisConsentService.getAccountConsentById(consentId);

        if (!accountConsent.isPresent()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}]. Update consent PSU data failed: consent not found by id",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId);
            return ResponseObject.<UpdateConsentPsuDataResponse>builder()
                       .fail(AIS_403, of(CONSENT_UNKNOWN_403)).build();
        }

        ValidationResult validationResult = consentValidationService.validateConsentPsuDataOnUpdate(accountConsent.get(), updatePsuData);

        if (validationResult.isNotValid()) {
            if (validationResult.getMessageError().getTppMessage().getMessageErrorCode() == PSU_CREDENTIALS_INVALID) {
                aisConsentService.updateConsentAuthorisationStatus(authorisationId, ScaStatus.FAILED);
            }

            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}], Authorisation-ID [{}]. Update consent PSU data - validation failed: {}",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId, authorisationId, validationResult.getMessageError());
            return ResponseObject.<UpdateConsentPsuDataResponse>builder()
                       .fail(validationResult.getMessageError())
                       .build();
        }

        AccountConsent consent = accountConsent.get();

        if (consent.isExpired()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}]. Update consent PSU data failed: consent expired",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId);
            return ResponseObject.<UpdateConsentPsuDataResponse>builder()
                       .fail(AIS_401, of(CONSENT_EXPIRED))
                       .build();
        }

        loggingContextService.storeConsentStatus(consent.getConsentStatus());

        return getUpdateConsentPsuDataResponse(updatePsuData);
    }

    private ResponseObject<UpdateConsentPsuDataResponse> getUpdateConsentPsuDataResponse(UpdateConsentPsuDataReq updatePsuData) {
        AisAuthorizationService service = aisScaAuthorisationServiceResolver.getServiceInitiation(updatePsuData.getAuthorizationId());

        Optional<AccountConsentAuthorization> authorization = service.getAccountConsentAuthorizationById(updatePsuData.getAuthorizationId(), updatePsuData.getConsentId());

        if (!authorization.isPresent()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Authorisation-ID: [{}]. Update consent PSU data failed: authorisation not found by id",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), updatePsuData.getAuthorizationId());
            return ResponseObject.<UpdateConsentPsuDataResponse>builder()
                       .fail(AIS_403, of(CONSENT_UNKNOWN_403)).build();
        }

        AccountConsentAuthorization consentAuthorization = authorization.get();
        UpdateConsentPsuDataResponse response = (UpdateConsentPsuDataResponse) authorisationChainResponsibilityService.apply(
            new AisAuthorisationProcessorRequest(consentAuthorization.getChosenScaApproach(),
                                                 consentAuthorization.getScaStatus(),
                                                 updatePsuData,
                                                 consentAuthorization));
        loggingContextService.storeScaStatus(response.getScaStatus());

        return Optional.ofNullable(response)
                   .map(s -> Optional.ofNullable(s.getErrorHolder())
                                 .map(e -> ResponseObject.<UpdateConsentPsuDataResponse>builder()
                                               .fail(e)
                                               .build())
                                 .orElseGet(ResponseObject.<UpdateConsentPsuDataResponse>builder().body(response)::build))
                   .orElseGet(ResponseObject.<UpdateConsentPsuDataResponse>builder()
                                  .fail(AIS_400, of(FORMAT_ERROR))
                                  ::build);
    }

    private ResponseObject<CreateConsentAuthorizationResponse> createConsentAuthorizationWithResponse(PsuIdData psuDataFromRequest, String consentId) {
        xs2aEventService.recordAisTppRequest(consentId, EventType.START_AIS_CONSENT_AUTHORISATION_REQUEST_RECEIVED);

        // TODO temporary solution: CMS should be refactored to return response objects instead of Strings, Enums, Booleans etc., so we should receive this error from CMS https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/581
        Optional<AccountConsent> accountConsent = aisConsentService.getAccountConsentById(consentId);

        if (!accountConsent.isPresent()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}]. Create consent authorisation with response failed: consent not found by id",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId);
            return ResponseObject.<CreateConsentAuthorizationResponse>builder()
                       .fail(AIS_403, of(CONSENT_UNKNOWN_403)).build();
        }

        boolean isMultilevel = accountConsent.get().isMultilevelScaRequired();

        PsuIdData psuIdData = psuDataFromRequest;

        if (psuIdData.isEmpty() && !isMultilevel) {
            Optional<PsuIdData> psuIdDataFromDb = accountConsent.get().getPsuIdDataList().stream().findFirst();
            if (psuIdDataFromDb.isPresent()) {
                psuIdData = psuIdDataFromDb.get();
            }
        }

        ValidationResult validationResult = consentValidationService.validateConsentAuthorisationOnCreate(accountConsent.get());
        if (validationResult.isNotValid()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}]. Create consent authorisation with response - validation failed: {}",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId, validationResult.getMessageError());
            return ResponseObject.<CreateConsentAuthorizationResponse>builder()
                       .fail(validationResult.getMessageError())
                       .build();
        }

        if (accountConsent.get().isExpired()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}]. Create consent authorisation with response failed: consent expired",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId);
            return ResponseObject.<CreateConsentAuthorizationResponse>builder()
                       .fail(AIS_401, of(CONSENT_EXPIRED))
                       .build();
        }

        loggingContextService.storeConsentStatus(accountConsent.get().getConsentStatus());

        AisAuthorizationService service = aisScaAuthorisationServiceResolver.getService();
        return service.createConsentAuthorization(psuIdData, consentId)
                   .map(resp -> ResponseObject.<CreateConsentAuthorizationResponse>builder().body(resp).build())
                   .orElseGet(ResponseObject.<CreateConsentAuthorizationResponse>builder()
                                  .fail(AIS_403, of(CONSENT_UNKNOWN_403))
                                  ::build);
    }

    private Optional<Xs2aAuthorisationSubResources> getAuthorisationSubResources(String consentId) {
        return aisConsentService.getAuthorisationSubResources(consentId)
                   .map(Xs2aAuthorisationSubResources::new);
    }
}