/*
 * Copyright 2015-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.cli.cfg;

import org.apache.karaf.shell.console.completer.ArgumentCompleter;
import org.apache.karaf.shell.console.completer.StringsCompleter;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.cfg.ConfigProperty;
import org.onosproject.cli.AbstractCompleter;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import static org.onosproject.cli.AbstractShellCommand.get;

/**
 * Component property name completer.
 */
public class ComponentPropertyNameCompleter extends AbstractCompleter {
    @Override
    public int complete(String buffer, int cursor, List<String> candidates) {
        // Delegate string completer
        StringsCompleter delegate = new StringsCompleter();

        // Component name is the previous argument.
        ArgumentCompleter.ArgumentList list = getArgumentList();
        String componentName = list.getArguments()[list.getCursorArgumentIndex() - 1];
        ComponentConfigService service = get(ComponentConfigService.class);

        SortedSet<String> strings = delegate.getStrings();
        Set<ConfigProperty> properties =
                service.getProperties(componentName);
        if (properties != null) {
            properties.forEach(property -> strings.add(property.name()));
        }

        // Now let the completer do the work for figuring out what to offer.
        return delegate.complete(buffer, cursor, candidates);
    }

}
