/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.internal.jimage;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ImageModules {
    private final Map<Loader, LoaderModuleData> loaders = new LinkedHashMap<>();
    private final Map<String, Set<String>> localPkgs = new HashMap<>();

    public ImageModules(Set<String> bootModules,
                        Set<String> extModules,
                        Set<String> appModules) {
        mapModulesToLoader(Loader.BOOT_LOADER, bootModules);
        mapModulesToLoader(Loader.EXT_LOADER, extModules);
        mapModulesToLoader(Loader.APP_LOADER, appModules);
    }

    // package-private

    /**
     * Returns the module to moduleToPackages map
     */
    Map<String, Set<String>> moduleToPackages() {
        return localPkgs;
    }

    /**
     * Sets the local packages of a module
     */
    void setPackages(String mn, Set<String> pkgs) {
        localPkgs.put(mn, pkgs);
    }

    /*
     * Returns the name of modules mapped to a given class loader in the image
     */
    Set<String> getModules(Loader type) {
        if (loaders.containsKey(type)) {
            return loaders.get(type).modules();
        } else {
            return Collections.emptySet();
        }
    }

    private void mapModulesToLoader(Loader loader, Set<String> modules) {
        if (modules.isEmpty())
            return;

        // put java.base first
        Set<String> mods = new LinkedHashSet<>();
        modules.stream()
                .filter(m -> m.equals("java.base"))
                .forEach(mods::add);
        modules.stream().sorted()
                .filter(m -> !m.equals("java.base"))
                .forEach(mods::add);
        loaders.put(loader, new LoaderModuleData(loader, mods));
    }

    enum Loader {
        BOOT_LOADER(0, "bootmodules"),
        EXT_LOADER(1, "extmodules"),
        APP_LOADER(2, "appmodules");  // ## may be more than 1 loader

        final int id;
        final String name;
        Loader(int id, String name) {
            this.id = id;
            this.name = name;
        }

        String getName() {
            return name;
        }
        static Loader get(int id) {
            switch (id) {
                case 0: return BOOT_LOADER;
                case 1: return EXT_LOADER;
                case 2: return APP_LOADER;
                default:
                    throw new IllegalArgumentException("invalid loader id: " + id);
            }
        }
        public int id() { return id; }
    }

    private class LoaderModuleData {
        private final Loader loader;
        private final Set<String> modules;
        LoaderModuleData(Loader loader, Set<String> modules) {
            this.loader = loader;
            this.modules = Collections.unmodifiableSet(modules);
        }

        Set<String> modules() {
            return modules;
        }
        Loader loader() { return loader; }
    }

    ImageModuleDataBuilder buildModuleData(Loader loader, BasicImageWriter writer) {
        Set<String> modules = getModules(loader);
        Map<String, List<String>> modulePackages = new LinkedHashMap<>();
        modules.stream().sorted().forEach((moduleName) -> {
            List<String> localPackages = localPkgs.get(moduleName).stream()
                    .map(pn -> pn.replace('.', '/'))
                    .sorted()
                    .collect(Collectors.toList());
            modulePackages.put(moduleName, localPackages);
        });

        return new ImageModuleDataBuilder(writer, modulePackages);
    }
}
