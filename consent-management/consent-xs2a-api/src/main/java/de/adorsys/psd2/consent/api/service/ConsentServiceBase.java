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

package de.adorsys.psd2.consent.api.service;

import de.adorsys.psd2.consent.api.CmsResponse;
import de.adorsys.psd2.consent.api.WrongChecksumException;
import de.adorsys.psd2.consent.api.ais.CmsConsent;
import de.adorsys.psd2.consent.api.consent.CmsCreateConsentResponse;
import de.adorsys.psd2.xs2a.core.consent.ConsentStatus;
import de.adorsys.psd2.xs2a.core.psu.PsuIdData;

import java.util.List;

/**
 * Base version of ConsentService that contains all method declarations.
 * Should not be implemented directly, consider using one of the interfaces that extends this one.
 *
 * @see ConsentService
 * @see ConsentServiceEncrypted
 */
interface ConsentServiceBase {

    /**
     * Create AIS consent
     *
     * @param consent needed parameters for creating AIS consent
     * @return create consent response, containing consent and its encrypted ID
     */
    CmsResponse<CmsCreateConsentResponse> createConsent(CmsConsent consent) throws WrongChecksumException;

    /**
     * Reads status of consent by id
     *
     * @param consentId id of consent
     * @return ConsentStatus
     */
    CmsResponse<ConsentStatus> getConsentStatusById(String consentId);

    /**
     * Updates consent status by id
     *
     * @param consentId id of consent
     * @param status    new consent status
     * @return Boolean
     */
    CmsResponse<Boolean> updateConsentStatusById(String consentId, ConsentStatus status) throws WrongChecksumException;

    /**
     * Reads full information of consent by id
     *
     * @param consentId id of consent
     * @return AisAccountConsent
     */
    CmsResponse<CmsConsent> getConsentById(String consentId);

    /**
     * Finds old consents for current TPP and PSU and terminates them.
     * This method should be invoked, when a new consent is authorised.
     *
     * @param newConsentId id of new consent
     * @return true if any consents have been terminated, false - if none
     */
    CmsResponse<Boolean> findAndTerminateOldConsentsByNewConsentId(String newConsentId);

//    /**
//     * Saves information about uses of consent
//     *
//     * @param request needed parameters for logging usage AIS consent
//     */
//    CmsResponse<CmsResponse.VoidResponse> checkConsentAndSaveActionLog(AisConsentActionRequest request) throws WrongChecksumException;
//
//
//    /**
//     * Updates AIS consent aspsp account access by id
//     *
//     * @param request   needed parameters for updating AIS consent
//     * @param consentId id of the consent to be updated
//     * @return String   consent id
//     */
//    CmsResponse<String> updateAspspAccountAccess(String consentId, AisAccountAccessInfo request) throws WrongChecksumException;
//
//    /**
//     * Updates AIS consent aspsp account access by id and return consent
//     *
//     * @param request   needed parameters for updating AIS consent
//     * @param consentId id of the consent to be updated
//     * @return AisAccountConsent consent
//     */
//    CmsResponse<AisAccountConsent> updateAspspAccountAccessWithResponse(String consentId, AisAccountAccessInfo request) throws WrongChecksumException;

    CmsResponse<List<PsuIdData>> getPsuDataByConsentId(String consentId);

    /**
     * Updates multilevel SCA required field
     *
     * @param consentId             String representation of the consent identifier
     * @param multilevelScaRequired multilevel SCA required indicator
     * @return <code>true</code> if authorisation was found and SCA required field updated, <code>false</code> otherwise
     */
    CmsResponse<Boolean> updateMultilevelScaRequired(String consentId, boolean multilevelScaRequired) throws WrongChecksumException;
}
