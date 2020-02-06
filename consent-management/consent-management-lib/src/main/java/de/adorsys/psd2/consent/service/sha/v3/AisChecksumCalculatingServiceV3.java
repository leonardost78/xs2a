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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.adorsys.psd2.consent.service.sha.ChecksumConstant;
import de.adorsys.psd2.consent.service.sha.ChecksumCalculatingService;
import de.adorsys.psd2.consent.service.sha.Sha512HashingService;
import de.adorsys.psd2.core.data.Consent;
import de.adorsys.psd2.core.data.ais.AccountAccess;
import de.adorsys.psd2.core.data.ais.AisConsent;
import de.adorsys.psd2.xs2a.core.consent.ConsentType;
import de.adorsys.psd2.xs2a.core.profile.AccountReference;
import de.adorsys.psd2.xs2a.core.profile.AccountReferenceType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class AisChecksumCalculatingServiceV3 implements ChecksumCalculatingService {
    private static final String VERSION = "003";
    private static final Charset CHARSET = Charset.defaultCharset();
    private final Sha512HashingService hashingService = new Sha512HashingService();
    private final ObjectMapper objectMapper = buildObjectMapper();

    @Override
    public boolean verifyConsentWithChecksum(Consent<?> consent, byte[] checksum) {
        if (consent == null || checksum == null) {
            return false;
        }

        if (ConsentType.AIS == consent.getConsentType()) {
            AisConsent aisConsent = (AisConsent) consent;

            return verifyConsentWithChecksumForAisConsent(aisConsent, checksum);
        }

        return false;
    }

    @Override
    public byte[] calculateChecksumForConsent(Consent<?> consent) {
        if (consent == null) {
            return new byte[0];
        }

        if (ConsentType.AIS == consent.getConsentType()) {

            AisConsent aisConsent = (AisConsent) consent;

            return calculateChecksumForAisConsent(aisConsent);
        }

        return new byte[0];
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    private boolean verifyConsentWithChecksumForAisConsent(AisConsent aisConsent, byte[] checksum) {
        String checksumStr = new String(checksum);
        String[] elements = checksumStr.split(ChecksumConstant.DELIMITER);

        if (elements.length == 1) {
            return false;
        }

        String consentChecksumFromDb = elements[ChecksumConstant.CONSENT_CHECKSUM_START_POSITION];
        String aisConsentChecksumCommon = calculateChecksumForAisConsentCommon(aisConsent);

        if (!consentChecksumFromDb.equals(aisConsentChecksumCommon)) {
            return false;
        }

        return isAspspAccessesChecksumValid(elements, aisConsent.getConsentData().getAspspAccountAccess());
    }

    private boolean isAspspAccessesChecksumValid(String[] elements, AccountAccess aspspAccess) {

        if (elements.length > 2) {
            String aspspAccessFromDb = elements[ChecksumConstant.ASPSP_ACCESS_CHECKSUM_START_POSITION];

            Map<AccountReferenceType, String> accessChecksumMapFromDB = getChecksumMapFromEncodedString(aspspAccessFromDb);
            Map<AccountReferenceType, String> currentAccessChecksumMap = calculateChecksumMapByReferenceType(aspspAccess);

            return areCurrentAccessesValid(accessChecksumMapFromDB, currentAccessChecksumMap);
        }

        return true;
    }

    private byte[] calculateChecksumForAisConsent(AisConsent aisConsent) {
        StringBuilder sb = new StringBuilder(VERSION).append(ChecksumConstant.DELIMITER);

        String aisConsentChecksumCommon = calculateChecksumForAisConsentCommon(aisConsent);
        sb.append(aisConsentChecksumCommon);

        AccountAccess aspspAccountAccess = aisConsent.getConsentData().getAspspAccountAccess();
        if (aspspAccountAccess.isNotEmpty()) {

            Map<AccountReferenceType, String> checksumMap = calculateChecksumMapByReferenceType(aspspAccountAccess);

            if (!checksumMap.isEmpty()) {
                byte[] checksumMapInBytes = getBytesFromObject(checksumMap);
                String aspspAccessChecksumEncodedString = Base64.getEncoder().encodeToString(checksumMapInBytes);
                sb.append(ChecksumConstant.DELIMITER);
                sb.append(aspspAccessChecksumEncodedString);
            }
        }

        return sb.toString().getBytes();
    }

    private Map<AccountReferenceType, String> getChecksumMapFromEncodedString(String aspspAccessFromDb) {
        byte[] decodedString = Base64.getDecoder().decode(aspspAccessFromDb);

        return getChecksumMapFromBytes(decodedString);
    }

    private String calculateChecksumForAisConsentCommon(AisConsent aisConsent) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("recurringIndicator", aisConsent.isRecurringIndicator());
        map.put("combinedServiceIndicator", aisConsent.getConsentData().isCombinedServiceIndicator());
        map.put("validUntil", aisConsent.getValidUntil());
        map.put("tppFrequencyPerDay", aisConsent.getFrequencyPerDay());
        map.put("accesses", aisConsent.getConsentData().getTppAccountAccess());

        byte[] consentAsBytes = getBytesFromObject(map);

        byte[] consentChecksum = calculateChecksum(consentAsBytes);
        return Base64.getEncoder().encodeToString(consentChecksum);
    }

    private Map<AccountReferenceType, String> calculateChecksumMapByReferenceType(AccountAccess aspspAccess) {
        Map<AccountReferenceType, String> checkSumMap = new LinkedHashMap<>();

        for (AccountReferenceType type : AccountReferenceType.values()) {
            String checksumByType = getChecksumByType(aspspAccess, type);
            if (checksumByType != null) {
                checkSumMap.put(type, checksumByType);
            }
        }

        return checkSumMap;
    }

    private String getChecksumByType(AccountAccess aspspAccess, AccountReferenceType type) {
        Set<AccountReference> references = Stream.of(aspspAccess.getAccounts(), aspspAccess.getBalances(), aspspAccess.getTransactions())
                                               .filter(Objects::nonNull)
                                               .flatMap(Collection::stream)
                                               .collect(Collectors.toSet());

        List<AccountReference> filtered = references.stream()
                                              .filter(acc -> acc.getUsedAccountReferenceSelector().getAccountReferenceType() == type)
                                              .filter(acc -> StringUtils.isNotBlank(acc.getResourceId()) || StringUtils.isNotBlank(acc.getAspspAccountId()))
                                              .sorted(Comparator.comparing(AccountReference::getAccountReferenceType)
                                                          .thenComparing(acc -> Optional.ofNullable(acc.getCurrency())
                                                                                    .map(Currency::getCurrencyCode)
                                                                                    .orElse(null)))
                                              .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(filtered)) {
            return null;
        }

        byte[] consentRefsAsBytes = getBytesFromObject(filtered);
        byte[] consentRefsChecksum = calculateChecksum(consentRefsAsBytes);
        return Base64.getEncoder().encodeToString(consentRefsChecksum);
    }

    private boolean areCurrentAccessesValid(Map<AccountReferenceType, String> accessMapFromDB, Map<AccountReferenceType, String> currentAccessMap) {
        if (accessMapFromDB == null || currentAccessMap == null) {
            return false;
        }

        return accessMapFromDB.entrySet().stream()
                   .allMatch(ent -> Optional.ofNullable(ent.getValue())
                                        .map(v -> v.equals(currentAccessMap.get(ent.getKey())))
                                        .orElse(false));
    }

    private byte[] calculateChecksum(byte[] checksumSource) {
        return hashingService.hash(checksumSource, CHARSET);
    }

    private Map<AccountReferenceType, String> getChecksumMapFromBytes(byte[] bytes) {
        try {
            return objectMapper.readValue(bytes, new TypeReference<LinkedHashMap<AccountReferenceType, String>>() {
            });
        } catch (IOException e) {
            return Collections.emptyMap();
        }
    }

    private byte[] getBytesFromObject(Object inputValue) {
        try {
            return objectMapper.writeValueAsBytes(inputValue);
        } catch (JsonProcessingException e) {
            return new byte[0];
        }
    }

    private ObjectMapper buildObjectMapper() {
        ObjectMapper localObjectMapper = new ObjectMapper();
        localObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        localObjectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        localObjectMapper.registerModule(new JavaTimeModule()); // add support for java.time types
        return localObjectMapper;
    }
}
