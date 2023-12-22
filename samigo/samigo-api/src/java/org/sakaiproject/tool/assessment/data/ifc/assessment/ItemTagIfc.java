/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2004, 2005, 2006, 2008 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.tool.assessment.data.ifc.assessment;

import java.io.Serializable;

//TODO: Should extend TagIfc
public interface ItemTagIfc extends Serializable {

    Long getId();

    void setId(Long id);

    ItemDataIfc getItem();

    void setItem(ItemDataIfc item);

    String getTagId();

    void setTagId(String tagId);

    String getTagLabel();

    void setTagLabel(String tagLabel);

    String getTagCollectionId();

    void setTagCollectionId(String tagCollectionId);

    String getTagCollectionName();

    void setTagCollectionName(String tagCollectionName);

}
