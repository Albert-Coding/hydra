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

import org.junit.Test;

public class TestOpFold extends TestOp {

    @Test
    public void testFold() throws Exception {
        doOpTest(
                new DataTableHelper().
                        tr().td("cat", "a", "1", "2").
                        tr().td("cat", "b", "2", "3").
                        tr().td("cat", "c", "3", "4").
                        tr().td("dog", "a", "6", "0").
                        tr().td("dog", "b", "7", "1").
                        tr().td("dog", "c", "8", "0"),
                "fold=0:1:a,b,c:2,3",
                new DataTableHelper().
                        tr().td("cat").td("a", "1", "2").td("b", "2", "3").td("c", "3", "4").
                        tr().td("dog").td("a", "6", "0").td("b", "7", "1").td("c", "8", "0")
        );
    }
}
