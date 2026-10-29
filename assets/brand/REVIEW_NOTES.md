# Design review checklist

The generated rendered icon **and all 14 binary assets** are committed in this branch (PNG exports at 16–1024px, ICO, ICNS, wordmark and social banner). The earlier generic SVG placeholders were removed.

- [x] Export assets and verify PNG/ICO/ICNS file readability
- [ ] Review icon legibility at 16, 24 and 32px
- [ ] Independently generate or upscale higher-fidelity 1024px masters if required for production
- [ ] Wire approved art into application launcher, installer and resource build paths

The 512px and 1024px exports are derived from a roughly 248px original sheet crop, not separate native-resolution generations. Keep PR in draft until approved; do not merge automatically.
