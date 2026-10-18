# Third-Party Links, Embeds and Services

**Effective date:** [PLACEHOLDER: YYYY-MM-DD]

ADF AIO may provide links to external platforms and may, depending on the implementation, display third-party embedded content. This document explains the distinction and provides an inventory to complete before publication.

## 1. External links versus embeds

- **External link:** opens a third-party website when a user chooses to click it. The destination may then collect information under its own policies.
- **Embedded content:** loads content from another provider inside the App, such as a video player, social post, map, or widget. The provider may receive technical information as soon as the embed loads, even if the user does not interact with it.

A social-media icon that only links to a platform is not the same as an embedded social-media widget.

## 2. Provider inventory

List only providers actually loaded by the deployed app.

| Provider | How it is used | Link or embed? | Data potentially received | Privacy / terms URL |
|---|---|---|---|---|
| Instagram / Meta | [PLACEHOLDER: actual use] | [Link/embed] | [Verify] | [Add current official policy URL] |
| GitHub | [PLACEHOLDER: actual use] | [Link/embed] | [Verify] | [Add current official policy URL] |
| LinkedIn | [PLACEHOLDER: actual use] | [Link/embed] | [Verify] | [Add current official policy URL] |
| X | [PLACEHOLDER: actual use] | [Link/embed] | [Verify] | [Add current official policy URL] |
| YouTube / Google | [PLACEHOLDER: actual use] | [Link/embed] | [Verify] | [Add current official policy URL] |
| WhatsApp / Meta | [PLACEHOLDER: actual use] | [Link/embed] | [Verify] | [Add current official policy URL] |
| Facebook / Meta | [PLACEHOLDER: actual use] | [Link/embed] | [Verify] | [Add current official policy URL] |
| Reddit | [PLACEHOLDER: actual use] | [Link/embed] | [Verify] | [Add current official policy URL] |
| Hosting / CDN | [PLACEHOLDER: provider] | [Service] | [Verify logs and metadata] | [Add current official policy URL] |
| Analytics / error reporting | [PLACEHOLDER: if any] | [Script/service] | [Verify] | [Add current official policy URL] |

## 3. Consent and privacy

Where required, delay optional third-party embeds until the user consents or clicks to load them. Consider a privacy-preserving placeholder that explains which provider will load and lets the user choose to proceed.

Do not claim that third-party providers receive no data unless the implementation and provider documentation support that claim.

## 4. Provider changes

The inventory should be reviewed whenever a provider, script, embed, API, analytics tool, or hosting service is added or removed.
