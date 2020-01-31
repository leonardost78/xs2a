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

package de.adorsys.psd2.consent.service.mapper;

import de.adorsys.psd2.consent.domain.payment.PisAuthorization;
import de.adorsys.psd2.xs2a.core.authorisation.Authorisation;
import de.adorsys.xs2a.reader.JsonReader;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CmsAuthorisationMapperTest {
    private CmsAuthorisationMapper cmsAuthorisationMapper = new CmsAuthorisationMapper(new PsuDataMapper());

    private JsonReader jsonReader = new JsonReader();

    @Test
    void mapToAuthorisations() {
        // Given
        PisAuthorization pisAuthorisation = jsonReader.getObjectFromFile("json/service/mapper/pis-authorisation.json", PisAuthorization.class);
        Authorisation expectedAuthorisation = jsonReader.getObjectFromFile("json/service/mapper/authorisation.json", Authorisation.class);

        // When
        List<Authorisation> actual = cmsAuthorisationMapper.mapToAuthorisations(Collections.singletonList(pisAuthorisation));

        // Then
        assertEquals(Collections.singletonList(expectedAuthorisation), actual);
    }
}
