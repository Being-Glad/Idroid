# Accessibility in Motion Design

Motion accessibility is mandatory. Vestibular disorders affect more than 35% of adults over 40. Motion sickness, dizziness, vertigo, and migraines can be triggered by sudden or large-scale screen animations.

---

## 1. The prefers-reduced-motion Media Query

Always provide a reduced-motion alternative. "Reduced motion" does not always mean "zero motion", it means removing large displacements, zooming, spinning, and parallax.

### Pure CSS
```css
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
```

Or scoped intentionally per component:
```css
.card {
  transition: transform 200ms ease, opacity 200ms ease;
}

@media (prefers-reduced-motion: reduce) {
  .card {
    transition: opacity 200ms ease; /* Keep opacity fade, kill transform movement */
  }
}
```

### Framer Motion / Motion
Use `useReducedMotion()` hook:
```jsx
import { useReducedMotion, motion } from "framer-motion";

export function Modal({ isOpen, children }) {
  const shouldReduceMotion = useReducedMotion();

  const variants = {
    hidden: {
      opacity: 0,
      y: shouldReduceMotion ? 0 : 16,
      scale: shouldReduceMotion ? 1 : 0.96,
    },
    visible: {
      opacity: 1,
      y: 0,
      scale: 1,
    },
  };

  return (
    <motion.div
      initial="hidden"
      animate="visible"
      exit="hidden"
      variants={variants}
      transition={{ duration: shouldReduceMotion ? 0.15 : 0.25 }}
    >
      {children}
    </motion.div>
  );
}
```

---

## 2. Vestibular Safety Guidelines

Avoid triggers that cause physical discomfort or nausea:

1. **No Large-Scale Viewport Movement**: Avoid sliding full-screen content horizontally or vertically across large distances. Fade in place instead.
2. **No Unconstrained Parallax**: Parallax scrolling (elements moving at conflicting speeds) is a top vestibular trigger. Disable all parallax under reduced motion.
3. **No Continuous Infinite Motion**: Pulsing, rotating, spinning loaders must have a clear functional end or be pausable.
4. **No Deep Zooming / Flying In**: Avoid scaling up from 0 to 100% across the full viewport.
5. **No Visual Flashing or Strobe**: Flashing more than 3 times in 1 second violates WCAG 2.3.1 (photosensitive seizure risk).

---

## 3. Safe Substitutes for Motion

When reduced motion is active, substitute spatial movement with non-spatial cues:
- **Opacity fades**: 0 to 1 (safe for vestibular system)
- **Color or border emphasis**: Subtle outline highlight
- **Blur materialization**: Slight blur(2px) to blur(0) or instant
- **Instant state change**: Snappy appearance with clear visual hierarchy
