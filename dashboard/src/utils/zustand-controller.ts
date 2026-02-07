/**
 * Constellation Dashboard - Zustand-Lit Controller
 *
 * A Lit ReactiveController that bridges Zustand stores with Lit components.
 * Automatically triggers re-renders when subscribed store state changes.
 */

import type { ReactiveController, ReactiveControllerHost } from 'lit';
import type { StoreApi } from 'zustand';

/**
 * StoreController connects a Zustand store to a Lit component.
 *
 * Usage in a Lit component:
 *
 * ```typescript
 * import { LitElement } from 'lit';
 * import { StoreController } from '../utils/zustand-controller';
 * import { useAppStore } from '../store/app.store';
 *
 * class MyComponent extends LitElement {
 *   private appStore = new StoreController(this, useAppStore);
 *
 *   render() {
 *     const { currentView, status } = this.appStore.state;
 *     return html`<div>View: ${currentView}, Status: ${status}</div>`;
 *   }
 * }
 * ```
 */
export class StoreController<T> implements ReactiveController {
  private unsubscribe?: () => void;
  private _state: T;

  constructor(
    private host: ReactiveControllerHost,
    private store: StoreApi<T>,
    private selector?: (state: T) => unknown
  ) {
    this._state = store.getState();
    host.addController(this);
  }

  hostConnected(): void {
    this.unsubscribe = this.store.subscribe((state, prevState) => {
      // If a selector is provided, only update if selected value changed
      if (this.selector) {
        const next = this.selector(state);
        const prev = this.selector(prevState);
        if (next !== prev) {
          this._state = state;
          this.host.requestUpdate();
        }
      } else {
        // No selector - update on any change
        this._state = state;
        this.host.requestUpdate();
      }
    });
  }

  hostDisconnected(): void {
    this.unsubscribe?.();
  }

  /**
   * Get the current state from the store
   */
  get state(): T {
    return this._state;
  }
}

/**
 * SelectiveStoreController only re-renders when selected slice changes.
 *
 * Usage:
 *
 * ```typescript
 * class MyComponent extends LitElement {
 *   private viewController = new SelectiveStoreController(
 *     this,
 *     useAppStore,
 *     (state) => state.currentView
 *   );
 *
 *   render() {
 *     return html`<div>View: ${this.viewController.value}</div>`;
 *   }
 * }
 * ```
 */
export class SelectiveStoreController<T, S> implements ReactiveController {
  private unsubscribe?: () => void;
  private _value: S;

  constructor(
    private host: ReactiveControllerHost,
    private store: StoreApi<T>,
    private selector: (state: T) => S
  ) {
    this._value = selector(store.getState());
    host.addController(this);
  }

  hostConnected(): void {
    this.unsubscribe = this.store.subscribe((state, prevState) => {
      const next = this.selector(state);
      const prev = this.selector(prevState);

      // Use shallow equality check for objects
      if (!shallowEqual(next, prev)) {
        this._value = next;
        this.host.requestUpdate();
      }
    });
  }

  hostDisconnected(): void {
    this.unsubscribe?.();
  }

  /**
   * Get the selected value from the store
   */
  get value(): S {
    return this._value;
  }
}

/**
 * Shallow equality check for objects and primitives
 */
function shallowEqual(a: unknown, b: unknown): boolean {
  if (a === b) return true;

  if (
    typeof a !== 'object' ||
    typeof b !== 'object' ||
    a === null ||
    b === null
  ) {
    return false;
  }

  const keysA = Object.keys(a as object);
  const keysB = Object.keys(b as object);

  if (keysA.length !== keysB.length) return false;

  for (const key of keysA) {
    if (
      (a as Record<string, unknown>)[key] !==
      (b as Record<string, unknown>)[key]
    ) {
      return false;
    }
  }

  return true;
}

export default StoreController;
