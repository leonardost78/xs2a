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

package de.adorsys.psd2.stub.impl;

import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiConfirmationCode;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.*;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.SinglePaymentSpi;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class SinglePaymentSpiMockImpl implements SinglePaymentSpi {
    private static final String TEST_ASPSP_DATA = "Test aspsp data";

    @Override
    @NotNull
    public SpiResponse<SpiSinglePaymentInitiationResponse> initiatePayment(@NotNull SpiContextData contextData, @NotNull SpiSinglePayment payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        log.info("SinglePaymentSpi#initiatePayment: contextData {}, spiSinglePayment {}, aspspConsentData {}", contextData, payment, aspspConsentDataProvider.loadAspspConsentData());
        SpiSinglePaymentInitiationResponse response = new SpiSinglePaymentInitiationResponse();
        response.setTransactionStatus(TransactionStatus.RCVD);
        response.setPaymentId(UUID.randomUUID().toString());
        response.setAspspAccountId("11111-11118");
        aspspConsentDataProvider.updateAspspConsentData(TEST_ASPSP_DATA.getBytes());

        return SpiResponse.<SpiSinglePaymentInitiationResponse>builder()
                   .payload(response)
                   .build();
    }

    @Override
    @NotNull
    public SpiResponse<SpiSinglePayment> getPaymentById(@NotNull SpiContextData contextData, @NotNull String acceptMediaType, @NotNull SpiSinglePayment payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        log.info("SinglePaymentSpi#getPaymentById: contextData {}, spiSinglePayment {}, aspspConsentData {}", contextData, payment, aspspConsentDataProvider.loadAspspConsentData());

        return SpiResponse.<SpiSinglePayment>builder()
                   .payload(payment)
                   .build();
    }

    @Override
    @NotNull
    public SpiResponse<SpiGetPaymentStatusResponse> getPaymentStatusById(@NotNull SpiContextData contextData, @NotNull String acceptMediaType, @NotNull SpiSinglePayment payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        log.info("SinglePaymentSpi#getPaymentStatusById: contextData {}, spiSinglePayment {}, aspspConsentData {}", contextData, payment, aspspConsentDataProvider.loadAspspConsentData());

        return SpiResponse.<SpiGetPaymentStatusResponse>builder()
                   .payload(new SpiGetPaymentStatusResponse(payment.getPaymentStatus(), true, SpiGetPaymentStatusResponse.RESPONSE_TYPE_JSON, null))
                   .build();
    }

    @Override
    @NotNull
    public SpiResponse<SpiPaymentExecutionResponse> executePaymentWithoutSca(@NotNull SpiContextData contextData, @NotNull SpiSinglePayment payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        log.info("SinglePaymentSpi#executePaymentWithoutSca: contextData {}, spiSinglePayment {}, aspspConsentData {}", contextData, payment, aspspConsentDataProvider.loadAspspConsentData());

        return SpiResponse.<SpiPaymentExecutionResponse>builder()
                   .payload(new SpiPaymentExecutionResponse(TransactionStatus.ACCP))
                   .build();
    }

    @Override
    @NotNull
    public SpiResponse<SpiPaymentExecutionResponse> verifyScaAuthorisationAndExecutePayment(@NotNull SpiContextData contextData, @NotNull SpiScaConfirmation spiScaConfirmation, @NotNull SpiSinglePayment payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        log.info("SinglePaymentSpi#verifyScaAuthorisationAndExecutePayment: contextData {}, spiScaConfirmation{}, spiSinglePayment {}, aspspConsentData {}", contextData, spiScaConfirmation, payment, aspspConsentDataProvider.loadAspspConsentData());

        return SpiResponse.<SpiPaymentExecutionResponse>builder()
                   .payload(new SpiPaymentExecutionResponse(TransactionStatus.ACCP))
                   .build();
    }

    @Override
    public @NotNull SpiResponse<SpiConfirmationCodeCheckingResponse> checkConfirmationCode(@NotNull SpiContextData contextData, @NotNull SpiConfirmationCode spiConfirmationCode, @NotNull SpiSinglePayment payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        log.info("SinglePaymentSpi#checkConfirmationCode: contextData {}, spiConfirmationCode{}, spiSinglePayment {}, aspspConsentData {}", contextData, spiConfirmationCode.getConfirmationCode(), payment, aspspConsentDataProvider.loadAspspConsentData());

        return SpiResponse.<SpiConfirmationCodeCheckingResponse>builder()
                   .payload(new SpiConfirmationCodeCheckingResponse(ScaStatus.FINALISED))
                   .build();
    }

    @Override
    public @NotNull SpiResponse<SpiPaymentConfirmationCodeValidationResponse> notifyConfirmationCodeValidation(@NotNull SpiContextData contextData, boolean confirmationCodeValidationResult, @NotNull SpiSinglePayment payment, boolean isCancellation, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        ScaStatus scaStatus = confirmationCodeValidationResult ? ScaStatus.FINALISED : ScaStatus.FAILED;
        TransactionStatus transactionStatus = isCancellation
                                                  ? confirmationCodeValidationResult ? TransactionStatus.CANC : payment.getPaymentStatus()
                                                  : confirmationCodeValidationResult ? TransactionStatus.ACSP : TransactionStatus.RJCT;

        SpiPaymentConfirmationCodeValidationResponse response = new SpiPaymentConfirmationCodeValidationResponse(scaStatus, transactionStatus);

        return SpiResponse.<SpiPaymentConfirmationCodeValidationResponse>builder()
                   .payload(response)
                   .build();
    }
}
