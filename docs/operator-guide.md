# Operator Guide

## First Deployment
1. Register operator, campaigns/seats, data-handling/compliance
   scope, agents and voice/chat-triage automation.
2. Import existing client campaign and billing history.
3. Run read-only compliance-scope and triage-automation mission
   dry-runs.
4. Configure safety-class allowed sets and human sign-off paths.
5. Publish a dry-run quality record and audit export.

## Minimum Production Controls
- data-handling/compliance-scope validation before any agent dispatch
- governor gate on every robot (automation) action before dispatch
- human sign-off for :high/:safety-critical actions (an out-of-scope
  data-handling action, an unregistered/untrained agent match, an
  unverified quality record)
- evidence-backed quality records
- audit export for every dispatch, sign-off and quality record
- backup manual call-centre process

## Certification
Certified operators must prove automation-safety integrity, data-
handling/compliance discipline, evidence-backed quality records and
human review for dispatch-affecting actions.
