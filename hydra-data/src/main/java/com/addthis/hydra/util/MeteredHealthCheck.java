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

import java.util.concurrent.TimeUnit;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Meter;

public class MeteredHealthCheck extends HealthCheck {

    private Meter failedCheckMeter;

    public MeteredHealthCheck(int maxFailures, String meterName, TimeUnit meterInterval) {
        super(maxFailures);
        this.failedCheckMeter = Metrics.newMeter(MeteredHealthCheck.class, meterName, meterName, meterInterval);
    }

    @Override
    public void onFailedCheck() {
        this.failedCheckMeter.mark();
    }
}
