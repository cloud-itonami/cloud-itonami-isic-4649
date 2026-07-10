# cloud-itonami-isic-4649

Open Business Blueprint for **ISIC Rev.5 4649**: Wholesale of Other
Household Goods -- household-order intake, per-jurisdiction
counterparty-diligence / consumer-product-safety regulatory
verification, physical dispatch, and invoice settlement for a
wholesale trader of housewares, small appliances, furniture, toys and
similar consumer goods not classified elsewhere.

This repository publishes a household-goods-wholesale actor --
household-order intake, per-jurisdiction contract / sanctions /
consumer-product-safety regulatory verification, physical dispatch and
invoice settlement -- as an OSS business that any qualified operator
can fork, deploy, run, improve and sell, so a regional household-goods
wholesaler never surrenders counterparty, credit, product-safety and
trade data to a closed distribution / ERP SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **HousewareTradeAdvisor
⊣ Consumer Product Safety Governor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:consumer-product-safety-
governor`, is a fresh, independent build.

**Like the fuel-wholesale / ag-machinery-wholesale / telecom-
electronics-wholesale siblings, this vertical is SELF-CONTAINED**:
there is no `kotoba-lang/housewaretrade` to delegate consumer-product-
safety validation to, so the credit-clearance / contract-on-file /
children's-product-certificate / active-recall / sanctions-screening
checks live as direct entity boolean/enum reads in
`housewaretrade.governor` (off dedicated `:credit-cleared?` /
`:contract-terms` / `:childrens-product-certificate-on-file?` /
`:recall-status` / `:sanctions-screened?` facts on the
`household-order` record), rather than wrapping an external capability
library's own validated function.

> **Why an actor layer at all?** An LLM is great at drafting an order
> summary, normalizing records, and reading a compliance file -- but it
> has **no notion of which jurisdiction's consumer-product-safety law
> is official, no license to dispatch real household goods to a
> counterparty or settle a real invoice, and no way to know on its own
> whether the counterparty's credit has actually been cleared, whether
> a children's product actually has a valid Children's Product
> Certificate on file, whether a SKU is currently subject to an active,
> unresolved recall, or whether OFAC / equivalent sanctions screening
> has actually been passed**. Letting it dispatch goods or settle an
> invoice directly invites fabricated regulatory citations, lead-tainted
> toys reaching children, a recalled appliance shipping to a second
> household, and an invoice settling against a sanctioned party --
> exposing the operator to real enforcement, product-liability and
> financial liability, for whoever runs it. This project seals the
> HousewareTradeAdvisor into a single node and wraps it with an
> independent **Consumer Product Safety Governor**, a human **approval
> workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers household-order intake through contract / sanctions /
consumer-product-safety regulatory verification, physical dispatch and
invoice settlement. It does **not**, by itself, hold any wholesale
licence, importer-of-record registration or operating authority
required to run a household-goods-wholesale business in a given
jurisdiction, and it does not claim to. It also does not perform the
actual physical warehouse pick/pack/ship itself, or judge distribution-
network economics -- carton/pallet-level automation and route
optimization (the blueprint's own `:optimization` technology) is a
follow-up slice, not in this R0. This build also deliberately does NOT
attempt to automatically ingest CPSC's own recall database -- an
operator populates/updates `:recall-status` today (via the same
`:order/intake` patch path a compliance officer would use for any other
order-directory correction); a real CPSC recall-feed sync integration
is a follow-up slice, not in this R0 (see `docs/business-model.md`
Business-process coverage). Whoever deploys and operates a live
instance (a qualified trading supervisor / distribution-center
operator) supplies any jurisdiction-specific operating authority, the
real warehouse-automation and ERP / accounts-receivable integrations,
and bears that jurisdiction's liability -- the software supplies the
governed, spec-cited, audited execution scaffold so that operator does
not have to build the compliance layer from scratch.

### Actuation

**Dispatching real household goods to a counterparty from the wholesale
distribution center and settling a real invoice are never autonomous,
at any phase, by construction.** Two independent layers enforce this
(`housewaretrade.governor`'s `:delivery/dispatch`/`:invoice/settle`
high-stakes gate and `housewaretrade.phase`'s phase table, which never
puts either op in any phase's `:auto` set) -- see
`housewaretrade.phase`'s docstring and
`test/housewaretrade/phase_test.clj`'s
`delivery-dispatch-never-auto-at-any-phase`/
`invoice-settle-never-auto-at-any-phase`. The actor may draft, check
and recommend; a human trading supervisor is always the one who
actually dispatches household goods or settles an invoice. Grounded in
consumer-product-safety doctrine (the same discipline every regulator in
`housewaretrade.facts` codifies: a real dispatch and a real invoice
settlement are human sign-off acts) -- a genuine DUAL-actuation shape,
applied SEQUENTIALLY to the SAME household-order (dispatch first,
invoice settlement later), matching the fuel-wholesale / ag-machinery-
wholesale siblings' own sequential shape.

## The core contract

```
household-order intake + jurisdiction facts (housewaretrade.facts, spec-cited)
        |
        v
   ┌───────────────────────┐   proposal      ┌─────────────────────────┐
   │ HousewareTradeAdvisor  │ ─────────────▶ │ Consumer Product Safety  │  (independent system)
   │ (sealed)               │  + citations    │ Governor                │
   └───────────────────────┘                 │ spec-basis · evidence-   │
          │                 commit ◀┼ incomplete · credit-uncleared ·  │
          │                         │ contract-missing · children's-   │
    record + ledger        escalate ┼ product-certificate-missing ·    │
          │              (ALWAYS for│ active-recall-unresolved ·       │
          │       :delivery/        │ counterparty-sanctions-flag-     │
          │       dispatch/         │ unresolved · already-dispatched ·│
          │       :invoice/         │ already-invoiced                 │
          │       settle)           └─────────────────────────┘
          ▼
      human approval
