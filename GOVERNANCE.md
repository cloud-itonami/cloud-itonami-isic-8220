# Governance

`cloud-itonami-8220` is an OSS open-business blueprint for community
call centre operations, robotics-premised.

## Maintainers
Maintainers may merge changes that preserve these invariants:
- an automation action the governor refuses is never dispatched.
- the Call Centre Governor remains independent of the advisor.
- hard policy violations (an out-of-scope data-handling action, an
  unregistered/untrained agent match, an unverified quality record)
  cannot be overridden by human approval.
- every dispatch, sign-off and quality-record path is auditable.
- sensitive customer and call data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model, storage contract, public business model, operator certification or license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is a separate trust mark and should require security, robot-safety, audit and data-flow review.

Certified operators can lose certification for:
- bypassing automation-safety or compliance-scope checks
- mishandling customer or call data
- misrepresenting certification status
- failing to respond to safety incidents
