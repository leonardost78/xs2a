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

package de.adorsys.psd2.xs2a.service.authorization.processor;

import de.adorsys.psd2.xs2a.core.pis.PaymentAuthorisationType;
import de.adorsys.psd2.xs2a.core.profile.ScaApproach;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.service.authorization.processor.model.AisAuthorisationProcessorRequest;
import de.adorsys.psd2.xs2a.service.authorization.processor.model.AuthorisationProcessorResponse;
import de.adorsys.psd2.xs2a.service.authorization.processor.service.AisAuthorisationProcessorServiceImpl;
import de.adorsys.psd2.xs2a.service.authorization.processor.service.PisAuthorisationProcessorServiceImpl;
import de.adorsys.psd2.xs2a.service.authorization.processor.service.PisCancellationAuthorisationProcessorServiceImpl;
import de.adorsys.psd2.xs2a.service.mapper.psd2.ServiceType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AuthorisationProcessorTest {

    private AuthorisationProcessor authorisationProcessor;

    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private AuthorisationProcessor nextProcessor;
    @Mock
    private AisAuthorisationProcessorServiceImpl aisAuthorisationProcessorServiceImpl;
    @Mock
    private PisAuthorisationProcessorServiceImpl pisAuthorisationProcessorService;
    @Mock
    private PisCancellationAuthorisationProcessorServiceImpl pisCancellationAuthorisationProcessorServiceImpl;

    private AisAuthorisationProcessorRequest request;

    @Before
    public void setUp() {
        authorisationProcessor = new ReceivedAuthorisationProcessor(applicationContext);
        authorisationProcessor.setNext(nextProcessor);

        request = new AisAuthorisationProcessorRequest(ScaApproach.EMBEDDED, null, null, null);
    }

    @Test
    public void apply_currentProcessor() {
        request.setScaStatus(ScaStatus.RECEIVED);
        AuthorisationProcessorResponse processorResponse = new AuthorisationProcessorResponse();

        when(applicationContext.getBean(AisAuthorisationProcessorServiceImpl.class)).thenReturn(aisAuthorisationProcessorServiceImpl);
        when(aisAuthorisationProcessorServiceImpl.doScaReceived(request)).thenReturn(processorResponse);
        doNothing().when(aisAuthorisationProcessorServiceImpl).updateAuthorisation(request, processorResponse);

        authorisationProcessor.apply(request);

        verify(applicationContext, times(2)).getBean(AisAuthorisationProcessorServiceImpl.class);
        verify(aisAuthorisationProcessorServiceImpl, times(1)).doScaReceived(request);
        verify(aisAuthorisationProcessorServiceImpl, times(1)).updateAuthorisation(request, processorResponse);
    }

    @Test
    public void apply_nextProcessor() {
        request.setScaStatus(ScaStatus.PSUIDENTIFIED);
        AuthorisationProcessorResponse processorResponse = new AuthorisationProcessorResponse();

        when(nextProcessor.process(request)).thenReturn(processorResponse);

        when(applicationContext.getBean(AisAuthorisationProcessorServiceImpl.class)).thenReturn(aisAuthorisationProcessorServiceImpl);
        doNothing().when(aisAuthorisationProcessorServiceImpl).updateAuthorisation(request, processorResponse);

        authorisationProcessor.apply(request);

        verify(nextProcessor, times(1)).process(request);
        verify(applicationContext, times(1)).getBean(AisAuthorisationProcessorServiceImpl.class);
        verify(aisAuthorisationProcessorServiceImpl, times(1)).updateAuthorisation(request, processorResponse);
    }

    @Test
    public void getProcessorService_AIS() {
        request.setServiceType(ServiceType.AIS);
        when(applicationContext.getBean(AisAuthorisationProcessorServiceImpl.class)).thenReturn(aisAuthorisationProcessorServiceImpl);

        authorisationProcessor.getProcessorService(request);

        verify(applicationContext, times(1)).getBean(AisAuthorisationProcessorServiceImpl.class);
    }

    @Test
    public void getProcessorService_PIS_initiation() {
        request.setServiceType(ServiceType.PIS);
        request.setPaymentAuthorisationType(PaymentAuthorisationType.CREATED);
        when(applicationContext.getBean(PisAuthorisationProcessorServiceImpl.class)).thenReturn(pisAuthorisationProcessorService);

        authorisationProcessor.getProcessorService(request);

        verify(applicationContext, times(1)).getBean(PisAuthorisationProcessorServiceImpl.class);
    }

    @Test
    public void getProcessorService_PIS_cancellation() {
        request.setServiceType(ServiceType.PIS);
        request.setPaymentAuthorisationType(PaymentAuthorisationType.CANCELLED);
        when(applicationContext.getBean(PisCancellationAuthorisationProcessorServiceImpl.class)).thenReturn(pisCancellationAuthorisationProcessorServiceImpl);

        authorisationProcessor.getProcessorService(request);

        verify(applicationContext, times(1)).getBean(PisCancellationAuthorisationProcessorServiceImpl.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getProcessorService_PIS_noPaymentAuthorisationType() {
        request.setServiceType(ServiceType.PIS);
        request.setPaymentAuthorisationType(null);

        authorisationProcessor.getProcessorService(request);

        verify(applicationContext, never()).getBean(anyString());
    }

    @Test
    public void process_nextProcessorIsNotSet() {
        request.setScaStatus(ScaStatus.PSUIDENTIFIED);
        authorisationProcessor.setNext(null);

        assertNull(authorisationProcessor.process(request));
    }
}