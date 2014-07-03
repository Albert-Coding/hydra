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
package com.addthis.hydra.task.output.tree;

import java.util.ArrayList;

import com.addthis.codec.annotations.FieldConfig;
import com.addthis.codec.codables.Codable;


/**
 * Integer value / index substitution for target name
 *
 * @user-reference
 */
public final class TreeMapperPathReference implements Codable {

    @FieldConfig(codable = true)
    private String path;
    @FieldConfig(codable = true)
    private Integer index;

    public TreeMapperPathReference() {
    }

    public TreeMapperPathReference(String path) {
        this.path = path;
    }

    @Override
    public String toString() {
        ArrayList<String> l = new ArrayList<String>();
        if (path != null) {
            l.add("path=" + path);
        }
        if (index != null) {
            l.add("index=" + index);
        }
        return "BundleTarget(" + l + ")";
    }

    public void resolve(TreeMapper mapper) {
        if (path != null) {
            setIndex(mapper.getPathIndex(path));
        }
    }

    /**
     * add processing target
     */
    public TreeMapperPathReference setIndex(Integer unit) {
        this.index = unit;
        return this;
    }

    public Integer getTargetUnit() {
        return index;
    }

    public String ruleName() {
        return path;
    }
}
