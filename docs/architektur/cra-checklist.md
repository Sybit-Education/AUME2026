# CRA assessment and security follow-up

Date: 2026-10-29. Status: planning checklist; scope and legal conclusions unapproved. Related decision: [ADR-0010](adr/0010-track-cra-scope-and-product-security-evidence.md).

## Scope assessment inputs

- [ ] Identify product manufacturer, operator and legal entities receiving the software.
- [ ] Describe whether Compose images/source are supplied to Seezeit, whether supply is commercial, and whether access is exclusively a remotely hosted browser service.
- [ ] Identify any locally supplied application and necessary remote processing; reassess if native clients are introduced.
- [ ] Document applicable product classification, assessment route and responsible reviewer.

## Engineering and ownership evidence

- [ ] Assign long-term maintenance and incident-response owners and funding.
- [ ] Maintain threat model and security requirements covering authorization, invitations, attachments and operational access.
- [ ] Produce machine-readable SBOMs for released software/images, monitor components and record vulnerability remediation.
- [ ] Publish a security contact and coordinated vulnerability disclosure procedure.
- [ ] Establish secure defaults, security testing, authenticated updates and documented installation/update procedures.
- [ ] Define and communicate the support period and retain required technical documentation and security updates where CRA applies.
- [ ] Determine conformity documentation, EU declaration and CE marking obligations if in scope; do not claim compliance prematurely.
- [ ] Define photo/record retention, lawful processing and former-resident access under a separate privacy assessment.

## Conditional CRA reporting runbook

For an in-scope manufacturer, reporting duties began 2026-09-11; most remaining duties apply from 2027-12-11. Assess reportability promptly. For reportable actively exploited vulnerabilities and severe security incidents, plan early warning within 24 hours of awareness and notification within 72 hours through the Single Reporting Platform. Final-report deadlines differ: exploited vulnerabilities, no later than 14 days after a corrective measure is available; severe incidents, within one month after incident notification. Assign an on-call escalation route rather than relying on business-day review.

Support is ordinarily at least five years, with the expected-use exception and potentially longer expected use requiring assessment. Security-update availability is generally at least ten years after issuance or the remaining support period, whichever is longer. Confirm the actual applicable provisions before commitments.

## Official sources for review

- [Regulation (EU) 2024/2847](https://eur-lex.europa.eu/legal-content/EN-DE/TXT/?from=EN&uri=CELEX%3A32024R2847) — Articles 2–3, 13–14 and Annex I.
- [Commission scope summary](https://digital-strategy.ec.europa.eu/en/policies/cra-summary)
- [Commission implementation timeline](https://digital-strategy.ec.europa.eu/en/factpages/cyber-resilience-act-implementation)
- [Commission reporting guidance](https://digital-strategy.ec.europa.eu/en/policies/cra-reporting)

These sources were consulted for the preceding architecture recommendation; this checklist does not independently certify their interpretation or future currency.
