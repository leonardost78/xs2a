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

package de.adorsys.psd2.xs2a.service.validator.ais.account;

import de.adorsys.psd2.core.data.ais.AccountAccess;
import de.adorsys.psd2.core.data.ais.AisConsent;
import de.adorsys.psd2.core.data.ais.AisConsentData;
import de.adorsys.psd2.xs2a.core.consent.ConsentTppInformation;
import de.adorsys.psd2.xs2a.core.domain.TppMessageInformation;
import de.adorsys.psd2.xs2a.core.error.ErrorType;
import de.adorsys.psd2.xs2a.core.error.MessageError;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.profile.AccountReference;
import de.adorsys.psd2.xs2a.core.tpp.TppInfo;
import de.adorsys.psd2.xs2a.service.validator.OauthConsentValidator;
import de.adorsys.psd2.xs2a.service.validator.ValidationResult;
import de.adorsys.psd2.xs2a.service.validator.ais.account.common.AccountConsentValidator;
import de.adorsys.psd2.xs2a.service.validator.ais.account.dto.GetCardAccountListConsentObject;
import de.adorsys.psd2.xs2a.service.validator.tpp.AisAccountTppInfoValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static de.adorsys.psd2.xs2a.core.error.ErrorType.AIS_401;
import static de.adorsys.psd2.xs2a.core.error.MessageErrorCode.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCardAccountListValidatorTest {
    private static final String REQUEST_URI = "/accounts";
    private static final String IBAN = "DE62500105179972514662";
    private static final String MASKED_PAN = "493702******0836";
    private static final TppInfo TPP_INFO = buildTppInfo("authorisation number");
    private static final TppInfo INVALID_TPP_INFO = buildTppInfo("invalid authorisation number");
    private static final MessageError TPP_VALIDATION_ERROR = new MessageError(ErrorType.AIS_401, TppMessageInformation.of(UNAUTHORIZED));
    private static final MessageError ACCESS_VALIDATION_ERROR = new MessageError(ErrorType.AIS_401, TppMessageInformation.of(CONSENT_INVALID));
    private static final MessageError FORBIDDEN_ERROR = new MessageError(ErrorType.AIS_403, TppMessageInformation.of(MessageErrorCode.FORBIDDEN));

    @InjectMocks
    private GetCardAccountListValidator getCardAccountListValidator;
    @Mock
    private AccountConsentValidator accountConsentValidator;
    @Mock
    private OauthConsentValidator oauthConsentValidator;
    @Mock
    private AisAccountTppInfoValidator aisAccountTppInfoValidator;

    @BeforeEach
    void setUp() {
        // Inject pisTppInfoValidator via setter
        getCardAccountListValidator.setAisAccountTppInfoValidator(aisAccountTppInfoValidator);
    }

    @Test
    void validate_withValidConsentObject_maskedPan_shouldReturnValid() {
        // Given
        AccountAccess accountAccess = buildXs2aAccountAccessWithMaskedPan(MASKED_PAN);
        AisConsent aisConsent = buildAccountConsent(accountAccess, TPP_INFO);
        when(accountConsentValidator.validate(aisConsent, REQUEST_URI)).thenReturn(ValidationResult.valid());
        when(oauthConsentValidator.validate(aisConsent)).thenReturn(ValidationResult.valid());
        when(aisAccountTppInfoValidator.validateTpp(TPP_INFO)).thenReturn(ValidationResult.valid());

        // When
        ValidationResult validationResult = getCardAccountListValidator.validate(new GetCardAccountListConsentObject(aisConsent, REQUEST_URI));

        // Then
        assertNotNull(validationResult);
        assertTrue(validationResult.isValid());
        assertNull(validationResult.getMessageError());
    }

    @Test
    void validate_withValidConsentObject_Iban_shouldReturnInvalid() {
        // Given
        AccountAccess accountAccess = buildXs2aAccountAccessWithIban(IBAN);
        AisConsent aisConsent = buildAccountConsent(accountAccess, TPP_INFO);
        when(aisAccountTppInfoValidator.validateTpp(TPP_INFO)).thenReturn(ValidationResult.valid());

        // When
        ValidationResult validationResult = getCardAccountListValidator.validate(new GetCardAccountListConsentObject(aisConsent, REQUEST_URI));

        // Then
        assertNotNull(validationResult);
        assertTrue(validationResult.isNotValid());
        assertEquals(ACCESS_VALIDATION_ERROR, validationResult.getMessageError());
    }

    @Test
    void validate_withValidConsentObject_mixedAccountReference_shouldReturnValid() {
        // Given
        AccountAccess accountAccess = buildXs2aAccountAccess(false, IBAN, MASKED_PAN);
        AisConsent aisConsent = buildAccountConsent(accountAccess, TPP_INFO);
        when(accountConsentValidator.validate(aisConsent, REQUEST_URI)).thenReturn(ValidationResult.valid());
        when(oauthConsentValidator.validate(aisConsent)).thenReturn(ValidationResult.valid());
        when(aisAccountTppInfoValidator.validateTpp(TPP_INFO)).thenReturn(ValidationResult.valid());

        // When
        GetCardAccountListConsentObject getCardAccountListConsentObject = new GetCardAccountListConsentObject(aisConsent, REQUEST_URI);
        ValidationResult validationResult = getCardAccountListValidator.validate(getCardAccountListConsentObject);

        // Then
        assertNotNull(validationResult);
        assertTrue(validationResult.isValid());
        assertNull(validationResult.getMessageError());
    }

    @Test
    void validate_withInvalidTppInConsent_shouldReturnTppValidationError() {
        // Given
        AccountAccess accountAccess = buildXs2aAccountAccessWithMaskedPan(null);
        AisConsent aisConsent = buildAccountConsent(accountAccess, INVALID_TPP_INFO);
        when(aisAccountTppInfoValidator.validateTpp(INVALID_TPP_INFO)).thenReturn(ValidationResult.invalid(TPP_VALIDATION_ERROR));

        // When
        GetCardAccountListConsentObject getCardAccountListConsentObject = new GetCardAccountListConsentObject(aisConsent, REQUEST_URI);
        ValidationResult validationResult = getCardAccountListValidator.validate(getCardAccountListConsentObject);

        // Then
        verify(aisAccountTppInfoValidator).validateTpp(aisConsent.getTppInfo());

        assertNotNull(validationResult);
        assertTrue(validationResult.isNotValid());
        assertEquals(TPP_VALIDATION_ERROR, validationResult.getMessageError());
    }

    @Test
    void validate_withInvalidConsent_shouldReturnInvalid() {
        // Given
        AccountAccess accountAccess = buildXs2aAccountAccessWithMaskedPan(MASKED_PAN);
        AisConsent aisConsent = buildAccountConsent(accountAccess, TPP_INFO);
        when(aisAccountTppInfoValidator.validateTpp(TPP_INFO)).thenReturn(ValidationResult.valid());
        ValidationResult validationResultExpected = ValidationResult.invalid(AIS_401, CONSENT_EXPIRED);
        when(accountConsentValidator.validate(aisConsent, REQUEST_URI)).thenReturn(validationResultExpected);

        // When
        ValidationResult validationResult = getCardAccountListValidator.validate(new GetCardAccountListConsentObject(aisConsent, REQUEST_URI));

        // Then
        assertNotNull(validationResult);
        assertTrue(validationResult.isNotValid());
        assertEquals(validationResultExpected.getMessageError(), validationResult.getMessageError());
    }

    @Test
    void validate_withOauthConcentInvalid_shouldReturnInvalid() {
        // Given
        AccountAccess accountAccess = buildXs2aAccountAccessWithMaskedPan(MASKED_PAN);
        AisConsent aisConsent = buildAccountConsent(accountAccess, TPP_INFO);
        when(aisAccountTppInfoValidator.validateTpp(TPP_INFO)).thenReturn(ValidationResult.valid());
        when(accountConsentValidator.validate(aisConsent, REQUEST_URI)).thenReturn(ValidationResult.valid());
        when(oauthConsentValidator.validate(aisConsent)).thenReturn(ValidationResult.invalid(FORBIDDEN_ERROR));

        // When
        ValidationResult validationResult = getCardAccountListValidator.validate(new GetCardAccountListConsentObject(aisConsent, REQUEST_URI));

        // Then
        assertNotNull(validationResult);
        assertTrue(validationResult.isNotValid());
        assertEquals(FORBIDDEN_ERROR, validationResult.getMessageError());
    }

    private AisConsent buildAccountConsent(AccountAccess accountAccess, TppInfo tppInfo) {
        AisConsent aisConsent = new AisConsent();
        ConsentTppInformation consentTppInformation = new ConsentTppInformation();
        consentTppInformation.setTppInfo(tppInfo);
        aisConsent.setConsentData(new AisConsentData(accountAccess, accountAccess, false));
        aisConsent.setConsentTppInformation(consentTppInformation);
        return aisConsent;
    }

    private AccountAccess buildXs2aAccountAccessWithIban(String iban) {
        return buildXs2aAccountAccess(false, iban, null);
    }

    private AccountAccess buildXs2aAccountAccessWithMaskedPan(String maskedPan) {
        return buildXs2aAccountAccess(false, null, maskedPan);
    }

    private AccountAccess buildXs2aAccountAccess(boolean withBalancesAccess, String iban, String maskedPan) {
        AccountReference accountReference = new AccountReference();
        accountReference.setIban(iban);
        accountReference.setMaskedPan(maskedPan);

        List<AccountReference> accountReferences = Collections.singletonList(accountReference);
        List<AccountReference> balances = withBalancesAccess
                                              ? accountReferences
                                              : Collections.emptyList();

        return new AccountAccess(accountReferences, balances, accountReferences, null, null, null, null);
    }

    private static TppInfo buildTppInfo(String authorisationNumber) {
        TppInfo tppInfo = new TppInfo();
        tppInfo.setAuthorisationNumber(authorisationNumber);
        return tppInfo;
    }
}
