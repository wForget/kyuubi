/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kyuubi.plugin.lineage.dispatcher.atlas

import java.util.Locale

import org.apache.atlas.AtlasClientV2
import org.apache.atlas.model.instance.AtlasEntity
import org.apache.atlas.model.instance.AtlasEntity.AtlasEntitiesWithExtInfo
import org.apache.commons.lang3.StringUtils

import org.apache.kyuubi.plugin.lineage.dispatcher.atlas.AtlasClientConf._

trait AtlasClient {
  def send(entities: Seq[AtlasEntity]): Unit
}

class AtlasRestClient(conf: AtlasClientConf) extends AtlasClient {

  private val atlasClient: AtlasClientV2 = {
    val serverUrl = conf.get(ATLAS_REST_ENDPOINT).split(",")
    val username = conf.get(CLIENT_USERNAME)
    val password = conf.get(CLIENT_PASSWORD)
    if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
      new AtlasClientV2(serverUrl, Array(username, password))
    } else {
      new AtlasClientV2(serverUrl: _*)
    }
  }

  override def send(entities: Seq[AtlasEntity]): Unit = {
    val entitiesWithExtInfo = new AtlasEntitiesWithExtInfo()
    entities.foreach(entitiesWithExtInfo.addEntity)
    atlasClient.createEntities(entitiesWithExtInfo)
  }
}

object AtlasClient {

  private lazy val client: AtlasClient = {
    val clientConf = AtlasClientConf.getConf()
    clientConf.get(CLIENT_TYPE).toLowerCase(Locale.ROOT) match {
      case "rest" => new AtlasRestClient(clientConf)
      case t => throw new RuntimeException(s"Unsupported client type: $t.")
    }
  }

  def getClient(): AtlasClient = client

}
