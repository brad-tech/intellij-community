// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable
import com.intellij.util.containers.DisposableWrapperList
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class AbstractObservableBooleanProperty : AbstractObservableProperty<Boolean>(), BooleanProperty {

  private val setListeners = DisposableWrapperList<() -> Unit>()
  private val resetListeners = DisposableWrapperList<() -> Unit>()

  protected fun fireChangeEvents(oldValue: Boolean, newValue: Boolean) {
    when {
      !oldValue && newValue -> fireSetEvent()
      oldValue && !newValue -> fireResetEvent()
    }
    fireChangeEvent(newValue)
  }

  private fun fireSetEvent() {
    setListeners.forEach { it() }
  }

  override fun afterSet(listener: () -> Unit) {
    setListeners.add(listener)
  }

  override fun afterSet(listener: () -> Unit, parentDisposable: Disposable) {
    setListeners.add(listener, parentDisposable)
  }

  private fun fireResetEvent() {
    resetListeners.forEach { it() }
  }

  override fun afterReset(listener: () -> Unit) {
    resetListeners.add(listener)
  }

  override fun afterReset(listener: () -> Unit, parentDisposable: Disposable) {
    resetListeners.add(listener, parentDisposable)
  }
}