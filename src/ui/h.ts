/**
 * Tiny in-house view helper: h(tag, props, ...children) creates DOM elements.
 * Screens are plain functions from state to elements; the app re-renders on change.
 * Never uses innerHTML, so text is always inserted as text.
 */
export type Child = Node | string | number | null | undefined | false | readonly Child[];
export type Props = Record<string, unknown>;

const PROPERTIES = new Set(["value", "checked", "disabled", "readOnly", "multiple", "selected"]);

export function h(tag: string, props: Props | null = null, ...children: Child[]): HTMLElement {
  const el = document.createElement(tag);
  if (props) {
    for (const [key, value] of Object.entries(props)) {
      if (value === undefined || value === null || value === false) continue;
      if (key.startsWith("on") && typeof value === "function") {
        el.addEventListener(key.slice(2).toLowerCase(), value as EventListener);
      } else if (PROPERTIES.has(key)) {
        Reflect.set(el, key, value);
      } else if (key === "style") {
        throw new Error("Inline style attributes are blocked by the CSP; use classes");
      } else {
        el.setAttribute(key === "className" ? "class" : key, value === true ? "" : String(value));
      }
    }
  }
  appendChildren(el, children);
  return el;
}

function appendChildren(el: Node, children: readonly Child[]): void {
  for (const c of children) {
    if (c === null || c === undefined || c === false) continue;
    if (Array.isArray(c)) appendChildren(el, c);
    else if (c instanceof Node) el.appendChild(c);
    else el.appendChild(document.createTextNode(String(c)));
  }
}

/** Replaces the content of root with the given children. */
export function mount(root: HTMLElement, ...children: Child[]): void {
  root.replaceChildren();
  appendChildren(root, children);
  const focus = root.querySelector<HTMLElement>("[data-autofocus]");
  focus?.focus();
}
