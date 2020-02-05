/*
 * Copyright 2018-2018 adorsys GmbH & Co KG
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

package de.adorsys.psd2.consent.service.mapper;

import de.adorsys.psd2.consent.api.TypeAccess;
import de.adorsys.psd2.consent.api.ais.*;
import de.adorsys.psd2.consent.domain.account.AisConsentAuthorization;
import de.adorsys.psd2.consent.domain.account.AspspAccountAccess;
import de.adorsys.psd2.consent.domain.account.Consent;
import de.adorsys.psd2.consent.domain.account.TppAccountAccess;
import de.adorsys.psd2.consent.service.AisConsentUsageService;
import de.adorsys.psd2.xs2a.core.ais.AccountAccessType;
import de.adorsys.psd2.xs2a.core.profile.AccountReference;
import de.adorsys.psd2.xs2a.core.profile.AccountReferenceSelector;
import de.adorsys.psd2.xs2a.core.profile.AdditionalInformationAccess;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ConsentMapper {
    private final PsuDataMapper psuDataMapper;
    private final TppInfoMapper tppInfoMapper;
    private final AisConsentUsageService aisConsentUsageService;
    private final AuthorisationTemplateMapper authorisationTemplateMapper;

    private AccountAccess getAvailableAccess(Consent consent) {
        AccountAccess tppAccountAccess = mapToAisAccountAccess(consent);
        AccountAccess aspspAccountAccess = mapToAspspAisAccountAccess(consent);

        return tppAccountAccess.getAllPsd2() != null
                   ? tppAccountAccess
                   : aspspAccountAccess.isNotEmpty() ? aspspAccountAccess : tppAccountAccess;
    }

    /**
     * Maps AisConsent to AisAccountConsent with accesses populated with account references, provided by ASPSP.
     * <p>
     * If no account references were provided by the ASPSP, TPP accesses will be used instead.
     *
     * @param consent AIS consent entity
     * @return mapped AIS consent
     */
    public CmsAccountConsent mapToAccountConsent(Consent consent) {
        AccountAccess chosenAccess = getAvailableAccess(consent);

        Map<String, Integer> usageCounterMap = aisConsentUsageService.getUsageCounterMap(consent);

        return new CmsAccountConsent(
            consent.getExternalId(),
            chosenAccess,
            mapToAspspAisAccountAccess(consent),
            consent.isRecurringIndicator(),
            consent.getValidUntil(),
            consent.getExpireDate(),
            consent.getAllowedFrequencyPerDay(),
            consent.getLastActionDate(),
            consent.getConsentStatus(),
            consent.getAccesses().stream().anyMatch(a -> a.getTypeAccess() == TypeAccess.BALANCE),
            consent.isTppRedirectPreferred(),
            consent.getAisConsentRequestType(),
            psuDataMapper.mapToPsuIdDataList(consent.getPsuDataList()),
            tppInfoMapper.mapToTppInfo(consent.getTppInfo()),
            authorisationTemplateMapper.mapToAuthorisationTemplate(consent.getAuthorisationTemplate()),
            consent.isMultilevelScaRequired(),
            mapToAisAccountConsentAuthorisation(consent.getAuthorizations()),
            usageCounterMap,
            consent.getCreationTimestamp(),
            consent.getStatusChangeTimestamp(),
            consent.getBody());
    }

    public CmsPsuAspspAccountConsent mapToCmsPsuAspspAccountConsent(Consent consent) {
        AccountAccess chosenAccess = getAvailableAccess(consent);

        Map<String, Integer> usageCounterMap = aisConsentUsageService.getUsageCounterMap(consent);

        return new CmsPsuAspspAccountConsent(
            consent.getExternalId(),
            chosenAccess,
            consent.isRecurringIndicator(),
            consent.getValidUntil(),
            consent.getExpireDate(),
            consent.getAllowedFrequencyPerDay(),
            consent.getLastActionDate(),
            consent.getConsentStatus(),
            consent.getAccesses().stream().anyMatch(a -> a.getTypeAccess() == TypeAccess.BALANCE),
            consent.isTppRedirectPreferred(),
            consent.getAisConsentRequestType(),
            psuDataMapper.mapToPsuIdDataList(consent.getPsuDataList()),
            tppInfoMapper.mapToTppInfo(consent.getTppInfo()),
            authorisationTemplateMapper.mapToAuthorisationTemplate(consent.getAuthorisationTemplate()),
            consent.isMultilevelScaRequired(),
            mapToAisAccountConsentAuthorisation(consent.getAuthorizations()),
            usageCounterMap,
            consent.getCreationTimestamp(),
            consent.getStatusChangeTimestamp(),
            consent.getBody());
    }

    public AisConsentAuthorizationResponse mapToAisConsentAuthorizationResponse(AisConsentAuthorization aisConsentAuthorization) {
        return Optional.ofNullable(aisConsentAuthorization)
                   .map(conAuth -> {
                       AisConsentAuthorizationResponse resp = new AisConsentAuthorizationResponse();
                       resp.setAuthorizationId(conAuth.getExternalId());
                       resp.setPsuIdData(psuDataMapper.mapToPsuIdData(conAuth.getPsuData()));
                       resp.setConsentId(conAuth.getConsent().getExternalId());
                       resp.setScaStatus(conAuth.getScaStatus());
                       resp.setAuthenticationMethodId(conAuth.getAuthenticationMethodId());
                       resp.setScaAuthenticationData(conAuth.getScaAuthenticationData());
                       resp.setChosenScaApproach(conAuth.getScaApproach());

                       return resp;
                   })
                   .orElse(null);
    }

    public Set<AspspAccountAccess> mapAspspAccountAccesses(AccountAccess aisAccountAccess) {
        Set<AspspAccountAccess> accesses = new HashSet<>();
        accesses.addAll(getAspspAccountAccesses(TypeAccess.ACCOUNT, aisAccountAccess.getAccounts()));
        accesses.addAll(getAspspAccountAccesses(TypeAccess.BALANCE, aisAccountAccess.getBalances()));
        accesses.addAll(getAspspAccountAccesses(TypeAccess.TRANSACTION, aisAccountAccess.getTransactions()));
        AdditionalInformationAccess info = aisAccountAccess.getAccountAdditionalInformationAccess();
        if (info != null) {
            BiConsumer<List<AccountReference>, TypeAccess> updateAccesses = (list, type) -> {
                if (CollectionUtils.isNotEmpty(list)) {
                    accesses.addAll(getAspspAccountAccesses(type, list));
                }
            };
            updateAccesses.accept(info.getOwnerName(), TypeAccess.OWNER_NAME);
        }
        return accesses;
    }

    AccountAccess mapToAisAccountAccess(Consent consent) {
        List<TppAccountAccess> accesses = consent.getAccesses();
        return new AccountAccess(mapToInitialAccountReferences(accesses, TypeAccess.ACCOUNT),
                                 mapToInitialAccountReferences(accesses, TypeAccess.BALANCE),
                                 mapToInitialAccountReferences(accesses, TypeAccess.TRANSACTION),
                                 getAccessType(consent.getAvailableAccounts()),
                                 getAccessType(consent.getAllPsd2()),
                                 getAccessType(consent.getAvailableAccountsWithBalance()),
                                 mapToInitialAdditionalInformationAccess(accesses, consent)
        );
    }

    private List<AccountReference> mapToInitialAccountReferences(List<TppAccountAccess> aisAccounts, TypeAccess typeAccess) {
        return aisAccounts.stream()
                   .filter(a -> a.getTypeAccess() == typeAccess)
                   .map(access -> new AccountReference(access.getAccountReferenceType(), access.getAccountIdentifier(), access.getCurrency()))
                   .collect(Collectors.toList());
    }

    private AccountAccess mapToAspspAisAccountAccess(Consent consent) {
        List<AspspAccountAccess> accesses = consent.getAspspAccountAccesses();
        return new AccountAccess(mapToAccountReferences(accesses, TypeAccess.ACCOUNT),
                                 mapToAccountReferences(accesses, TypeAccess.BALANCE),
                                 mapToAccountReferences(accesses, TypeAccess.TRANSACTION),
                                 getAccessType(consent.getAvailableAccounts()),
                                 getAccessType(consent.getAllPsd2()),
                                 getAccessType(consent.getAvailableAccountsWithBalance()),
                                 mapToAspspAdditionalInformationAccess(accesses, consent)
        );
    }

    private AdditionalInformationAccess mapToInitialAdditionalInformationAccess(List<TppAccountAccess> accesses, Consent consent) {
        return consent.checkNoneAdditionalAccountInformation()
                   ? null
                   : new AdditionalInformationAccess(mapToAdditionalInformationInitialAccountReferences(consent.getOwnerNameType(), TypeAccess.OWNER_NAME, accesses));
    }

    private List<AccountReference> mapToAdditionalInformationInitialAccountReferences(AdditionalAccountInformationType type, TypeAccess typeAccess, List<TppAccountAccess> accesses) {
        return type == AdditionalAccountInformationType.DEDICATED_ACCOUNTS
                   ? mapToInitialAccountReferences(accesses, typeAccess)
                   : type == AdditionalAccountInformationType.ALL_AVAILABLE_ACCOUNTS ? Collections.emptyList() : null;
    }

    private AdditionalInformationAccess mapToAspspAdditionalInformationAccess(List<AspspAccountAccess> accesses, Consent consent) {
        return consent.checkNoneAdditionalAccountInformation()
                   ? null
                   : new AdditionalInformationAccess(mapToAdditionalInformationAspspAccountReferences(consent.getOwnerNameType(), TypeAccess.OWNER_NAME, accesses));
    }

    private List<AccountReference> mapToAdditionalInformationAspspAccountReferences(AdditionalAccountInformationType type, TypeAccess typeAccess, List<AspspAccountAccess> accesses) {
        return type == AdditionalAccountInformationType.DEDICATED_ACCOUNTS
                   ? mapToAccountReferences(accesses, typeAccess)
                   : type == AdditionalAccountInformationType.ALL_AVAILABLE_ACCOUNTS ? Collections.emptyList() : null;
    }

    private List<AccountReference> mapToAccountReferences(List<AspspAccountAccess> aisAccounts, TypeAccess typeAccess) {
        return aisAccounts.stream()
                   .filter(a -> a.getTypeAccess() == typeAccess)
                   .map(access -> new AccountReference(access.getAccountReferenceType(), access.getAccountIdentifier(), access.getCurrency(), access.getResourceId(), access.getAspspAccountId()))
                   .collect(Collectors.toList());
    }

    private Set<AspspAccountAccess> getAspspAccountAccesses(TypeAccess typeAccess, List<AccountReference> accountReferences) {
        return Optional.ofNullable(accountReferences)
                   .map(lst -> lst.stream()
                                   .map(acc -> mapToAspspAccountAccess(typeAccess, acc))
                                   .collect(Collectors.toSet()))
                   .orElse(Collections.emptySet());
    }

    private AspspAccountAccess mapToAspspAccountAccess(TypeAccess typeAccess, AccountReference accountReference) {
        AccountReferenceSelector selector = accountReference.getUsedAccountReferenceSelector();

        return new AspspAccountAccess(selector.getAccountValue(),
                                      typeAccess,
                                      selector.getAccountReferenceType(),
                                      accountReference.getCurrency(),
                                      accountReference.getResourceId(),
                                      accountReference.getAspspAccountId());
    }

    private String getAccessType(AccountAccessType type) {
        return Optional.ofNullable(type)
                   .map(Enum::name)
                   .orElse(null);
    }


    private List<AisAccountConsentAuthorisation> mapToAisAccountConsentAuthorisation(List<AisConsentAuthorization> aisConsentAuthorisations) {
        if (CollectionUtils.isEmpty(aisConsentAuthorisations)) {
            return Collections.emptyList();
        }

        return aisConsentAuthorisations.stream()
                   .map(this::mapToAisAccountConsentAuthorisation)
                   .collect(Collectors.toList());
    }

    private AisAccountConsentAuthorisation mapToAisAccountConsentAuthorisation(AisConsentAuthorization aisConsentAuthorisation) {
        return Optional.ofNullable(aisConsentAuthorisation)
                   .map(auth -> new AisAccountConsentAuthorisation(auth.getExternalId(),
                                                                   psuDataMapper.mapToPsuIdData(auth.getPsuData()),
                                                                   auth.getScaStatus()))
                   .orElse(null);
    }
}