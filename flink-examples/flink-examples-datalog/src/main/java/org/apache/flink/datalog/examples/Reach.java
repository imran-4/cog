/*
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.apache.flink.datalog.examples;

import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.datalog.BatchDatalogEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Table;
import org.apache.flink.types.IntValue;

public class Reach {
    public static void main(String[] args) throws Exception {
        String testFilePath = null;

        if (args.length > 0) {
            testFilePath = args[0].trim();
        } else
            throw new Exception("Please provide input dataset. ");
        String inputProgram = null;
        if (args.length == 2) {
            inputProgram =
                    "reach(X,Y) :- graph(X,Y),X="+ Integer.parseInt(args[1]) +".\n"
                            + "reach(X,Y) :- reach(X,Z),graph(Z,Y).\n";
        } else if (args.length == 3) {


        inputProgram =
                "reach(X,Y) :- graph(X,Y),X="+ new IntValue(Integer.parseInt(args[1])) +".\n"
                        + "reach(X,Y) :- reach(X,Z),graph(Z,Y).\n";
        }
        String query = "reach(X,Y)?";

        ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
        EnvironmentSettings settings = EnvironmentSettings
                .newInstance()
                .useDatalogPlanner()
                .inBatchMode()
                .build();
        BatchDatalogEnvironment datalogEnv = BatchDatalogEnvironment.create(env, settings);
        DataSet<Tuple2<Integer, Integer>> dataSet = env.readCsvFile(testFilePath).fieldDelimiter(",").types(Integer.class, Integer.class);

        datalogEnv.registerDataSet("graph", dataSet, "v1,v2");
        Table queryResult = datalogEnv.datalogQuery(inputProgram, query);
        DataSet<Tuple2<Integer, Integer>> resultDS = datalogEnv.toDataSet(queryResult, dataSet.getType());

//        resultDS.writeAsCsv(testFilePath+"_output");
        System.out.println(resultDS.count());
    }
}
