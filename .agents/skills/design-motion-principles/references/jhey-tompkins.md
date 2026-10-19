# Jhey Tompkins: Playful Experimentation & CSS Innovation

Jhey Tompkins (@jh3yy) represents the creative boundary of the web: **pushing CSS, animations, and micro-interactions to create memorable delight and whimsical innovation**.

---

## The Core Philosophy

> "What could this become?"

Where Emil says "keep it minimal" and Jakub says "keep it refined", Jhey asks:
- "How can we make this unexpectedly joyful?"
- "Can we build this purely in CSS without heavy JavaScript dependencies?"
- "How can CSS variables, 3D cuboids, and custom properties turn static UI into art?"

Best for: Creative portfolios, marketing campaigns, educational and kids apps, Easter eggs, celebratory brand moments.

---

## Jhey's Key Techniques

### 1. Typed Properties with `@property`
Unlocking true CSS property interpolation:
```css
@property --gradient-angle {
  syntax: "<angle>";
  initial-value: 0deg;
  inherits: false;
}

@keyframes rotate-angle {
  to { --gradient-angle: 360deg; }
}

.glow-card {
  background: conic-gradient(from var(--gradient-angle), #ff4500, #ff8c00, #ff4500);
  animation: rotate-angle 4s linear infinite;
}
```

### 2. Deconstructed 3D Cuboids
Building 3D objects with semantic CSS boxes:
- Every face is a `div` or pseudo-element positioned with `preserve-3d`.
- Use `--size`, `--depth`, `--height` variables to scale smoothly.

### 3. Scroll-Driven Triggers vs Scroll-Speed Scrubbing
Separate scroll detection from playback speed:
- Use scroll timeline to trigger a boolean or state variable.
- Run a normal duration-based CSS transition or keyframe once triggered, so fast/slow scrolling doesn't distort the animation's natural tempo.

### 4. Coordinated Stagger with Negative Delays
Creating seamless cyclical animations where items appear mid-stride:
```css
.orbit-dot {
  animation: orbit 3s linear infinite;
  animation-delay: calc(var(--i) * -0.6s);
}
```
Negative delays start the animation at a specific offset immediately without waiting for a delay to elapse.
