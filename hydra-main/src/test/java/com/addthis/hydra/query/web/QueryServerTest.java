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
package com.addthis.hydra.query.web;

import com.addthis.basis.test.SlowTest;

import com.addthis.codec.config.Configs;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(SlowTest.class)
public class QueryServerTest {

    @Test
    public void simpleMainLike() throws Exception {
        try (QueryServer queryServer = Configs.newDefault(QueryServer.class)) {
            queryServer.run();
        }
    }
}