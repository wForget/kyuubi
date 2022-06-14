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

import io.trino.tpcds.Table

import scala.collection.JavaConverters._
import scala.io.{Codec, Source}
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.kyuubi.KyuubiFunSuite
import org.apache.kyuubi.spark.connector.common.GoldenFileUtils._
import org.apache.kyuubi.spark.connector.common.LocalSparkSession.withSparkSession
import org.apache.kyuubi.spark.connector.common.SparkUtils
import org.apache.spark.sql.execution.datasources.v2.BatchScanExec

// scalastyle:off line.size.limit
/**
 * To run this test suite:
 * {{{
 *   build/mvn clean install \
 *     -Dmaven.plugin.scalatest.exclude.tags="" \
 *     -Dtest=none -DwildcardSuites=org.apache.kyuubi.spark.connector.tpcds.TPCDSQuerySuite
 * }}}
 *
 * To re-generate golden files for this suite:
 * {{{
 *   KYUUBI_UPDATE=1 build/mvn clean install \
 *     -Dmaven.plugin.scalatest.exclude.tags="" \
 *     -Dtest=none -DwildcardSuites=org.apache.kyuubi.spark.connector.tpcds.TPCDSQuerySuite
 * }}}
 */
// scalastyle:on line.size.limit

class TPCDSQuerySuite extends KyuubiFunSuite {

  val queries: Set[String] = (1 to 99).map(i => s"q$i").toSet -
    ("q14", "q23", "q24", "q39") +
    ("q14a", "q14b", "q23a", "q23b", "q24a", "q24b", "q39a", "q39b")

  test("run query on tiny") {
    assume(SparkUtils.isSparkVersionEqualTo("3.2"))
    val viewSuffix = "view";
    val sparkConf = new SparkConf().setMaster("local[*]")
      .set("spark.ui.enabled", "false")
      .set("spark.sql.catalogImplementation", "in-memory")
      .set("spark.sql.catalog.tpcds", classOf[TPCDSCatalog].getName)
      .set("spark.sql.catalog.tpcds.useTableSchema_2_6", "true")
    withSparkSession(SparkSession.builder.config(sparkConf).getOrCreate()) { spark =>
      spark.sql("USE tpcds.tiny")
      queries.map { queryName =>
        val in = getClass.getClassLoader.getResourceAsStream(s"tpcds_3.2/$queryName.sql")
        val queryContent: String = Source.fromInputStream(in)(Codec.UTF8).mkString
        in.close()
        queryName -> queryContent
      }.foreach { case (name, sql) =>
        try {
          val result = spark.sql(sql).collect()
          val schema = spark.sql(sql).schema
          val schemaDDL = LICENSE_HEADER + schema.toDDL + "\n"
          spark.createDataFrame(result.toList.asJava, schema).createTempView(s"$name$viewSuffix")
          val sumHashResult = LICENSE_HEADER + spark.sql(
            s"select sum(hash(*)) from $name$viewSuffix").collect().head.get(0) + "\n"
          val tuple = generateGoldenFiles("tpcds_3.2", name, schemaDDL, sumHashResult)
          assert(schemaDDL == tuple._1)
          assert(sumHashResult == tuple._2)
        } catch {
          case cause: Throwable =>
            fail(name, cause)
        }
      }
    }
  }

  test("test taskPartitionBytes") {
    val taskPartitionBytes: Long = 1 * 1024 * 1024L

    val sparkConf = new SparkConf().setMaster("local[*]")
      .set("spark.ui.enabled", "false")
      .set("spark.sql.catalogImplementation", "in-memory")
      .set("spark.sql.catalog.tpcds", classOf[TPCDSCatalog].getName)
      .set("spark.connector.tpcds.taskPartitionBytes", String.valueOf(taskPartitionBytes))
    withSparkSession(SparkSession.builder.config(sparkConf).getOrCreate()) { spark =>
      val tableName = "catalog_returns"
      val table = Table.getTable(tableName)
      val scale = 100
      val df = spark.sql(s"select * from tpcds.sf$scale.$tableName")
      val scan = df.queryExecution.analyzed.collect {
        case scanExec: BatchScanExec => scanExec.scan.asInstanceOf[TPCDSBatchScan]
      }
      val expected =
        (TPCDSStatisticsUtils.sizeInBytes(table, scale) / taskPartitionBytes).ceil.toInt
      assert(expected == 1)
    }

  }
}
