# Emil Kowalski: Purposeful Restraint & Speed

Emil Kowalski (creator of animations at Linear, ex-Vercel, creator of *Animations on the Web*) focuses on **motion that makes software feel fast, snappy, and invisible**.

---

## The Core Philosophy

> "The best animation is that which goes unnoticed."

In productivity software, users do not come to admire animations. They come to get work done. Motion must serve a functional purpose:
1. Provide tactile feedback (did my action register?)
2. Maintain spatial orientation (where did this modal come from?)
3. Mask latency (progressive disclosure while data loads)

---

## Emil's Key Rules

### 1. The Frequency Rule
- **High Frequency (100+ times/day)**: Instant or sub-120ms. Examples: command bar selection, checkbox toggling, list navigation, tab switching.
- **Medium Frequency (daily)**: 150ms – 220ms. Examples: modal open, dropdown menu, notifications.
- **Low Frequency (monthly/annual)**: 250ms – 400ms. Examples: onboarding, success screens, achievements.

### 2. Never Animate Keyboard Interactions
If a user hits `Cmd + K`, `Escape`, or arrow keys, show the result immediately. Power users perceive keyboard animation as lag.

### 3. Fast Durations & Snappy Springs
- Durations should generally sit between **150ms and 220ms**.
- For springs: high stiffness (`stiffness: 350-400`), solid damping (`damping: 30-35`), zero overshoot (`bounce: 0`).

### 4. Interruptibility Over Everything
Users trigger actions rapidly. An animation must be interruptible mid-flight:
- If a user opens and immediately closes a menu, it should reverse fluidly from its current frame, not finish opening first.
- Use CSS transitions on state or Framer Motion springs rather than fixed `@keyframes`.

### 5. Transform Origins Matter
Always anchor dropdowns, popovers, and tooltips to their trigger element:
```css
.dropdown-menu {
  transform-origin: top left; /* Or dynamic based on trigger position */
}
```

### 6. Velocity-Based Drag Dismissal
Don't make users drag a drawer 50% across the screen to dismiss. Calculate velocity: if the user flicks fast, dismiss even with a 20px travel distance.