```

**The HousewareTradeAdvisor never dispatches household goods to a
counterparty or settles an invoice the Consumer Product Safety Governor
would reject, and never does so without a human sign-off.** Hard
violations (fabricated regulatory requirements; unsupported evidence;
an uncleared counterparty credit; no contract-terms on file; a
children's product with no valid Children's Product Certificate; a SKU
with an active, unresolved recall; an unresolved sanctions-screening
flag; a double dispatch/invoice) force **hold** and *cannot* be
approved past; a clean dispatch/invoice proposal still always routes to
a human.

## Two domain-defining checks, two different temporal shapes

This vertical's own defining regulatory content splits into TWO real,
distinct US CPSC mechanics, and this build deliberately keeps them
SEPARATE rather than folding one into the other, because they operate
on genuinely different real-world timelines:

- **`childrens-product-certificate-missing`** -- a PRE-SHIPMENT
  product-certification gate, type-gated on `:childrens-product?`.
  Folds TWO evidentiary arms of the SAME Children's Product Certificate
  determination (`:lead-phthalate-tested?` AND `:childrens-product-
  certificate-on-file?`) into ONE rule -- the metal-wholesale sibling's
  own single-fact-gated-fold shape, NOT the ag-machinery sibling's own
  two-independent-checks shape (see `housewaretrade.governor` namespace
  docstring for the full reasoning). A general household good is a true
  NO-OP for this check (`ho-1`); a fully-certified children's product
  dispatches cleanly (`ho-6`); an uncertified one HARD-holds (`ho-7`).
- **`active-recall-unresolved`** -- this vertical's own genuine
  structural novelty: a POST-HOC, discovered-defect FLAG (grounded in
  CPSA §15(b), 15 U.S.C. §2064(b)), not a pre-shipment certificate at
  all. Modeled as a re-checked `:recall-status` ENUM
  (`:none`/`:open`/`:resolved`) on the order, evaluated at BOTH
  `:delivery/dispatch` and `:invoice/settle` -- the SAME span as the
  counterparty-sanctions check, and for the SAME reason: a recall can be
  discovered from field data AFTER a SKU has already dispatched cleanly
  in the past. `ho-8` proves the full lifecycle: HARD-holds while
  `:open`, then dispatches cleanly on the SAME order once patched to
  `:resolved` via a plain `:order/intake` upsert (no new op needed).
  `:recall-status` is **not** a double-actuation-guard `:status` value
  in the sense `cloud-itonami-isic-6492`'s real bug (ADR-2607071320)
  warned this fleet off of -- the double-actuation guards remain the
  dedicated `:dispatched?`/`:invoiced?` booleans, exactly like every
  sibling.

See `docs/adr/0001-architecture.md` Decisions 4-5 for the full design
reasoning, including the alternatives considered and rejected.

## Run

```bash
clojure -M:dev:run     # walk one clean dispatch + invoice lifecycle, the children's-product + recall proofs, plus HARD-hold cases, through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work** where a real, fixed, robot-
actuatable apparatus exists for this actor's own governed entity. Here
an AS/RS (automated storage and retrieval system) / robotic case-
picking-and-palletizing shuttle stages the SKU's carton or pallet at the
wholesale distribution-center dock -- housewares, small appliances and
toys are carton/pallet-scale goods well-suited to this apparatus in
real-world distribution-center practice (unlike, say, the ag-machinery
sibling's own large self-propelled/towed equipment, which is driven off
the lot by a human operator and has no comparable fixed apparatus --
`:robotics false` there). The governor never dispatches hardware
itself: a dispatch-clearing action must have cleared the same sign-off
a human trading supervisor would need. This restates the fleet-wide
robotics premise three ways (ADR-2607011000): the blueprint declares
`:robotics true`, the README names the robot that performs the physical
act, and the Consumer Product Safety Governor is the independent gate
that robot's command must pass -- a robot may stage a carton, but only
after the governor and a human supervisor both agree it is safe to.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Consumer Product Safety Governor, dispatch/invoice draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`4649`). Like the fuel-wholesale / ag-machinery-wholesale siblings, this
vertical is NOT backed by a separate bespoke domain capability lib: the
household-goods-trading checks (credit-clearance, contract-on-file,
children's-product certification, active-recall status,
sanctions-screening) are direct entity boolean/enum reads in
`housewaretrade.governor`, on top of the generic
robotics/identity/forms/dmn/bpmn/audit-ledger stack.

