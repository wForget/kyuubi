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

package org.apache.kyuubi.spark.connector.tpcds

import scala.collection.JavaConverters._

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.connector.catalog.Table

import org.apache.kyuubi.spark.connector.common.SparkConfParser

case class TPCDSBatchScanConf(spark: SparkSession, table: Table, options: Map[String, String]) {

  private val confParser: SparkConfParser = SparkConfParser(options,
    spark.conf,
    table.properties().asScala.toMap)

  lazy val taskPartitionBytes: Long = confParser.longConf()
    .option("taskPartitionBytes")
    .sessionConf("spark.connector.tpcds.taskPartitionBytes")
    .tableProperty("taskPartitionBytes")
    .defaultValue(134217728)
    .parse()

}
