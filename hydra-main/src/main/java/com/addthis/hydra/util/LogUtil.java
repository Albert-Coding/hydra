/*
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
package com.addthis.hydra.util;

import com.addthis.basis.util.RollingLog;
import com.addthis.basis.util.Strings;

import com.addthis.codec.CodecExceptionLineNumber;
import com.addthis.codec.CodecJSON;
import com.addthis.hydra.task.output.TaskDataOutput;
import com.addthis.hydra.task.run.TaskRunConfig;
import com.addthis.maljson.JSONException;
import com.addthis.maljson.JSONObject;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;

public class LogUtil {

    private static DateTimeFormatter format = DateTimeFormat.forPattern("yyMMdd-HHmmss.SSS");

    /**
     * @param eventLog - rolling event log
     * @param log - backup basic logger, only used if eventLog is null
     * @param output - the KV data to output
     */
    public static void log(RollingLog eventLog, Logger log, StringMapHelper output) {
        if (eventLog == null) {
            log.warn(output.toString() + "----> EventLog was null redirecting to stdout");
        } else {
            String msg = Strings.cat("<", format.print(System.currentTimeMillis()), ">");
            output.add("timestamp", msg);
            eventLog.writeLine(output.createKVPairs().toString());
        }
    }

    public static TaskDataOutput newBundleOutputFromConfig(String name) {
        Config outputConfig = ConfigFactory.load().getConfig("hydra.log.events").getConfig(name);
        String jsonString = outputConfig.resolve().root().render(ConfigRenderOptions.concise());
        try {
            JSONObject outputJson = new JSONObject(jsonString);
            TaskDataOutput output = CodecJSON.decodeObject(TaskDataOutput.class, outputJson);
            output.init(new TaskRunConfig(0, 1, "event-log" + name));
            return output;
        } catch (JSONException ex) {
            throw new IllegalStateException(ex); // typesafe should not render invalid json
        } catch (CodecExceptionLineNumber lineNumber) {
            throw new RuntimeException(lineNumber);
        }
    }
}
