# Output Format Specification (Audit Mode)

This document specifies the exact formatting requirements for Motion Audit reports in both **HTML Mode** (default) and **Terminal Mode** (flag-triggered).

---

## 1. HTML Report Specification (Default)

The HTML report is a self-contained single-file document saved to `motion-audits/{project-name}-{ISO-date}.html`.

### Key Requirements:
- **No external CSS/JS dependencies**: Embedded inside a single `<style>` tag.
- **Dark/Light Mode aware**: Uses CSS variables (`--bg`, `--fg`, `--border`, `--accent`, etc.).
- **Live looping demo cards**: Attached to each Critical and Important finding (using the template in `demo-shell.html`).
- **No animations on report chrome**: The audit report itself must NOT have entrance, scroll, or hover animations. Only the demo cards animate.

### Report Structure:
1. **Header**: Project Name, Audit Date, Perspective Weighting Breakdown (Primary / Secondary / Selective).
2. **Executive Summary**: Score, Total Issues (Critical, Important, Opportunities).
3. **Perspective Sections**:
   - Through Emil Kowalski's Lens (Restraint & Speed)
   - Through Jakub Krehel's Lens (Production Polish)
   - Through Jhey Tompkins' Lens (Creative Opportunity)
4. **Motion Gap Summary**: Conditional renders lacking animation.
5. **Accessibility Scorecard**: `prefers-reduced-motion` compliance.

---

## 2. Terminal Mode Specification (Flag-Triggered)

Triggered by `--terminal`, `--inline`, `--no-html`, or "show inline".

Render full markdown directly in chat:
```markdown
# 🎬 Motion Design Audit: {Project Name}
**Date**: {YYYY-MM-DD} | **Context**: {Project Context}
**Weighting**: Primary: {Designer} · Secondary: {Designer} · Selective: {Designer}

---

## 🔴 Critical Findings ({Count})
### 1. {Finding Title}
- **Location**: `{file}:{line}`
- **Issue**: {Description of anti-pattern or bug}
- **Lens**: {Emil / Jakub / Jhey}
- **Recommended Fix**:
```{language}
// Code solution with reduced-motion support
```

---

## 🟡 Important Refinements ({Count})
...

---

## 🟢 Creative Opportunities ({Count})
...

---

## 📋 Motion Gap Analysis
- `{file}:{line}`: Conditional render lacks transition wrapper.
```
