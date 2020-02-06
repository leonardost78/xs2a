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

package de.adorsys.psd2.xs2a.web.aspect;

import de.adorsys.psd2.core.data.ais.AccountAccess;
import de.adorsys.psd2.core.data.ais.AisConsent;
import de.adorsys.psd2.xs2a.core.consent.AisConsentRequestType;
import de.adorsys.psd2.xs2a.domain.ResponseObject;
import de.adorsys.psd2.xs2a.domain.account.*;
import de.adorsys.psd2.xs2a.service.profile.AspspProfileServiceWrapper;
import de.adorsys.psd2.xs2a.web.controller.CardAccountController;
import de.adorsys.psd2.xs2a.web.link.CardAccountDetailsLinks;
import de.adorsys.psd2.xs2a.web.link.TransactionsReportCardDownloadLinks;
import de.adorsys.psd2.xs2a.web.link.TransactionsReportCardLinks;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Aspect
@Component
public class CardAccountAspect extends AbstractLinkAspect<CardAccountController> {

    public CardAccountAspect(AspspProfileServiceWrapper aspspProfileServiceWrapper) {
        super(aspspProfileServiceWrapper);
    }

    @AfterReturning(pointcut = "execution(* de.adorsys.psd2.xs2a.service.ais.CardAccountService.getCardAccountList(..))", returning = "result")
    public ResponseObject<Xs2aCardAccountListHolder> getCardAccountList(ResponseObject<Xs2aCardAccountListHolder> result) {
        if (!result.hasError()) {
            Xs2aCardAccountListHolder body = result.getBody();
            List<Xs2aCardAccountDetails> cardAccountDetails = body.getCardAccountDetails();
            AisConsent aisConsent = body.getAisConsent();
            if (aisConsent.getAisConsentRequestType() == AisConsentRequestType.ALL_AVAILABLE_ACCOUNTS) {
                cardAccountDetails.forEach(acc -> acc.setLinks(null));
            } else {
                cardAccountDetails.forEach(acc -> setLinksForCardAccountDetails(acc, body.getAisConsent().getAccess()));
            }
        }
        return result;
    }

    @AfterReturning(pointcut = "execution(* de.adorsys.psd2.xs2a.service.ais.CardAccountService.getCardAccountDetails(..))", returning = "result")
    public ResponseObject<Xs2aCardAccountDetailsHolder> getCardAccountDetails(ResponseObject<Xs2aCardAccountDetailsHolder> result) {
        if (!result.hasError()) {
            Xs2aCardAccountDetailsHolder body = result.getBody();
            setLinksForCardAccountDetails(body.getCardAccountDetails(), body.getAisConsent().getAccess());
        }
        return result;
    }

    @AfterReturning(pointcut = "execution(* de.adorsys.psd2.xs2a.service.ais.CardTransactionService.getCardTransactionsReportByPeriod(..)) && args(request)", returning = "result", argNames = "result,request")
    public ResponseObject<Xs2aCardTransactionsReport> getTransactionsReportByPeriod(ResponseObject<Xs2aCardTransactionsReport> result, Xs2aTransactionsReportByPeriodRequest request) {
        if (!result.hasError()) {
            Xs2aCardTransactionsReport transactionsReport = result.getBody();
            boolean isWithBalance = Optional.ofNullable(transactionsReport.getBalances())
                                        .map(balances -> !balances.isEmpty())
                                        .orElse(false);
            transactionsReport.setLinks(new TransactionsReportCardDownloadLinks(getHttpUrl(), request.getAccountId(), isWithBalance, transactionsReport.getDownloadId()));
            Xs2aCardAccountReport cardAccountReport = transactionsReport.getCardAccountReport();
            if (cardAccountReport != null) {
                cardAccountReport.setLinks(new TransactionsReportCardLinks(getHttpUrl(), request.getAccountId(), isWithBalance));
            }
        }
        return result;
    }

    private void setLinksForCardAccountDetails(Xs2aCardAccountDetails cardAccountDetails, AccountAccess accountAccess) {
        String url = getHttpUrl();
        String id = cardAccountDetails.getResourceId();
        CardAccountDetailsLinks cardAccountDetailsLinks = new CardAccountDetailsLinks(url,id, accountAccess);
        cardAccountDetails.setLinks(cardAccountDetailsLinks);
    }
}
