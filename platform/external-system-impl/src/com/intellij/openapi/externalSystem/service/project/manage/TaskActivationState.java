/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
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
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
* @author Vladislav.Soroka
* @since 10/30/2014
*/
@Tag("activation")
public class TaskActivationState {
  @Tag("before_sync")
  @AbstractCollection(surroundWithTag = false, elementTag = "task", elementValueAttribute = "name")
  public Set<String> beforeSyncTasks = new LinkedHashSet<String>();

  @Tag("after_sync")
  @AbstractCollection(surroundWithTag = false, elementTag = "task", elementValueAttribute = "name")
  public Set<String> afterSyncTasks = new LinkedHashSet<String>();

  @Tag("before_compile")
  @AbstractCollection(surroundWithTag = false, elementTag = "task", elementValueAttribute = "name")
  public Set<String> beforeCompileTasks = new LinkedHashSet<String>();

  @Tag("after_compile")
  @AbstractCollection(surroundWithTag = false, elementTag = "task", elementValueAttribute = "name")
  public Set<String> afterCompileTasks = new LinkedHashSet<String>();

  @Tag("after_rebuild")
  @AbstractCollection(surroundWithTag = false, elementTag = "task", elementValueAttribute = "name")
  public Set<String> afterRebuildTask = new LinkedHashSet<String>();

  @Tag("before_rebuild")
  @AbstractCollection(surroundWithTag = false, elementTag = "task", elementValueAttribute = "name")
  public Set<String> beforeRebuildTask = new LinkedHashSet<String>();

  @NotNull
  public Set<String> getTasks(@NotNull ExternalSystemTaskActivator.Phase phase) {
    switch (phase) {
      case AFTER_COMPILE:
        return afterCompileTasks;
      case BEFORE_COMPILE:
        return beforeCompileTasks;
      case AFTER_SYNC:
        return afterSyncTasks;
      case BEFORE_SYNC:
        return beforeSyncTasks;
      case AFTER_REBUILD:
        return afterRebuildTask;
      case BEFORE_REBUILD:
        return beforeRebuildTask;
      default:
        throw new IllegalArgumentException("Unknown task activation phase: " + phase);
    }
  }
}
