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

package com.addthis.hydra.query.aggregate;

import java.util.HashMap;
import java.util.Map;

import com.addthis.hydra.data.query.QueryException;
import com.addthis.meshy.ChannelMaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PerQueryRRAllocator implements TaskAllocator {

    static final Logger log = LoggerFactory.getLogger(PerQueryRRAllocator.class);

    @Override
    public void allocateTasks(QueryTaskSource[] taskSources, ChannelMaster meshy,
            Map<String, String> queryOptions) {
        Map<String, Integer> queryPerHostCountMap = new HashMap<>();

        for (int i = 0; i < taskSources.length; i++) {
            QueryTaskSourceOption selectedOption = allocateQueryTaskLegacy(queryPerHostCountMap, taskSources[i], i);
            // if our preferred choice is unavailable, let the query task pick whoever later
            selectedOption.tryActivate(meshy, queryOptions);
        }
    }

    /**
     * Allocate the query task to the best available host.  Currently we only try
     * to evenly distribute the queries amongst available hosts.  For example if hosts
     * 1 and 2 both have data for task a but host 1 already has a query task assigned
     * to it for a different task then we will pick host 2.
     *
     * @param queryPerHostCountMap - map of number of queries assigned to each host
     * @param taskSource            - the available queryReference objects for the task being assigned
     */
    private static QueryTaskSourceOption allocateQueryTaskLegacy(Map<String, Integer> queryPerHostCountMap,
            QueryTaskSource taskSource, int taskId) {
        if ((taskSource == null) || (taskSource.options.length == 0)) {
            throw new RuntimeException("fileReferenceWrapper list cannot be empty");
        }
        int minNumberAssigned = -1;
        QueryTaskSourceOption bestQueryOption = null;
        for (QueryTaskSourceOption option : taskSource.options) {
            String host = option.queryReference.getHostUUID();
            Integer numberAssigned = queryPerHostCountMap.get(host);
            if (numberAssigned == null) {
                numberAssigned = 0;
            }
            log.trace("host: {} currently has: {} assigned", host, numberAssigned);
            // if we haven't picked any host
            // or if the current number of sub queries assigned to this host is less than the least loaded host we have found so far
            if ((minNumberAssigned < 0) || (numberAssigned < minNumberAssigned)) {
                minNumberAssigned = numberAssigned;
                bestQueryOption = option;
            }
        }
        // bestWrapper should never be null here
        if (bestQueryOption == null) {
            throw new QueryException("Unable to select best query task option");
        }
        log.trace("selected host: {} as best host for task: {}",
                bestQueryOption.queryReference.getHostUUID(), taskId);
        queryPerHostCountMap.put(bestQueryOption.queryReference.getHostUUID(), minNumberAssigned + 1);
        return bestQueryOption;
    }
}
