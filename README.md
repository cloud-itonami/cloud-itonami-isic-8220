# cloud-itonami-8220

Open Business Blueprint for **ISIC Rev.5 8220**: activities of call
centres (inbound/outbound customer contact operations run on behalf
of client businesses).

This repository designs a forkable OSS business for community call
centre operations: agent-registration and data-handling-scope
management, robotics-assisted first-line voice/chat triage and
screen-pop automation ahead of human-agent handoff, and call/quality
records — run by a qualified operator so a contact-center company
keeps its own compliance and quality-assurance history instead of
renting a closed call-centre platform.

## Scope note: contact-center operations, not telecom infrastructure

This repository is deliberately scoped to the customer-contact
SERVICE business (staffing, scripting, quality assurance, data
handling for client businesses' inbound/outbound calls and chats),
distinct from the fleet's telecom-infrastructure verticals (mobile
network operation, VoIP reselling) which provide the underlying
communications carriage. Call centres carry their own distinct
compliance regime: PCI-DSS where payment-card data is handled over
the phone; ISO 18295 as the international customer-contact-centre
quality standard; COPC certification as a widely recognized
customer-experience quality framework; telemarketing-specific
regulation (the US Telephone Consumer Protection Act and FTC
Telemarketing Sales Rule, national Do-Not-Call registries); and data-
protection obligations (GDPR, CCPA) given the volume of customer
personal data call centres process.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a
**robot performs the physical domain work**. For a labor-intensive
contact-center business, this takes the form of automated voice/chat
"bots" that perform first-line intake triage and screen-pop
preparation ahead of a human agent -- the industry's own colloquial
"robot" -- operating under an actor that proposes actions and an
independent **Call Centre Governor** that gates them. The governor
never releases a customer-data-handling action or an agent match
itself; `:high`/`:safety-critical` actions (a data-handling action
outside verified compliance scope, an agent match without a completed
registration/training check, a quality record without verified
evidence) require human sign-off.

## Core Contract

```text
intake + identity + data-handling/compliance scope + agent registration
        |
        v
Call Centre Advisor -> Call Centre Governor -> match, dispatch, follow-up record, or human approval
        |
        v
robot actions (gated) + call/quality record + audit ledger
```

No automated advice can release a data-handling action the governor
refuses, match an unregistered agent to a job, or publish a quality
record without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `8220`). Implemented by:

- [`kotoba-lang/robotics`](https://github.com/kotoba-lang/robotics) — missions, actions, safety-stops, telemetry proofs
- [`kotoba-lang/labor`](https://github.com/kotoba-lang/labor) — agent registration, dispatch, timesheet/follow-up contracts

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
