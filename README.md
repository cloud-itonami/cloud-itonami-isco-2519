# cloud-itonami-isco-2519

Open Business Blueprint for **ISCO-08 2519**: Software and Applications Developers and Analysts NEC — an ISCO
**Wave 0 (cognitive substrate)** occupation per the reverse-toposort
rollout plan (ADR-2607121000): pure-cognitive work, the LLM-first wave,
with **no robotics gate** — eligible for actor implementation now.

**Maturity: `:implemented`** — SoftwareDevNECAdvisor ⊣
SoftwareDevNECGovernor as a langgraph StateGraph
(`intake → advise → govern → decide → commit/hold`, human-approval
interrupt), modeled on cloud-itonami-isco-4311's bookkeeping actor.
15 tests / 34 assertions green.

## Running the suite

```
nbb test/run_suite.cljk
```

Not `clojure -M:test`. The sources here were renamed to `.kotoba` on
2026-09-10 without their contents changing, and `.kotoba` is not an
extension `clojure.tools.namespace` scans — so from that commit until
2026-09-11 the documented command collected **zero tests and exited 0**,
the same value it had given for 15 green tests the day before. The
sentence above went on being published while nothing checked it. That
alias now refuses (exit 2) instead of answering `pass` without looking.

`test/run_suite.cljk` stages the `.kotoba` sources into a scratch tree as
`.cljc` and runs them on nbb, resolving `langgraph`, `langchain` and
`text` from the sibling west checkouts and naming which ones it used. It
exits `0` only when the suite ran, met its floor and passed; `1` when
something failed; and `2` when it could not measure — a missing
dependency, no `*-test` namespace, no summary line, or **a run that came
in under the count published above**. That count is the floor, which is
why it is stated as a number: a suite that silently drops from 15 tests
to 12 is refused rather than reported as green.

The software HARD invariant — semver as arithmetic over a set diff:

1. **Breaking change detection** — symbols removed from the
   registered API surface (set difference) require a MAJOR bump;
   additions with no removals require at least MINOR. Semver is a
   contract computed from the diff, not a mood chosen at release
   time.
2. **Version advance** — the proposed version must be strictly
   greater than the registered one; re-releases and rollbacks are
   arithmetic violations.

Also HARD: invented/foreign modules, unregistered organization,
non-`:propose` effect. Escalations (always human sign-off):
`:publish-release` (external registry publication), low confidence
(< 0.6).

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet
(labor-transition context: ADR-2607122100 — ISCO wave-0 agentization
is marketed through the 7810 labour-exchange lane).
