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

package de.adorsys.psd2.xs2a.web.validator;

import de.adorsys.psd2.xs2a.exception.MessageError;
import de.adorsys.psd2.xs2a.web.validator.header.HeaderValidator;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractMethodValidator implements MethodValidator {

    protected abstract List<HeaderValidator> getHeaderValidators();

    protected abstract List<HeaderValidator> getBodyValidators();

    @Override
    public void validate(HttpServletRequest request, MessageError messageError) {
        Map<String, String> headers = Collections.list(request.getHeaderNames())
                                          .stream()
                                          .collect(Collectors.toMap(h -> h, request::getHeader));

        getHeaderValidators().forEach(v -> v.validate(headers, request, messageError));
        getBodyValidators().forEach(v -> v.validate(headers, request, messageError));
    }
}
