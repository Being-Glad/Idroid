# Motion Performance & Hardware Optimization

Fluid animations require 60fps (or 120fps on ProMotion displays), leaving an 8.3ms - 16.6ms window per frame.

---

## 1. The Rendering Pipeline

The browser renders web pages in four sequential stages:
1. **JavaScript / Style**: Calculate CSS rules and DOM changes.
2. **Layout (Reflow)**: Calculate geometry (width, height, position, margin). Expensive!
3. **Paint**: Fill in pixels, colors, text, borders, shadows. Expensive!
4. **Composite**: Assemble GPU texture layers. Cheap & hardware-accelerated!

### Animate Composite Properties Only:
- `transform` (`translateX`, `translateY`, `scale`, `rotate`, `skew`)
- `opacity`
- `filter` (with moderation)

### Never Animate Layout Properties:
- ❌ `width`, `height`, `top`, `left`, `right`, `bottom`, `margin`, `padding`, `font-size`, `border-width`.

---

## 2. Using `will-change` Safely

`will-change` promotes an element to its own graphics layer on the GPU.

### Rules for `will-change`:
- **Be Specific**: Use `will-change: transform, opacity`, never `will-change: all`.
- **Apply on Hover / Focus Just Before Animation**:
  ```css
  .card:hover {
    will-change: transform;
  }
  ```
- **Don't Overuse**: Too many GPU layers consume vast amounts of VRAM, slowing down devices and causing battery drain.

---

## 3. Fast Layout Transitions with FLIP

When an element must change dimensions or move between DOM containers (e.g., expanding cards, reordering lists):
- **First**: Record initial bounding box (`getBoundingClientRect`).
- **Last**: Record target bounding box.
- **Inverse**: Calculate delta (`deltaX = first.left - last.left`, `deltaY = first.top - last.top`). Apply `transform: translate(deltaX, deltaY) scale(...)`.
- **Play**: Animate transform to `none`.

In Framer Motion, FLIP is automated using the `layout` and `layoutId` props.
