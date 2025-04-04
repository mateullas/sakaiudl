/**
 * Copyright (c) 2024 The Apereo Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://opensource.org/licenses/ecl2
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sakaiproject.microsoft.impl;

import org.sakaiproject.microsoft.api.MicrosoftLoggingService;
import org.sakaiproject.microsoft.api.model.MicrosoftLog;
import org.sakaiproject.microsoft.api.persistence.MicrosoftLoggingRepository;
import org.springframework.transaction.annotation.Transactional;

import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.time.ZonedDateTime;
import java.util.List;

@Log4j2
@Transactional
public class MicrosoftLoggingServiceImpl implements MicrosoftLoggingService {
	
	
	@Setter
	MicrosoftLoggingRepository microsoftLoggingRepository;

	@Override
	public void saveLog(MicrosoftLog log) {
		microsoftLoggingRepository.save(log);
	}

	@Override
	public List<MicrosoftLog> findAll() {
		return (List<MicrosoftLog>) microsoftLoggingRepository.findAll();
	}

	@Override
	public List<MicrosoftLog> findFromZonedDateTime(ZonedDateTime zonedDateTime) {
		return (List<MicrosoftLog>) microsoftLoggingRepository.getLogsFromZonedDateTime(zonedDateTime);
	}
	
}
