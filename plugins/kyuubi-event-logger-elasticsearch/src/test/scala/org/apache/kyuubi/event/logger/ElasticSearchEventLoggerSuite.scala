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

package org.apache.kyuubi.event.logger

import org.apache.kyuubi.config.KyuubiConf
import org.apache.kyuubi.event.logger.ElasticSearchEventLoggerConf._
import org.apache.kyuubi.events.KyuubiEvent


class ElasticSearchEventLoggerSuite extends WithElasticsearchContainer {

  val conf = KyuubiConf()

  test("log event into elasticsearch") {
    val logger = new ElasticSearchEventLogger[KyuubiTestEvent]
    conf.set(ELASTICSEARCH_EVENT_LOGGER_HOSTS, getElasticSearchHosts())
    logger.initialize(conf)

    val event = KyuubiTestEvent("test001")
    logger.logEvent(event)
  }

}

case class KyuubiTestEvent(value: String) extends KyuubiEvent {

  override def partitions: Seq[(String, String)] = Seq.empty

}
