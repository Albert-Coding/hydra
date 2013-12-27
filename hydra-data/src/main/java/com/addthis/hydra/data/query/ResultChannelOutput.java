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
package com.addthis.hydra.data.query;

import com.addthis.bundle.channel.DataChannelError;
import com.addthis.bundle.channel.DataChannelOutput;
import com.addthis.bundle.core.Bundle;


public class ResultChannelOutput extends AbstractQueryOp {

    private final DataChannelOutput output;

    public ResultChannelOutput(DataChannelOutput output) {
        this.output = output;
    }

    public DataChannelOutput getOutput() {
        return output;
    }

    @Override
    public void send(Bundle row) throws DataChannelError {
        output.send(row);
    }

    @Override
    public void sendComplete() {
        output.sendComplete();
    }

    @Override
    public String getSimpleName() {
        return "ResultChannelOutput(" + output + ")";
    }
}
