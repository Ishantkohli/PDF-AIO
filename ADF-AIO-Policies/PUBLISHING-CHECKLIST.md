> **Source-audit hold (20 October 2026):** Do not publish until Gemini status, Android backup behavior, analytics/crash reporting, and live website cookies/scripts are verified. See `SOURCE-CODE-AUDIT-NOTES.md`.

# ADF AIO — Publishing Checklist

Complete this checklist before making the legal pages public.

## Ownership and contact

- [ ] Identify the actual legal owner / rights holder.
- [ ] Add a working contact email for privacy, support, and licensing.
- [ ] Add effective and last-updated dates.
- [ ] Choose the applicable governing law and dispute process with legal advice.
- [ ] Confirm who owns the code, graphics, logo, and other assets.

## Technical privacy audit

- [ ] Review source code for forms, APIs, analytics, pixels, external scripts, and embeds.
- [ ] Inspect cookies, local storage, session storage, IndexedDB, and service workers.
- [ ] Review hosting, CDN, logging, monitoring, and error-reporting practices.
- [ ] Identify every third-party processor/provider and link to its current official policies.
- [ ] Document what data is collected, why, where it is stored, who receives it, and retention.
- [ ] Verify whether account, payment, location, file-upload, or analytics features exist.
- [ ] Confirm deletion and access-request processes.

## Consent

- [ ] Reject optional categories as easily as accepting them.
- [ ] Do not preselect optional consent.
- [ ] Prevent optional scripts/embeds from loading before consent where required.
- [ ] Provide a way to reopen cookie preferences and withdraw consent.
- [ ] Keep marketing consent separate from service/contact forms.
- [ ] Record only the minimum consent evidence necessary.
- [ ] Test consent behavior in a clean browser profile.

## License and repository

- [ ] Choose repository visibility intentionally.
- [ ] Add the finalized proprietary `LICENSE` file if source reuse is prohibited.
- [ ] Review every dependency's license and include required notices.
- [ ] Do not claim ownership over third-party/open-source components.
- [ ] Remove secrets, tokens, private keys, and private user data from Git history where necessary.
- [ ] Ensure the license matches the actual ownership and permissions you intend to grant.

## Legal review and ongoing maintenance

- [ ] Review policies against the actual app behavior.
- [ ] Get legal review for the jurisdictions where the app is offered.
- [ ] Confirm age-related requirements.
- [ ] Add a visible link to policies in the app footer/settings and near relevant forms.
- [ ] Re-review after adding a new provider, form, analytics tool, login, payment, or embed.
- [ ] Keep an internal record of changes and policy versions.

**Do not publish the placeholder text as final policy language.** These templates are a starting point, not a certification of compliance.
