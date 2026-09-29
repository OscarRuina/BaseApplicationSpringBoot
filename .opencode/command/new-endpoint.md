---
description: Add a REST endpoint end to end — implementation, tests, and a Docker suite run.
agent: build
---

Add a REST endpoint to BaseApplicationSpringBoot, end to end.

The endpoint specification is:

$ARGUMENTS

## Do this in order

1. Delegate to the **`endpoint-builder`** subagent with the specification above.
   It reads the `new-endpoint` skill, runs the resource elicitation if the
   arguments do not already answer it, and implements the endpoint.
   Collect its file manifest, its authorization choice, its assumptions and its
   follow-ups.

2. Delegate to the **`test-author`** subagent with the manifest from step 1.
   It reads the `endpoint-tests` skill, adds the authorization matrix rows
   first, then the contract, unit and rollback tests.
   Collect its matrix report, its test inventory and anything it could not cover.

3. Delegate to the **`verify-suite`** subagent. It runs the suite in Docker —
   this host has no JDK, so there is no other way — and returns the failures
   verbatim.

4. Report back in one message:
   - the case type and the endpoint shape
   - the files created and edited
   - which authorization matrix list each route went into
   - the suite result, or each failure verbatim
   - anything left uncovered, and anything that needs a human decision

## Rules

**Stop before committing.** The user owns every commit. Leave the work staged.

If step 1 stops early, report the blocker and do not run steps 2 and 3 against
an incomplete implementation.
