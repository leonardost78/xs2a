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

package de.adorsys.psd2.consent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.client.DefaultResponseErrorHandler;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
public class ConsentRestErrorHandler extends DefaultResponseErrorHandler {
    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        HttpStatus statusCode = response.getStatusCode();

        byte[] textInBytes = new byte[]{};

        try {
            InputStream responseBody = response.getBody();
            textInBytes = FileCopyUtils.copyToByteArray(responseBody);

        } catch (IOException ex) {
            log.error("Error during handling REST error from CMS.");
        }

        if (statusCode.value() == 404) {
            throw new CmsRestException(statusCode);
        }
        throw new CmsRestException(statusCode, new String(textInBytes).replaceAll("\"", ""));
    }
}
