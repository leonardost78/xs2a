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

package de.adorsys.psd2.xs2a.web.filter;

import de.adorsys.psd2.xs2a.web.Xs2aEndpointChecker;
import de.adorsys.psd2.xs2a.web.error.TppErrorMessageWriter;

import javax.servlet.http.HttpServletRequest;

/**
 * Abstract filter that will be executed only once and will be applied only to XS2A endpoints.
 */
public abstract class AbstractXs2aFilter extends GlobalAbstractExceptionFilter {
    private final Xs2aEndpointChecker xs2aEndpointChecker;

    protected AbstractXs2aFilter(TppErrorMessageWriter tppErrorMessageWriter, Xs2aEndpointChecker xs2aEndpointChecker) {
        super(tppErrorMessageWriter);
        this.xs2aEndpointChecker = xs2aEndpointChecker;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !xs2aEndpointChecker.isXs2aEndpoint(request);
    }
}
