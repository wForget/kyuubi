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
package org.apache.spark.sql

import org.apache.spark.sql.execution.{CommandResultExec, SparkPlan}
import org.apache.spark.sql.execution.adaptive.{AdaptiveSparkPlanExec, ShuffleQueryStageExec}
import org.apache.spark.sql.execution.exchange.{ENSURE_REQUIREMENTS, ShuffleExchangeExec}
import org.apache.spark.sql.hive.HiveUtils.CONVERT_METASTORE_PARQUET
import org.apache.spark.sql.internal.SQLConf._

class DynamicShufflePartitionsSuite extends KyuubiSparkSQLExtensionTest {

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    setupData()
  }

  test("test dynamic shuffle partitions") {
    def collectExchanges(plan: SparkPlan): Seq[ShuffleExchangeExec] = {
      plan match {
        case p: CommandResultExec => collectExchanges(p.commandPhysicalPlan)
        case p: AdaptiveSparkPlanExec => collectExchanges(p.finalPhysicalPlan)
        case p: ShuffleQueryStageExec => collectExchanges(p.plan)
        case p: ShuffleExchangeExec => p +: collectExchanges(p.child)
        case p => p.children.flatMap(collectExchanges)
      }
    }

    // datasource scan
    withTable("table1", "table2", "table3") {
      sql("create table table1 stored as parquet as select c1, c2 from t1")
      sql("create table table2 stored as parquet as select c1, c2 from t2")
      sql("create table table3 (c1 int, c2 string) stored as parquet")

      withSQLConf(
        AUTO_BROADCASTJOIN_THRESHOLD.key -> "-1",
        COALESCE_PARTITIONS_INITIAL_PARTITION_NUM.key -> "2",
        ADVISORY_PARTITION_SIZE_IN_BYTES.key -> "500") {
        val df = sql("insert overwrite table3 " +
          " select a.c1 as c1, b.c2 as c2 from table1 a join table2 b on a.c1 = b.c1")

        val exchanges = collectExchanges(df.queryExecution.executedPlan)
        val (joinExchanges, rebalanceExchanges) = exchanges
          .partition(_.shuffleOrigin == ENSURE_REQUIREMENTS)
        // table scan size: 7369 3287
        assert(joinExchanges.size == 2)
        joinExchanges.foreach(e => assert(e.outputPartitioning.numPartitions == 15))
        // shuffle query size: 1742 509
        assert(rebalanceExchanges.size == 1)
        assert(rebalanceExchanges.head.outputPartitioning.numPartitions == 4)
      }

      // hive table scan
      sql("ANALYZE TABLE table1 COMPUTE STATISTICS")
      sql("ANALYZE TABLE table2 COMPUTE STATISTICS")
      withSQLConf(
        AUTO_BROADCASTJOIN_THRESHOLD.key -> "-1",
        COALESCE_PARTITIONS_INITIAL_PARTITION_NUM.key -> "2",
        ADVISORY_PARTITION_SIZE_IN_BYTES.key -> "500",
        CONVERT_METASTORE_PARQUET.key -> "false") {
        val df = sql("insert overwrite table3 " +
          " select a.c1 as c1, b.c2 as c2 from table1 a join table2 b on a.c1 = b.c1")

        val exchanges = collectExchanges(df.queryExecution.executedPlan)
        val (joinExchanges, rebalanceExchanges) = exchanges
          .partition(_.shuffleOrigin == ENSURE_REQUIREMENTS)
        // table scan size: 7369 3287
        assert(joinExchanges.size == 2)
        joinExchanges.foreach(e => assert(e.outputPartitioning.numPartitions == 15))
        // shuffle query size: 4820 720
        assert(rebalanceExchanges.size == 1)
        assert(rebalanceExchanges.head.outputPartitioning.numPartitions == 10)
      }
    }
  }

}