## Layout

| File | Role |
|---|---|
| `src/housewaretrade/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + dispatch AND invoice history (dual history). The double-actuation guard checks dedicated `:dispatched?`/`:invoiced?` booleans rather than a `:status` value |
| `src/housewaretrade/registry.cljc` | Dispatch/invoice draft records (record construction only -- the Consumer Product Safety Governor's checks are direct entity booleans/enum reads, so there are no pure range-check functions to host here) |
| `src/housewaretrade/facts.cljc` | Per-jurisdiction generic counterparty-diligence catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/housewaretrade/housewaretradeadvisor.cljc` | **HousewareTradeAdvisor** -- `mock-advisor` ‖ `llm-advisor`; intake/safety-verification/dispatch/invoice proposals |
| `src/housewaretrade/governor.cljc` | **Consumer Product Safety Governor** -- 7 HARD checks (spec-basis · evidence-incomplete · credit-uncleared · contract-missing · children's-product-certificate-missing · active-recall-unresolved · counterparty-sanctions-flag-unresolved) + 2 double-actuation guards + 1 soft (confidence/actuation gate) |
| `src/housewaretrade/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (dispatch/invoice always human; order intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/housewaretrade/operation.cljc` | **OperationActor** -- langgraph StateGraph |
| `src/housewaretrade/sim.cljc` | demo driver |
| `test/housewaretrade/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers household-order intake through contract / sanctions /
consumer-product-safety regulatory verification, physical dispatch and
invoice settlement -- the core governed lifecycle:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Household-order intake + per-jurisdiction evidence checklisting, HARD-gated on an official spec-basis citation (`:order/intake`/`:safety/verify`) | Real warehouse-automation/ERP integration, carton/pallet routing and distribution-network economics |
| Physical dispatch, HARD-gated on full evidence, a credit-cleared counterparty, contract-terms on file, a valid Children's Product Certificate for children's products, no active unresolved recall, a passed sanctions screen and no double-dispatch (`:delivery/dispatch`) | Automated ingestion of CPSC's own recall database (`:recall-status` is operator-maintained in this R0) |
| Invoice settlement, HARD-gated on full evidence, no active unresolved recall, a passed sanctions screen and no double-invoice (`:invoice/settle`) | |
| Immutable audit ledger for every intake/verification/dispatch/invoice decision | |

Extending coverage is additive: add the next gate (e.g. a CPSC recall-
feed sync, or a mattress/rug flammability-specific evidence item) as
its own governed op or dedicated HARD check with its own tests,
following the SAME "an independent governor re-verifies against the
actor's own records before any real-world act" pattern this repo's
flagship ops already establish.

## Jurisdiction coverage (honest)

`housewaretrade.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `housewaretrade.facts/catalog`
-- currently 2 seeded (USA, JPN) out of ~194 jurisdictions worldwide.
This is a starting catalog to prove the governor contract end-to-end,
not a claim of global coverage. Adding a jurisdiction is additive: one
map entry in `housewaretrade.facts/catalog`, citing a real official
source -- never fabricate a jurisdiction's requirements to make
coverage look bigger. See `docs/business-model.md` "Jurisdiction
coverage (honest)" for this build's own confidence caveats on specific
citations (independent verification recommended before operational
reliance).

## Maturity

`:implemented` -- `HousewareTradeAdvisor` + `Consumer Product Safety
Governor` run as real, tested code (see `Run` above), following the
SAME governed-actor architecture as the other prior actors across this
fleet, with its own distinct, independently-named governor and its own
direct-entity-boolean/enum household-goods-trading checks. See
`docs/adr/0001-architecture.md` for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
